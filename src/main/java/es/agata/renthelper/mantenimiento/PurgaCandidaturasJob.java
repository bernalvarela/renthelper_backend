package es.agata.renthelper.mantenimiento;

import es.agata.renthelper.config.ContextoTenant;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.llm.ServicioInformeComparativo;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioComentario;
import es.agata.renthelper.repositorio.RepositorioEvaluacion;
import es.agata.renthelper.repositorio.RepositorioEventoFormulario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Borrado por plazo de conservación (RGPD).
 *
 * <p>Declarar un plazo en el aviso de privacidad y no borrar nada es peor que no declararlo. Esto
 * lo cumple de verdad: borra las candidaturas no seleccionadas pasados los meses configurados,
 * junto con sus evaluaciones y eventos.
 *
 * <p>Las SELECCIONADAS se conservan: son las del contrato y tienen otra base legal.
 */
@Component
public class PurgaCandidaturasJob {

	private static final Logger log = LoggerFactory.getLogger(PurgaCandidaturasJob.class);

	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioEvaluacion repoEvaluaciones;
	private final RepositorioEventoFormulario repoEventos;
	private final RepositorioComentario repoComentarios;
	private final PropiedadesRentHelper.Retencion retencion;
	private final ServicioInformeComparativo informes;

	public PurgaCandidaturasJob(RepositorioCandidatura repoCandidaturas, RepositorioEvaluacion repoEvaluaciones,
	                            RepositorioEventoFormulario repoEventos, RepositorioComentario repoComentarios,
	                            ServicioInformeComparativo informes, PropiedadesRentHelper propiedades) {
		this.informes = informes;
		this.repoCandidaturas = repoCandidaturas;
		this.repoEvaluaciones = repoEvaluaciones;
		this.repoEventos = repoEventos;
		this.repoComentarios = repoComentarios;
		this.retencion = propiedades.retencion();
	}

	@Scheduled(cron = "${renthelper.retencion.cron:0 30 4 * * *}")
	@Transactional
	public void purgar() {
		ContextoTenant.establecer(ContextoTenant.POR_DEFECTO);
		try {
			Instant limite = Instant.now().minus(retencion.meses() * 30L, ChronoUnit.DAYS);
			List<Candidatura> caducadas = repoCandidaturas.caducadas(limite);
			if (caducadas.isEmpty()) {
				return;
			}
			for (Candidatura candidatura : caducadas) {
				repoEvaluaciones.deleteByCandidaturaId(candidatura.getId());
				repoEventos.deleteByCandidaturaId(candidatura.getId());
				// Tus apuntes también: son texto libre sobre una persona identificada, y dejarlos
				// atrás convertiría la purga en un borrado a medias.
				repoComentarios.deleteByCandidaturaId(candidatura.getId());
				// Y los informes comparativos donde sale: hablan de ella con sus cifras.
				informes.olvidarCandidatura(candidatura.getAnuncioId(), candidatura.getId());
			}
			repoCandidaturas.deleteAll(caducadas);
			log.info("Purgadas {} candidaturas por plazo de conservación ({} meses)", caducadas.size(),
					retencion.meses());
		} finally {
			ContextoTenant.limpiar();
		}
	}
}
