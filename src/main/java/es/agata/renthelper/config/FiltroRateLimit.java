package es.agata.renthelper.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite por IP sobre los endpoints públicos.
 *
 * <p>Ventana deslizante en memoria, sin dependencias: para un servicio de un solo nodo con 150
 * candidaturas por anuncio no hace falta Redis ni Bucket4j. Si algún día hay varias réplicas,
 * esto deja de ser exacto y habrá que moverlo fuera.
 *
 * <p>Está calibrado para frenar bots, no candidatos: 150 contactos legítimos en 20 horas
 * significa que un límite agresivo cerraría la puerta a gente de verdad.
 */
@Component
public class FiltroRateLimit extends OncePerRequestFilter {

	private record Contador(Instant inicioVentana, AtomicInteger cuenta) {
	}

	private final Map<String, Contador> porMinuto = new ConcurrentHashMap<>();
	private final Map<String, Contador> altasPorHora = new ConcurrentHashMap<>();
	private final PropiedadesRentHelper.RateLimit limites;

	public FiltroRateLimit(PropiedadesRentHelper propiedades) {
		this.limites = propiedades.rateLimit() == null
				? new PropiedadesRentHelper.RateLimit(10, 90)
				: propiedades.rateLimit();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest peticion) {
		return !peticion.getRequestURI().startsWith("/api/publico/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
	                                FilterChain cadena) throws ServletException, IOException {
		String ip = ipDe(peticion);

		if (superado(porMinuto, ip, Duration.ofMinutes(1), limites.peticionesPorIpMinuto())) {
			rechazar(respuesta, "Demasiadas peticiones. Espera un momento.");
			return;
		}
		boolean esAlta = "POST".equals(peticion.getMethod())
				&& peticion.getRequestURI().matches("/api/publico/anuncios/[^/]+/candidaturas");
		if (esAlta && superado(altasPorHora, ip, Duration.ofHours(1), limites.altasPorIpHora())) {
			rechazar(respuesta, "Has abierto demasiadas solicitudes desde esta conexión.");
			return;
		}
		cadena.doFilter(peticion, respuesta);
	}

	private boolean superado(Map<String, Contador> mapa, String ip, Duration ventana, int maximo) {
		Instant ahora = Instant.now();
		Contador contador = mapa.compute(ip, (clave, actual) -> {
			if (actual == null || Duration.between(actual.inicioVentana(), ahora).compareTo(ventana) > 0) {
				return new Contador(ahora, new AtomicInteger());
			}
			return actual;
		});
		if (mapa.size() > 10_000) {
			// Poda perezosa: sin esto el mapa crece sin límite ante un escaneo de IPs.
			mapa.entrySet().removeIf(e -> Duration.between(e.getValue().inicioVentana(), ahora)
					.compareTo(ventana) > 0);
		}
		return contador.cuenta().incrementAndGet() > maximo;
	}

	private void rechazar(HttpServletResponse respuesta, String mensaje) throws IOException {
		respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		respuesta.setContentType("application/json;charset=UTF-8");
		respuesta.getWriter().write("{\"error\":\"%s\"}".formatted(mensaje));
	}

	/** Detrás de Traefik la IP real viene en X-Forwarded-For. */
	private static String ipDe(HttpServletRequest peticion) {
		String reenviada = peticion.getHeader("X-Forwarded-For");
		if (reenviada != null && !reenviada.isBlank()) {
			int coma = reenviada.indexOf(',');
			return (coma > 0 ? reenviada.substring(0, coma) : reenviada).trim();
		}
		return peticion.getRemoteAddr();
	}
}
