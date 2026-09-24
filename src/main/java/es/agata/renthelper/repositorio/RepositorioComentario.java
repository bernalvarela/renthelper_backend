package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.ComentarioCandidatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioComentario extends JpaRepository<ComentarioCandidatura, UUID> {

	/** Del más reciente al más antiguo: lo último que apuntaste es lo que quieres leer primero. */
	List<ComentarioCandidatura> findByCandidaturaIdOrderByCreadoEnDesc(UUID candidaturaId);

	/** Para el borrado en cascada: purga por retención y derecho de supresión. */
	void deleteByCandidaturaId(UUID candidaturaId);
}
