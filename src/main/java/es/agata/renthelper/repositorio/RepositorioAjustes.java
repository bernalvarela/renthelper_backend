package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Ajustes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioAjustes extends JpaRepository<Ajustes, UUID> {

	/**
	 * La fila única del tenant.
	 *
	 * <p>El discriminador de Hibernate ya filtra por tenant, así que «el primero» es «el suyo».
	 * Devuelve Optional porque en el primer arranque todavía no existe.
	 */
	Optional<Ajustes> findFirstBy();
}
