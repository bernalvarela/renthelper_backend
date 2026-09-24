package es.agata.renthelper.outbox;

import es.agata.renthelper.dominio.MensajeOutbox;
import es.agata.renthelper.api.admin.DtosAdmin;
import es.agata.renthelper.repositorio.RepositorioOutbox;
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

	private final RepositorioOutbox repositorio;

	public ServicioOutbox(RepositorioOutbox repositorio) {
		this.repositorio = repositorio;
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
		List<DtosAdmin.OperacionOutbox> recientes = repositorio.findTop30ByOrderByCreadoEnDesc().stream()
				.map(m -> new DtosAdmin.OperacionOutbox(
						m.getId(), m.getTipo().name(), m.getReferenciaId(), m.getEstado().name(),
						m.getIntentos(), m.getAplazamientos(), m.getCreadoEn(), m.getProcesadoEn(),
						m.getProximaEjecucion(), m.getUltimoError()))
				.toList();

		return new DtosAdmin.Actividad(
				repositorio.countByEstado(MensajeOutbox.Estado.PENDIENTE),
				repositorio.countByEstado(MensajeOutbox.Estado.FALLIDO),
				recientes);
	}
}
