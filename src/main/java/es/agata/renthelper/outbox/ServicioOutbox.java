package es.agata.renthelper.outbox;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.dominio.MensajeOutbox;
import es.agata.renthelper.api.admin.DtosAdmin;
import es.agata.renthelper.repositorio.RepositorioOutbox;
import es.agata.renthelper.repositorio.RepositorioProveedorLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encola trabajo diferido.
 *
 * <p>Se llama <b>dentro</b> de la transacción que guarda la candidatura: si el commit falla, el
 * mensaje tampoco existe, y si el commit va bien el trabajo está garantizado aunque el LLM y
 * Telegram estén caídos. Es la diferencia entre confirmar al candidato en 200 ms y hacerle
 * esperar a que responda un tercero.
 */
@Service
public class ServicioOutbox {

	private static final Logger log = LoggerFactory.getLogger(ServicioOutbox.class);

	/** Cuántas operaciones ya terminadas se enseñan en el panel, además de todas las de la cola. */
	private static final int RECIENTES = 30;

	private final RepositorioOutbox repositorio;
	private final RepositorioProveedorLlm repoProveedores;

	public ServicioOutbox(RepositorioOutbox repositorio, RepositorioProveedorLlm repoProveedores) {
		this.repositorio = repositorio;
		this.repoProveedores = repoProveedores;
	}

	public void encolarEvaluacion(UUID candidaturaId) {
		encolarEvaluacion(candidaturaId, false, null);
	}

	public void encolarEvaluacion(UUID candidaturaId, boolean forzarLlm) {
		encolarEvaluacion(candidaturaId, forzarLlm, null);
	}

	/**
	 * @param forzarLlm  vestigio de cuando el nombre se comprobaba en una llamada aparte.
	 * @param proveedorId si viene, evalúa con ESE modelo y no con el primero de la cadena. Es lo
	 *                    que permite pedir la opinión de uno concreto y compararla con la de otro
	 *                    sobre la misma candidatura.
	 */
	public void encolarEvaluacion(UUID candidaturaId, boolean forzarLlm, UUID proveedorId) {
		MensajeOutbox mensaje = new MensajeOutbox(MensajeOutbox.Tipo.EVALUAR_CANDIDATURA, candidaturaId);
		Map<String, Object> payload = new java.util.HashMap<>();
		if (forzarLlm) {
			payload.put("forzarLlm", true);
		}
		if (proveedorId != null) {
			payload.put("proveedorId", proveedorId.toString());
		}
		if (!payload.isEmpty()) {
			mensaje.setPayload(payload);
		}
		repositorio.save(mensaje);
	}

	/**
	 * Qué candidaturas tienen una evaluación en cola, y para cuándo está programada.
	 *
	 * <p>Es lo que permite al panel poner un indicador en la fila mientras se repuntúa: sin esto,
	 * pedir la repuntuación no cambiaba nada visible hasta que el outbox la procesaba. La hora
	 * distingue «en unos segundos» de «aplazada una hora porque se agotó la cuota».
	 */
	@Transactional(readOnly = true)
	public Map<UUID, Instant> evaluacionesEnCola(Collection<UUID> candidaturas) {
		if (candidaturas.isEmpty()) {
			return Map.of();
		}
		Map<UUID, Instant> programadas = new java.util.HashMap<>();
		repositorio.findByTipoAndEstadoAndReferenciaIdIn(MensajeOutbox.Tipo.EVALUAR_CANDIDATURA,
						MensajeOutbox.Estado.PENDIENTE, candidaturas)
				.forEach(m -> programadas.merge(m.getReferenciaId(), m.getProximaEjecucion(),
						(a, b) -> a.isBefore(b) ? a : b));
		return programadas;
	}

	/**
	 * Correo con el enlace de continuación.
	 *
	 * <p>Va por el outbox y no en la petición de alta: si el SMTP tarda o falla, el candidato no
	 * puede quedarse esperando en la primera pantalla. Él ya tiene el enlace en su barra de
	 * direcciones y en el propio formulario; el correo es la copia de seguridad.
	 */
	public void encolarEnlace(UUID candidaturaId) {
		repositorio.save(new MensajeOutbox(MensajeOutbox.Tipo.ENVIAR_ENLACE, candidaturaId));
	}

	/**
	 * La alerta se encola aparte tras evaluar, no se manda en el mismo paso: así un fallo de
	 * Telegram reintenta el envío sin volver a pagar la llamada al LLM.
	 */
	public void encolarAlerta(UUID candidaturaId, int puntuacion) {
		MensajeOutbox mensaje = new MensajeOutbox(MensajeOutbox.Tipo.ALERTA_INMEDIATA, candidaturaId);
		mensaje.setPayload(Map.of("puntuacion", puntuacion));
		repositorio.save(mensaje);
	}

	/**
	 * Qué está haciendo el trabajo de fondo.
	 *
	 * <p>Evaluar, alertar y enviar enlaces ocurre minutos después de que entre la candidatura, y
	 * desde el panel «aún no tiene nota» y «lleva cuatro horas aplazada porque se agotó la cuota»
	 * se ven exactamente igual. Esto los distingue.
	 *
	 * <p>Los aplazamientos se cuentan aparte de los intentos a propósito: esperar por cuota no es
	 * fracasar, y mezclarlos haría pensar que algo está roto cuando sólo está haciendo cola.
	 */
	@Transactional(readOnly = true)
	public DtosAdmin.Actividad actividad() {
		// Primero TODO lo que sigue en cola y después lo último que ha terminado. Antes eran sólo
		// las treinta más recientes, y una evaluación aplazada desde ayer se caía de la lista en
		// cuanto entraba trabajo nuevo: ni se veía ni se podía cancelar.
		List<MensajeOutbox> enCola = repositorio.findByEstadoOrderByProximaEjecucionAsc(MensajeOutbox.Estado.PENDIENTE);
		java.util.LinkedHashMap<UUID, MensajeOutbox> operaciones = new java.util.LinkedHashMap<>();
		enCola.forEach(m -> operaciones.put(m.getId(), m));
		repositorio.findTop30ByOrderByCreadoEnDesc().stream()
				.limit(RECIENTES)
				.forEach(m -> operaciones.putIfAbsent(m.getId(), m));

		Map<UUID, String> modelos = new java.util.HashMap<>();
		repoProveedores.findAll().forEach(p -> modelos.put(p.getId(),
				p.getModelo() == null ? p.getNombre() : p.getNombre() + " · " + p.getModelo()));

		List<DtosAdmin.OperacionOutbox> lista = operaciones.values().stream()
				.map(m -> new DtosAdmin.OperacionOutbox(
						m.getId(), m.getTipo().name(), m.getReferenciaId(), m.getEstado().name(),
						m.getIntentos(), m.getAplazamientos(), m.getCreadoEn(), m.getProcesadoEn(),
						m.getProximaEjecucion(), m.getUltimoError(), modeloPedido(m, modelos)))
				.toList();

		return new DtosAdmin.Actividad(
				enCola.size(),
				repositorio.countByEstado(MensajeOutbox.Estado.FALLIDO),
				lista);
	}

	/**
	 * Anula una operación en cola.
	 *
	 * <p>Para lo que no va a salir nunca: una evaluación pedida a un modelo sin cuota se aplaza
	 * cada hora durante un día entero antes de rendirse. Sólo se anula lo que sigue PENDIENTE; si
	 * el outbox la está procesando en ese momento, esto espera a que acabe (ver
	 * {@link RepositorioOutbox#bloquear}) y entonces ya no hay nada que anular.
	 */
	@Transactional
	public void cancelar(UUID operacionId) {
		MensajeOutbox mensaje = repositorio.bloquear(operacionId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esa operación no existe"));
		if (mensaje.getEstado() != MensajeOutbox.Estado.PENDIENTE) {
			throw ExcepcionNegocio.conflicto("YA_PROCESADA", switch (mensaje.getEstado()) {
				case COMPLETADO -> "Ya no está en cola: acaba de completarse.";
				case FALLIDO -> "Ya no está en cola: acaba de fallar y no se reintentará.";
				case CANCELADO -> "Ya estaba cancelada.";
				case PENDIENTE -> throw new IllegalStateException("inalcanzable");
			});
		}
		mensaje.cancelar();
		log.info("Operación {} ({}) cancelada a mano", mensaje.getId(), mensaje.getTipo());
	}

	/** Anula las evaluaciones en cola de una candidatura. Devuelve cuántas había. */
	@Transactional
	public int cancelarEvaluaciones(UUID candidaturaId) {
		int canceladas = 0;
		for (MensajeOutbox pendiente : repositorio.findByTipoAndEstadoAndReferenciaIdIn(
				MensajeOutbox.Tipo.EVALUAR_CANDIDATURA, MensajeOutbox.Estado.PENDIENTE, List.of(candidaturaId))) {
			// Se vuelve a leer bloqueada: entre la consulta y aquí el outbox puede haberla tomado.
			MensajeOutbox mensaje = repositorio.bloquear(pendiente.getId()).orElse(null);
			if (mensaje != null && mensaje.getEstado() == MensajeOutbox.Estado.PENDIENTE) {
				mensaje.cancelar();
				canceladas++;
			}
		}
		log.info("Evaluaciones en cola de la candidatura {} canceladas a mano: {}", candidaturaId, canceladas);
		return canceladas;
	}

	/** Con qué modelo se pidió la evaluación, si se pidió uno concreto. Null = el de la cadena. */
	private static String modeloPedido(MensajeOutbox mensaje, Map<UUID, String> modelos) {
		Object id = mensaje.getPayload() == null ? null : mensaje.getPayload().get("proveedorId");
		if (id == null) {
			return null;
		}
		try {
			return modelos.getOrDefault(UUID.fromString(String.valueOf(id)), "un modelo ya borrado");
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
