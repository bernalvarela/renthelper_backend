package es.agata.renthelper.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Dos superficies muy distintas en la misma aplicación:
 *
 * <ul>
 *   <li>El formulario del candidato es <b>público</b> por diseño: el enlace se pega en idealista.
 *       Su protección es el token impredecible, el límite por IP y la validación en servidor.</li>
 *   <li>El panel guarda ingresos, edades y teléfonos de 150 personas. Sin autenticación, cualquiera
 *       los lee. No es una tarea para el final.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder codificadorPassword() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public SecurityFilterChain cadenaFiltros(HttpSecurity http, Environment entorno) throws Exception {
		CsrfTokenRequestAttributeHandler manejadorCsrf = new CsrfTokenRequestAttributeHandler();
		manejadorCsrf.setCsrfRequestAttributeName(null);

		// La consola de H2 va en un iframe y sin CSRF. Sólo se abre con el perfil dev activo,
		// para que no haya manera de que quede expuesta en producción por descuido.
		boolean desarrollo = entorno.matchesProfiles("dev", "dev-pg");
		if (desarrollo) {
			http
					.authorizeHttpRequests(p -> p.requestMatchers("/h2-console/**").permitAll())
					.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
		}

		http
				.authorizeHttpRequests(peticiones -> peticiones
						// Formulario del candidato y recursos del bundle público.
						.requestMatchers("/", "/c/**", "/api/publico/**").permitAll()
						// Los HTML de las dos SPAs. /c/{slug} y /admin/** no se sirven tal cual:
						// ConfiguracionWeb los reenvía a estos ficheros, y el reenvío pasa otra vez
						// por esta cadena. Sin esto, la ruta estaba permitida pero su forward no, y
						// en producción (en local los sirve Vite) todo acababa en 401.
						.requestMatchers("/index.html", "/admin.html").permitAll()
						// Las páginas de error tampoco: un 404 o un 500 no deben convertirse en 401.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/assets/**", "/favicon.ico", "/manifest.webmanifest").permitAll()
						// Necesario antes del login: es lo que materializa la cookie XSRF-TOKEN.
						.requestMatchers("/api/csrf").permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						// ANTES de la regla general: gana la primera que casa, y /api/admin/**
						// también captura el propio login. El .permitAll() de formLogin añade
						// su regla al final, así que no llega a evaluarse nunca y el POST de
						// login se respondía con 401 sin intentar autenticar siquiera.
						.requestMatchers(HttpMethod.POST, "/api/admin/login", "/api/admin/logout").permitAll()
						.requestMatchers("/api/admin/**").authenticated()
						.requestMatchers("/admin/**").permitAll()   // la SPA se sirve; sus datos no
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						// El formulario público es anónimo y sin sesión: CSRF no aporta nada ahí.
						.ignoringRequestMatchers("/api/publico/**")
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(manejadorCsrf))
				.formLogin(login -> login
						.loginProcessingUrl("/api/admin/login")
						.successHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
						.failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
						.permitAll())
				.logout(logout -> logout
						.logoutUrl("/api/admin/logout")
						.logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value())))
				// Sin redirección a una página de login: el panel es una SPA que espera un 401.
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
						new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.httpBasic(Customizer.withDefaults())
				.headers(headers -> headers
						.frameOptions(frame -> {
							if (desarrollo) {
								frame.sameOrigin();
							} else {
								frame.deny();
							}
						})
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
										+ "script-src 'self'; connect-src 'self'; "
										+ (desarrollo ? "" : "frame-ancestors 'none'"))));

		return http.build();
	}
}
