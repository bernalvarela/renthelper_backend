package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.ProveedorLlm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioProveedorLlm extends JpaRepository<ProveedorLlm, UUID> {

	/**
	 * La cadena completa, en su orden.
	 *
	 * <p>Incluye los desactivados: el panel los tiene que enseñar, y filtrarlos es cosa del
	 * orquestador, que además necesita saber por qué descartó cada uno para poder explicarlo.
	 */
	List<ProveedorLlm> findAllByOrderByOrdenAscNombreAsc();

	boolean existsByNombre(String nombre);
}
