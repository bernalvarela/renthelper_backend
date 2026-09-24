package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Rubrica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioRubrica extends JpaRepository<Rubrica, UUID> {

	List<Rubrica> findAllByOrderByNombreAscVersionDesc();
}
