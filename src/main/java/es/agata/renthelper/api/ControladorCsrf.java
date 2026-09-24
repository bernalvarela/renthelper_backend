package es.agata.renthelper.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * El token CSRF de Spring Security es diferido: la cookie sólo se emite cuando alguien lo pide.
 * El panel llama aquí antes del login, que es el primer POST que necesita el token.
 */
@RestController
public class ControladorCsrf {

	@GetMapping("/api/csrf")
	public Map<String, String> token(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}
}
