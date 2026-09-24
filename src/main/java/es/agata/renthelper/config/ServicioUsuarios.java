package es.agata.renthelper.config;

import es.agata.renthelper.repositorio.RepositorioUsuario;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ServicioUsuarios implements UserDetailsService {

	private final RepositorioUsuario repositorio;

	public ServicioUsuarios(RepositorioUsuario repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return repositorio.findByEmailIgnoreCase(email)
				.map(UsuarioAutenticado::de)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario desconocido"));
	}
}
