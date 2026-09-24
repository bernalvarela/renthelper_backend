package es.agata.renthelper.servicio;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.EsquemaFormulario;
import es.agata.renthelper.dominio.EstadoCandidatura;
import es.agata.renthelper.dominio.EventoFormulario;
import es.agata.renthelper.formularios.ContextoValidacion;
import es.agata.renthelper.formularios.ErrorValidacion;
import es.agata.renthelper.formularios.NormalizadorTelefono;
import es.agata.renthelper.formularios.ValidadorRespuestas;
import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.TipoCampo;
import es.agata.renthelper.outbox.ServicioOutbox;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEsquemaFormulario;
import es.agata.renthelper.repositorio.RepositorioEventoFormulario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.regex.Pattern;

/** Flujo del candidato: alta, autosave por paso y envío. */
@Service
public class ServicioCandidaturas {

	private static final Logger log = LoggerFactory.getLogger(ServicioCandidaturas.class);
	/** Mismo criterio que el validador del formulario, para no aceptar aquí lo que allí falla. */
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

	private final RepositorioAnuncio repoAnuncios;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioEsquemaFormulario repoEsquemas;
	private final RepositorioEventoFormulario repoEventos;
	private final ValidadorRespuestas validador;
	private final ServicioOutbox outbox;
	private final boolean sinteticasPorDefecto;

	public ServicioCandidaturas(RepositorioAnuncio repoAnuncios, RepositorioCandidatura repoCandidaturas,
	                            RepositorioEsquemaFormulario repoEsquemas,
	                            RepositorioEventoFormulario repoEventos,
	                            ValidadorRespuestas validador, ServicioOutbox outbox,
	                            PropiedadesRentHelper propiedades) {
		this.sinteticasPorDefecto = propiedades.sinteticasPorDefecto();
		this.repoAnuncios = repoAnuncios;
		this.repoCandidaturas = repoCandidaturas;
		this.repoEsquemas = repoEsquemas;
		this.repoEventos = repoEventos;
		this.validador = validador;
		this.outbox = outbox;
	}

	public Anuncio anuncioPorSlug(String slug) {
		return repoAnuncios.findBySlug(slug)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Ese enlace no corresponde a ningún anuncio."));
	}

	public Anuncio anuncioPorId(UUID id) {
		return repoAnuncios.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Anuncio no disponible."));
	}

	public Candidatura porToken(String token) {
		return repoCandidaturas.findByToken(token)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Ese enlace ya no es válido."));
	}

	public EsquemaFormulario esquemaDe(Candidatura candidatura) {
		return repoEsquemas.findById(candidatura.getFormSchemaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Formulario no disponible."));
	}

	@Transactional
	public Candidatura crear(String slug, String nombre, String email, String idioma, String ip) {
		Anuncio anuncio = anuncioPorSlug(slug);
		if (!anuncio.isAceptandoCandidaturas()) {
			// El interruptor del panel, no despublicar en idealista: los que ya empezaron
			// pueden terminar su formulario.
			throw ExcepcionNegocio.conflicto("CERRADO",
					"Hemos recibido muchas solicitudes y hemos cerrado el proceso. ¡Gracias por tu interés!");
		}
		if (nombre == null || nombre.isBlank()) {
			throw ExcepcionNegocio.invalido("NOMBRE_REQUERIDO", "Necesitamos tu nombre para continuar.");
		}

		Candidatura candidatura = new Candidatura(anuncio.getId(), anuncio.getFormSchemaId(),
				GeneradorTokens.token(), nombre.trim(), anuncio.idiomaValido(idioma));
		candidatura.setIpAlta(ip);
		candidatura.setSintetica(sinteticasPorDefecto);

		if (email != null && !email.isBlank()) {
			String limpio = email.trim();
			if (!EMAIL.matcher(limpio).matches()) {
				throw ExcepcionNegocio.invalido("EMAIL", "Revisa el correo electrónico.");
			}
			candidatura.setEmail(limpio);
			// Se siembra también en las respuestas para que el paso de contacto llegue
			// relleno: pedir dos veces el mismo dato es la forma más fácil de irritar.
			candidatura.getRespuestas().put("email", limpio);
		}

		repoCandidaturas.save(candidatura);
		repoEventos.save(new EventoFormulario(candidatura.getId(), EventoFormulario.Tipo.ALTA, 0));

		if (candidatura.getEmail() != null) {
			// Por el outbox: si el SMTP tarda, el candidato no puede quedarse esperando en la
			// primera pantalla. El enlace ya lo tiene en pantalla; el correo es la copia.
			outbox.encolarEnlace(candidatura.getId());
		}

		log.info("Nueva candidatura {} en el anuncio {} · {} · idioma {}{}{}", candidatura.getId(),
				slug, candidatura.getNombre(), candidatura.getIdioma(),
				candidatura.getEmail() == null ? "" : " · con correo",
				candidatura.isSintetica() ? " · sintética" : "");
		return candidatura;
	}

	/**
	 * Autosave de un paso. Devuelve los errores sin bloquear el guardado: en móvil el candidato
	 * puede cambiar de app en cualquier momento, y perder lo escrito es perder la candidatura.
	 */
	@Transactional
	public List<ErrorValidacion> guardarPaso(String token, int indicePaso, Map<String, Object> respuestas) {
		Candidatura candidatura = porToken(token);
		exigirEditable(candidatura);
		EsquemaFormulario esquema = esquemaDe(candidatura);

		candidatura.guardarPaso(indicePaso + 1, respuestas);
		List<ErrorValidacion> errores = validador.validarPaso(esquema.getDefinicion(), indicePaso,
				candidatura.getRespuestas(), contextoDe(candidatura));

		if (errores.isEmpty()) {
			repoEventos.save(new EventoFormulario(candidatura.getId(),
					EventoFormulario.Tipo.PASO_COMPLETADO, indicePaso));
			log.info("Candidatura {} completó el paso {}", candidatura.getId(), indicePaso);
		} else {
			log.info("Candidatura {} guardó el paso {} con {} campos pendientes",
					candidatura.getId(), indicePaso, errores.size());
		}
		repoCandidaturas.save(candidatura);
		return errores;
	}

	@Transactional
	public List<ErrorValidacion> enviar(String token) {
		Candidatura candidatura = porToken(token);
		if (candidatura.getEstado() != EstadoCandidatura.BORRADOR) {
			return List.of();   // Reenvío: idempotente, no se duplica la evaluación.
		}
		EsquemaFormulario esquema = esquemaDe(candidatura);

		List<ErrorValidacion> errores = validador.validarCompleto(esquema.getDefinicion(),
				candidatura.getRespuestas(), contextoDe(candidatura));
		if (!errores.isEmpty()) {
			log.info("Envío rechazado de la candidatura {}: {} errores de validación ({})",
					candidatura.getId(), errores.size(),
					errores.stream().map(ErrorValidacion::campo).distinct().toList());
			return errores;
		}

		extraerContacto(candidatura, esquema);
		candidatura.marcarEnviada();
		repoCandidaturas.save(candidatura);
		repoEventos.save(new EventoFormulario(candidatura.getId(), EventoFormulario.Tipo.ENVIADO, null));

		// En la misma transacción: si el commit falla, no hay mensaje; si va bien, la evaluación
		// está garantizada aunque el LLM y Telegram estén caídos.
		outbox.encolarEvaluacion(candidatura.getId());

		log.info("Candidatura {} enviada tras {} s · encolada para evaluar", candidatura.getId(),
				candidatura.getSegundosCumplimentacion());
		return List.of();
	}

	/**
	 * Copia teléfono y correo a columnas propias.
	 *
	 * <p>El teléfono normalizado es la clave de deduplicación: con 150 contactos hay gente que
	 * abre el enlace tres veces y lo escribe de tres formas distintas. Si ya existe una
	 * candidatura con ese número en el anuncio, se avisa en vez de crear un duplicado silencioso.
	 */
	private void extraerContacto(Candidatura candidatura, EsquemaFormulario esquema) {
		for (Campo campo : esquema.getDefinicion().camposRaiz().toList()) {
			Object valor = candidatura.getRespuestas().get(campo.id());
			if (valor == null) {
				continue;
			}
			if (campo.tipo() == TipoCampo.TELEFONO) {
				String normalizado = NormalizadorTelefono.normalizar(String.valueOf(valor));
				if (normalizado != null) {
					repoCandidaturas.findByAnuncioIdAndTelefonoNormalizado(candidatura.getAnuncioId(), normalizado)
							.filter(existente -> !existente.getId().equals(candidatura.getId()))
							.ifPresent(existente -> {
								throw ExcepcionNegocio.conflicto("DUPLICADO",
										"Ya hemos recibido una solicitud con ese teléfono para este piso.");
							});
					candidatura.setTelefonoNormalizado(normalizado);
				}
			} else if (campo.tipo() == TipoCampo.EMAIL) {
				candidatura.setEmail(String.valueOf(valor));
			}
		}
	}

	/** Las fechas del anuncio que la validación necesita para resolver `@anuncio.*`. */
	private ContextoValidacion contextoDe(Candidatura candidatura) {
		return new ContextoValidacion(anuncioPorId(candidatura.getAnuncioId()).getDisponibleDesde());
	}

	private void exigirEditable(Candidatura candidatura) {
		if (candidatura.getEstado() != EstadoCandidatura.BORRADOR) {
			throw ExcepcionNegocio.conflicto("YA_ENVIADA", "Esta solicitud ya se envió. ¡Gracias!");
		}
	}
}
