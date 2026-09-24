package es.agata.renthelper.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establece el tenant de la petición a partir del usuario autenticado.
 *
 * <p>En los endpoints públicos no hay usuario, así que se queda el tenant por defecto: hoy es el
 * único que existe. El día que haya varios, el anuncio del slug dirá cuál es y este filtro es el
 * único sitio que hay que tocar.
 */
@Component
public class FiltroTenant extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
	                                FilterChain cadena) throws ServletException, IOException {
		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
				ContextoTenant.establecer(usuario.tenantId());
			} else {
				ContextoTenant.establecer(ContextoTenant.POR_DEFECTO);
			}
			cadena.doFilter(peticion, respuesta);
		} finally {
			// Imprescindible con pool de hilos: un ThreadLocal que no se limpia filtra datos
			// de un tenant a la siguiente petición que reutilice el hilo.
			ContextoTenant.limpiar();
		}
	}
}
