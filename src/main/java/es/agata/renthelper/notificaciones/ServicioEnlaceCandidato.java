package es.agata.renthelper.notificaciones;

import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manda al candidato su enlace de continuación.
 *
 * <p>Resuelve el fallo que más candidaturas pierde: alguien empieza desde el móvil, le entra una
 * llamada, cierra la pestaña y no sabe cómo volver. La cookie no basta —el formulario se abre en
 * el navegador embebido de idealista, donde puede no sobrevivir— y el enlace es la única forma
 * fiable de recuperar lo escrito.
 */
@Service
public class ServicioEnlaceCandidato {

	private static final Logger log = LoggerFactory.getLogger(ServicioEnlaceCandidato.class);

	private final ClienteCorreo correo;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioAnuncio repoAnuncios;
	private final String urlPublica;

	public ServicioEnlaceCandidato(ClienteCorreo correo, RepositorioCandidatura repoCandidaturas,
	                               RepositorioAnuncio repoAnuncios, PropiedadesRentHelper propiedades) {
		this.correo = correo;
		this.repoCandidaturas = repoCandidaturas;
		this.repoAnuncios = repoAnuncios;
		this.urlPublica = propiedades.urlPublica();
	}

	@Transactional(readOnly = true)
	public void enviarEnlace(UUID candidaturaId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId).orElseThrow();
		if (candidatura.getEmail() == null || candidatura.getEmail().isBlank()) {
			return;
		}
		Anuncio anuncio = repoAnuncios.findById(candidatura.getAnuncioId()).orElseThrow();
		String enlace = "%s/c/%s/%s".formatted(urlPublica, anuncio.getSlug(), candidatura.getToken());

		String cuerpo = """
				Hola%s:

				Gracias por tu interés en %s.

				Este es tu enlace para continuar con la solicitud cuando quieras. Lo que vayas
				escribiendo se guarda solo, así que puedes cerrar y retomarlo más tarde:

				%s

				Guárdalo: es la única forma de recuperar lo que ya has rellenado.

				Un saludo.
				""".formatted(
				candidatura.getNombre() == null ? "" : ", " + primerNombre(candidatura.getNombre()),
				anuncio.getTitulo(), enlace);

		correo.enviar(candidatura.getEmail(), "Tu solicitud para " + anuncio.getTitulo(), cuerpo);
		log.info("Enlace de continuación enviado para la candidatura {}", candidaturaId);
	}

	/** Sólo el nombre de pila: «Hola, Marta» lee mejor que el nombre completo. */
	private static String primerNombre(String nombre) {
		int espacio = nombre.trim().indexOf(' ');
		return espacio > 0 ? nombre.trim().substring(0, espacio) : nombre.trim();
	}
}
