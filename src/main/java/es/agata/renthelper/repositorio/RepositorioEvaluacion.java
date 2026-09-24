package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.dominio.RolEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEvaluacion extends JpaRepository<Evaluacion, UUID> {

	List<Evaluacion> findByCandidaturaIdOrderByCreadaEnDesc(UUID candidaturaId);

	Optional<Evaluacion> findFirstByCandidaturaIdAndRolOrderByCreadaEnDesc(UUID candidaturaId, RolEvaluacion rol);

	/**
	 * La última evaluación PRINCIPAL que salió bien.
	 *
	 * <p>Se usa para no degradar una candidatura ya valorada cuando un reintento falla: si esto
	 * devuelve algo, el intento fallido no puede quedarse con el rol PRINCIPAL.
	 */
	Optional<Evaluacion> findFirstByCandidaturaIdAndRolAndErrorIsNullOrderByCreadaEnDesc(
			UUID candidaturaId, RolEvaluacion rol);

	void deleteByCandidaturaId(UUID candidaturaId);

	/** Todas las evaluaciones de un anuncio: la base de la vista de desacuerdos y correlación. */
	@Query("""
			select e from Evaluacion e
			where e.candidaturaId in (select c.id from Candidatura c where c.anuncioId = :anuncioId)
			order by e.creadaEn desc
			""")
	List<Evaluacion> porAnuncio(@Param("anuncioId") UUID anuncioId);
}
