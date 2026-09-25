package es.agata.renthelper.outbox;

import es.agata.renthelper.config.ContextoTenant;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.dominio.MensajeOutbox;
import es.agata.renthelper.llm.ErroresLlm;
import es.agata.renthelper.llm.OrquestadorEvaluacion;
import es.agata.renthelper.notificaciones.ServicioEnlaceCandidato;
import es.agata.renthelper.notificaciones.ServicioNotificaciones;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Poller del outbox.
 *
 * <p>Además de dar fiabilidad, <b>es el limitador de velocidad del LLM</b>: el tier gratuito de
 * Gemini limita peticiones por minuto, y como el digest sale cada tres horas no importa que una
 * evaluación tarde diez minutos en procesarse. Ajustando {@code intervalo-ms} y {@code lote} se
 * respeta cualquier cuota sin escribir un rate limiter.
 *
 * <p>Asume un único nodo: {@code fixedDelay} evita el solape consigo mismo y el SELECT bloquea
 * con SKIP LOCKED. Con varias réplicas haría falta un cerrojo distribuido.
 */
@Component
public class ProcesadorOutbox {

	private static final Logger log = LoggerFactory.getLogger(ProcesadorOutbox.class);

	private final RepositorioOutbox repoOutbox;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioAnuncio repoAnuncios;
	private final OrquestadorEvaluacion orquestador;
	private final ServicioNotificaciones notificaciones;
	private final ServicioEnlaceCandidato enlaces;
	private final ServicioOutbox servicioOutbox;
	private final PropiedadesRentHelper.Outbox config;

	public ProcesadorOutbox(RepositorioOutbox repoOutbox, RepositorioCandidatura repoCandidaturas,
	                        RepositorioAnuncio repoAnuncios, OrquestadorEvaluacion orquestador,
	                        ServicioNotificaciones notificaciones, ServicioEnlaceCandidato enlaces,
	                        ServicioOutbox servicioOutbox,
	                        PropiedadesRentHelper propiedades) {
		this.repoOutbox = repoOutbox;
		this.repoCandidaturas = repoCandidaturas;
		this.repoAnuncios = repoAnuncios;
		this.orquestador = orquestador;
		this.notificaciones = notificaciones;
		this.enlaces = enlaces;
		this.servicioOutbox = servicioOutbox;
		this.config = propiedades.outbox();
	}

	@Scheduled(fixedDelayString = "${renthelper.outbox.intervalo-ms:15000}")
	@Transactional
	public void procesar() {
		ContextoTenant.establecer(ContextoTenant.POR_DEFECTO);
		try {
			List<MensajeOutbox> lote = repoOutbox.tomarPendientes(Instant.now(), Limit.of(config.lote()));
			if (lote.isEmpty()) {
				return;
			}
			log.info("Outbox: procesando {} mensaje(s)", lote.size());

			for (MensajeOutbox mensaje : lote) {
				try {
					despachar(mensaje);
					mensaje.completar();
					log.info("Outbox: {} completado para {}", mensaje.getTipo(), mensaje.getReferenciaId());

				} catch (ReintentarMasTarde e) {
					// No es un fallo: el proveedor volverá. La puntuación de reglas ya está
					// guardada, así que la candidatura no se queda sin nota mientras tanto.
					mensaje.aplazar(config.aplazamientoMinutos(), config.maxAplazamientos());
					log.warn("Outbox: {} para {} aplazado {} min ({}/{}) · {}", mensaje.getTipo(),
							mensaje.getReferenciaId(), config.aplazamientoMinutos(),
							mensaje.getAplazamientos(), config.maxAplazamientos(), e.getMessage());

				} catch (RuntimeException | LinkageError e) {
					// LinkageError: en la imagen nativa, una reflexión sin registrar es un Error y
					// no una excepción. Sin capturarlo aquí, el mensaje ni contaba el intento ni
					// esperaba el backoff: se volvía a tomar en la pasada siguiente, para siempre.
					mensaje.fallar(e.getMessage(), config.maxIntentos(), config.backoffBaseSegundos());
					if (mensaje.getEstado() == MensajeOutbox.Estado.FALLIDO) {
						log.error("Outbox: {} para {} agotó los {} intentos y queda FALLIDO",
								mensaje.getTipo(), mensaje.getReferenciaId(), config.maxIntentos(), e);
					} else {
						log.error("Outbox: {} para {} falló en el intento {}; se reintentará",
								mensaje.getTipo(), mensaje.getReferenciaId(), mensaje.getIntentos(), e);
					}
				}
				repoOutbox.save(mensaje);
			}
		} finally {
			ContextoTenant.limpiar();
		}
	}

	private void despachar(MensajeOutbox mensaje) {
		switch (mensaje.getTipo()) {
			case EVALUAR_CANDIDATURA -> evaluar(mensaje.getReferenciaId(),
					mensaje.getPayload() != null
							&& Boolean.TRUE.equals(mensaje.getPayload().get("forzarLlm")),
					proveedorDe(mensaje));
			case ALERTA_INMEDIATA -> notificaciones.enviarAlerta(mensaje.getReferenciaId());
			case ENVIAR_ENLACE -> enlaces.enviarEnlace(mensaje.getReferenciaId());
		}
	}

	/** El modelo elegido a mano desde el panel, si lo hubo. */
	private static java.util.UUID proveedorDe(MensajeOutbox mensaje) {
		if (mensaje.getPayload() == null) {
			return null;
		}
		Object valor = mensaje.getPayload().get("proveedorId");
		return valor == null ? null : java.util.UUID.fromString(String.valueOf(valor));
	}

	private void evaluar(java.util.UUID candidaturaId, boolean forzarLlm, java.util.UUID proveedorId) {
		Evaluacion evaluacion = orquestador.evaluar(candidaturaId, forzarLlm, proveedorId);
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId).orElseThrow();
		Anuncio anuncio = repoAnuncios.findById(candidatura.getAnuncioId()).orElseThrow();

		// Alerta inmediata sólo por encima del umbral. Con 150 candidaturas, un mensaje por
		// cada una hace que dejes de mirar el bot el segundo día; el resto va en el digest.
		if (evaluacion.getPuntuacionTotal() >= anuncio.getUmbralAlerta() && !candidatura.isSintetica()) {
			log.info("Candidatura {} supera el umbral de alerta ({} >= {}): encolando aviso inmediato",
					candidaturaId, evaluacion.getPuntuacionTotal(), anuncio.getUmbralAlerta());
			servicioOutbox.encolarAlerta(candidaturaId, evaluacion.getPuntuacionTotal());
		}

		// La evaluación determinista ya está guardada y la transacción sigue viva, así que lanzar
		// aquí reprograma el mensaje sin perder la puntuación: cuando vuelva la cuota, la
		// candidatura se completa con el juicio del modelo.
		if (ErroresLlm.esTransitorio(evaluacion.getError())) {
			throw new ReintentarMasTarde(evaluacion.getError());
		}
	}
}
