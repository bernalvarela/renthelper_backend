package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.InformeComparativo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioInformeComparativo extends JpaRepository<InformeComparativo, UUID> {

	Optional<InformeComparativo> findFirstByAnuncioIdOrderByCreadoEnDesc(UUID anuncioId);

	List<InformeComparativo> findByAnuncioId(UUID anuncioId);
}
