package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.EstadoCandidatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioCandidatura extends JpaRepository<Candidatura, UUID> {

	Optional<Candidatura> findByToken(String token);

	Optional<Candidatura> findByAnuncioIdAndTelefonoNormalizado(UUID anuncioId, String telefono);

	long countByAnuncioIdAndEstadoIn(UUID anuncioId, Collection<EstadoCandidatura> estados);

	/**
	 * Orden de triaje: primero las que cumplen mínimos, después por puntuación. La marca de
	 * mínimos ordena al fondo pero no descarta: el descarte automático arranca desactivado.
	 */
	@Query("""
			select c from Candidatura c
			where c.anuncioId = :anuncioId
			  and (:incluirBorradores = true or c.estado <> es.agata.renthelper.dominio.EstadoCandidatura.BORRADOR)
			order by c.noCumpleMinimos asc, c.puntuacion desc nulls last, c.enviadaEn asc
			""")
	List<Candidatura> paraTriaje(@Param("anuncioId") UUID anuncioId,
	                             @Param("incluirBorradores") boolean incluirBorradores);

	/** Lo entrado desde el último digest. Si no hay nada nuevo, no se manda mensaje. */
	@Query("""
			select c from Candidatura c
			where c.anuncioId = :anuncioId
			  and c.enviadaEn > :desde
			  and c.estado <> es.agata.renthelper.dominio.EstadoCandidatura.BORRADOR
			order by c.puntuacion desc nulls last
			""")
	List<Candidatura> nuevasDesde(@Param("anuncioId") UUID anuncioId, @Param("desde") Instant desde);

	List<Candidatura> findByAnuncioIdAndSinteticaTrue(UUID anuncioId);

	/** Purga por retención. Las seleccionadas se conservan: son las del contrato. */
	@Query("""
			select c from Candidatura c
			where c.sintetica = false
			  and c.estado <> es.agata.renthelper.dominio.EstadoCandidatura.SELECCIONADA
			  and c.creadaEn < :limite
			""")
	List<Candidatura> caducadas(@Param("limite") Instant limite);
}
