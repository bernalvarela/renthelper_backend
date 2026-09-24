package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Anuncio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioAnuncio extends JpaRepository<Anuncio, UUID> {

	Optional<Anuncio> findBySlug(String slug);

	boolean existsBySlug(String slug);

	List<Anuncio> findAllByOrderByCreadoEnDesc();
}
