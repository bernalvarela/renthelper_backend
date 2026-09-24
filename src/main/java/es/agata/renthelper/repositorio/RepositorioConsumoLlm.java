package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.ConsumoLlm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioConsumoLlm extends JpaRepository<ConsumoLlm, Long> {

	Optional<ConsumoLlm> findByProveedorAndPeriodo(String proveedor, String periodo);

	List<ConsumoLlm> findByPeriodo(String periodo);
}
