package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioUsuario extends JpaRepository<Usuario, UUID> {

	Optional<Usuario> findByEmailIgnoreCase(String email);
}
