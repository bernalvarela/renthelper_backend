package es.agata.renthelper.config;

import es.agata.renthelper.dominio.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** UserDetails que arrastra el tenant, para que {@link FiltroTenant} pueda establecerlo. */
public record UsuarioAutenticado(UUID id, UUID tenantId, String email, String hash, String rol)
		implements UserDetails {

	public static UsuarioAutenticado de(Usuario usuario) {
		return new UsuarioAutenticado(usuario.getId(), usuario.getTenantId(), usuario.getEmail(),
				usuario.getPasswordHash(), usuario.getRol());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
	}

	@Override
	public String getPassword() {
		return hash;
	}

	@Override
	public String getUsername() {
		return email;
	}
}
