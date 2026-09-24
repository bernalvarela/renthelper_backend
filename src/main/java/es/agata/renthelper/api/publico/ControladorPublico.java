package es.agata.renthelper.api.publico;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.servicio.ServicioCandidaturas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API del formulario del candidato. Sin autenticación: el enlace se pega en idealista.
 *
 * <p>Lo que la protege: el token impredecible, el límite por IP de
 * {@code FiltroRateLimit} y que toda la validación se rehace en servidor.
 */
@RestController
@RequestMapping("/api/publico")
public class ControladorPublico {

	private final ServicioCandidaturas servicio;
	private final String urlPublica;

	public ControladorPublico(ServicioCandidaturas servicio, PropiedadesRentHelper propiedades) {
		this.servicio = servicio;
		this.urlPublica = propiedades.urlPublica();
	}

	@GetMapping("/anuncios/{slug}")
	public DtosPublico.AnuncioPublico anuncio(@PathVariable String slug) {
		return aDto(servicio.anuncioPorSlug(slug));
	}

	@PostMapping("/anuncios/{slug}/candidaturas")
	public ResponseEntity<DtosPublico.AltaRespuesta> alta(@PathVariable String slug,
	                                                      @RequestBody DtosPublico.AltaPeticion peticion,
	                                                      HttpServletRequest http,
	                                                      HttpServletResponse respuesta) {
		// Consentimiento explícito antes de guardar nada: es la base legal del tratamiento.
		if (!peticion.consentimiento()) {
			throw ExcepcionNegocio.invalido("CONSENTIMIENTO",
					"Necesitamos tu consentimiento para tratar los datos de la solicitud.");
		}
		Candidatura candidatura = servicio.crear(slug, peticion.nombre(), peticion.email(),
				peticion.idioma(), ip(http));

		// La cookie es una comodidad, no el mecanismo: dentro del navegador embebido de
		// idealista puede no sobrevivir, y por eso el token también va en la URL.
		Cookie cookie = new Cookie("rh_token", candidatura.getToken());
		cookie.setHttpOnly(true);
		cookie.setPath("/c/" + slug);
		cookie.setMaxAge(60 * 60 * 24 * 30);
		respuesta.addCookie(cookie);

		String url = "%s/c/%s/%s".formatted(urlPublica, slug, candidatura.getToken());
		return ResponseEntity.ok(new DtosPublico.AltaRespuesta(candidatura.getToken(), url,
				candidatura.getEmail() != null));
	}

	@GetMapping("/candidaturas/{token}")
	public DtosPublico.EstadoFormulario estado(@PathVariable String token) {
		Candidatura candidatura = servicio.porToken(token);
		Anuncio anuncio = servicio.anuncioPorId(candidatura.getAnuncioId());
		return new DtosPublico.EstadoFormulario(
				aDto(anuncio),
				servicio.esquemaDe(candidatura).getDefinicion(),
				candidatura.getRespuestas(),
				candidatura.getPasoActual(),
				candidatura.getIdioma(),
				candidatura.getEstado().name(),
				candidatura.getNombre(),
				candidatura.getEmail() != null);
	}

	@PutMapping("/candidaturas/{token}/pasos/{indice}")
	public DtosPublico.ResultadoValidacion guardarPaso(@PathVariable String token,
	                                                   @PathVariable int indice,
	                                                   @RequestBody DtosPublico.GuardarPasoPeticion peticion) {
		return DtosPublico.ResultadoValidacion.de(
				servicio.guardarPaso(token, indice, peticion.respuestas()));
	}

	@PostMapping("/candidaturas/{token}/enviar")
	public DtosPublico.ResultadoValidacion enviar(@PathVariable String token) {
		return DtosPublico.ResultadoValidacion.de(servicio.enviar(token));
	}

	private DtosPublico.AnuncioPublico aDto(Anuncio anuncio) {
		return new DtosPublico.AnuncioPublico(
				anuncio.getSlug(), anuncio.getTitulo(), anuncio.getDireccion(), anuncio.getRentaMensual(),
				anuncio.getDisponibleDesde(), anuncio.getIdiomas(), anuncio.getIdiomaPorDefecto(),
				anuncio.isAceptandoCandidaturas());
	}

	/** Detrás de Traefik la IP real viene en X-Forwarded-For. */
	private static String ip(HttpServletRequest peticion) {
		String reenviada = peticion.getHeader("X-Forwarded-For");
		if (reenviada != null && !reenviada.isBlank()) {
			int coma = reenviada.indexOf(',');
			return (coma > 0 ? reenviada.substring(0, coma) : reenviada).trim();
		}
		return peticion.getRemoteAddr();
	}
}
