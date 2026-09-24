package es.agata.renthelper.notificaciones;

import es.agata.renthelper.config.ContextoTenant;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.dominio.EstadoCandidatura;
import es.agata.renthelper.dominio.RolEvaluacion;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEvaluacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Notificaciones a Telegram.
 *
 * <p>Con 150 contactos en 20 horas, un mensaje por candidato es ruido y acabas silenciando el
 * bot. El reparto es: alerta inmediata sólo por encima del umbral del anuncio, y digest por
 * tiempo para todo lo demás. El cron por defecto va de 9:00 a 21:00, así que lo que entra de
 * madrugada se acumula al primero de la mañana.
 */
@Service
public class ServicioNotificaciones {

	private static final Logger log = LoggerFactory.getLogger(ServicioNotificaciones.class);
	private static final int TOP_EN_DIGEST = 5;

	private final ClienteTelegram telegram;
	private final RepositorioAnuncio repoAnuncios;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioEvaluacion repoEvaluaciones;
	private final String urlPublica;

	public ServicioNotificaciones(ClienteTelegram telegram, RepositorioAnuncio repoAnuncios,
	                              RepositorioCandidatura repoCandidaturas,
	                              RepositorioEvaluacion repoEvaluaciones,
	                              PropiedadesRentHelper propiedades) {
		this.telegram = telegram;
		this.repoAnuncios = repoAnuncios;
		this.repoCandidaturas = repoCandidaturas;
		this.repoEvaluaciones = repoEvaluaciones;
		this.urlPublica = propiedades.urlPublica();
	}

	@Transactional
	public void enviarAlerta(UUID candidaturaId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId).orElseThrow();
		Anuncio anuncio = repoAnuncios.findById(candidatura.getAnuncioId()).orElseThrow();
		Evaluacion evaluacion = repoEvaluaciones
				.findFirstByCandidaturaIdAndRolOrderByCreadaEnDesc(candidaturaId, RolEvaluacion.PRINCIPAL)
				.orElseThrow();

		StringBuilder sb = new StringBuilder();
		sb.append("⭐ <b>Candidatura destacada</b> · ")
				.append(ClienteTelegram.escapar(anuncio.getTitulo())).append("\n\n")
				.append("<b>").append(evaluacion.getPuntuacionTotal()).append("/100</b> · ")
				.append(ClienteTelegram.escapar(candidatura.getNombre())).append('\n');

		if (evaluacion.getResumen() != null) {
			sb.append('\n').append(ClienteTelegram.escapar(evaluacion.getResumen())).append('\n');
		}
		// Sin resumen es que no hubo modelo. Decirlo evita leer una nota de sólo reglas como si
		// el modelo la hubiese bendecido, que es justo cuando conviene desconfiar del ranking.
		if (evaluacion.getResumen() == null && evaluacion.getError() != null) {
			sb.append("\n⚠️ <i>Sin valoración de IA: ")
					.append(ClienteTelegram.escapar(evaluacion.getError())).append("</i>\n");
		}
		anexarListado(sb, "⚠️ Ojo", evaluacion.getBanderas());
		anexarListado(sb, "❓ Preguntar", evaluacion.getPreguntasPendientes());

		sb.append("\n<a href=\"").append(urlPublica).append("/admin/candidaturas/")
				.append(candidatura.getId()).append("\">Ver ficha completa</a>");

		telegram.enviar(sb.toString());
		candidatura.setNotificadaEn(Instant.now());
		repoCandidaturas.save(candidatura);
		log.info("Alerta enviada por {} ({} puntos)", candidatura.getNombre(),
				evaluacion.getPuntuacionTotal());
	}

	@Scheduled(cron = "${renthelper.telegram.digest-cron:0 0 9,12,15,18,21 * * *}")
	@Transactional
	public void enviarDigests() {
		ContextoTenant.establecer(ContextoTenant.POR_DEFECTO);
		try {
			for (Anuncio anuncio : repoAnuncios.findAllByOrderByCreadoEnDesc()) {
				try {
					enviarDigest(anuncio);
				} catch (RuntimeException e) {
					log.warn("No se pudo enviar el digest de {}: {}", anuncio.getSlug(), e.toString());
				}
			}
		} finally {
			ContextoTenant.limpiar();
		}
	}

	private void enviarDigest(Anuncio anuncio) {
		Instant desde = anuncio.getUltimoDigestEn() == null ? anuncio.getCreadoEn() : anuncio.getUltimoDigestEn();
		List<Candidatura> nuevas = repoCandidaturas.nuevasDesde(anuncio.getId(), desde).stream()
				.filter(c -> !c.isSintetica())
				.toList();

		// Si no hay nada nuevo no se manda mensaje: un digest vacío cada tres horas entrena
		// a ignorar el bot igual de rápido que uno por candidato.
		if (nuevas.isEmpty()) {
			return;
		}

		long sinMinimos = nuevas.stream().filter(Candidatura::isNoCumpleMinimos).count();
		long total = repoCandidaturas.countByAnuncioIdAndEstadoIn(anuncio.getId(),
				List.of(EstadoCandidatura.ENVIADA, EstadoCandidatura.EVALUADA));

		StringBuilder sb = new StringBuilder();
		sb.append("📋 <b>").append(ClienteTelegram.escapar(anuncio.getTitulo())).append("</b>\n")
				.append(nuevas.size()).append(" nuevas");
		if (sinMinimos > 0) {
			sb.append(" · ").append(sinMinimos).append(" por debajo de mínimos");
		}
		sb.append(" · ").append(total).append(" en total\n");

		String[] medallas = {"🥇", "🥈", "🥉", "▫️", "▫️"};
		List<Candidatura> mejores = nuevas.stream()
				.filter(c -> !c.isNoCumpleMinimos())
				.limit(TOP_EN_DIGEST)
				.toList();

		for (int i = 0; i < mejores.size(); i++) {
			Candidatura candidatura = mejores.get(i);
			sb.append('\n').append(medallas[i]).append(" <b>")
					.append(candidatura.getPuntuacion() == null ? "—" : candidatura.getPuntuacion())
					.append("</b> · ").append(ClienteTelegram.escapar(candidatura.getNombre())).append('\n');
			repoEvaluaciones
					.findFirstByCandidaturaIdAndRolOrderByCreadaEnDesc(candidatura.getId(), RolEvaluacion.PRINCIPAL)
					.filter(e -> e.getResumen() != null)
					.ifPresent(e -> sb.append("<i>").append(ClienteTelegram.escapar(e.getResumen()))
							.append("</i>\n"));
		}

		sb.append("\n<a href=\"").append(urlPublica).append("/admin/anuncios/").append(anuncio.getId())
				.append("\">Abrir el panel para triar</a>");

		telegram.enviar(sb.toString());
		anuncio.setUltimoDigestEn(Instant.now());
		repoAnuncios.save(anuncio);
		log.info("Digest enviado del anuncio {}: {} nuevas, {} por debajo de mínimos",
				anuncio.getSlug(), nuevas.size(), sinMinimos);
	}

	private void anexarListado(StringBuilder sb, String titulo, List<String> elementos) {
		if (elementos == null || elementos.isEmpty()) {
			return;
		}
		sb.append('\n').append(titulo).append(":\n");
		elementos.forEach(e -> sb.append("· ").append(ClienteTelegram.escapar(e)).append('\n'));
	}
}
