package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.EsquemaFormulario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioEsquemaFormulario extends JpaRepository<EsquemaFormulario, UUID> {

	List<EsquemaFormulario> findAllByOrderByNombreAscVersionDesc();
}
