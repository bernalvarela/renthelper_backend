package es.agata.renthelper.servicio;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.api.admin.DtosAdmin;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Ajustes;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.ComentarioCandidatura;
import es.agata.renthelper.fiscal.BonificacionFiscal;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import es.agata.renthelper.dominio.EsquemaFormulario;
import es.agata.renthelper.dominio.EstadoCandidatura;
import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.dominio.RolEvaluacion;
import es.agata.renthelper.dominio.Rubrica;
import es.agata.renthelper.formularios.ValidadorEsquema;
import es.agata.renthelper.formularios.modelo.Paso;
import es.agata.renthelper.outbox.ServicioOutbox;
import es.agata.renthelper.puntuacion.ValidadorRubrica;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEsquemaFormulario;
import es.agata.renthelper.repositorio.RepositorioEvaluacion;
import es.agata.renthelper.repositorio.RepositorioComentario;
import es.agata.renthelper.repositorio.RepositorioEventoFormulario;
import es.agata.renthelper.repositorio.RepositorioRubrica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServicioAdmin {

	private static final Logger log = LoggerFactory.getLogger(ServicioAdmin.class);

	private final RepositorioAnuncio repoAnuncios;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioEvaluacion repoEvaluaciones;
	private final RepositorioEsquemaFormulario repoEsquemas;
	private final RepositorioRubrica repoRubricas;
	private final RepositorioEventoFormulario repoEventos;
	private final RepositorioComentario repoComentarios;
	private final RepositorioAjustes repoAjustes;
	private final BonificacionFiscal bonificacionFiscal;
	private final ValidadorEsquema validadorEsquema;
	private final ValidadorRubrica validadorRubrica;
	private final ServicioOutbox outbox;
	private final String urlPublica;

	public ServicioAdmin(RepositorioAnuncio repoAnuncios, RepositorioCandidatura repoCandidaturas,
	                     RepositorioEvaluacion repoEvaluaciones, RepositorioEsquemaFormulario repoEsquemas,
	                     RepositorioRubrica repoRubricas, RepositorioEventoFormulario repoEventos,
	                     RepositorioComentario repoComentarios, RepositorioAjustes repoAjustes,
	                     BonificacionFiscal bonificacionFiscal,
	                     ValidadorEsquema validadorEsquema, ValidadorRubrica validadorRubrica,
	                     ServicioOutbox outbox, PropiedadesRentHelper propiedades) {
		this.repoAnuncios = repoAnuncios;
		this.repoCandidaturas = repoCandidaturas;
		this.repoEvaluaciones = repoEvaluaciones;
		this.repoEsquemas = repoEsquemas;
		this.repoRubricas = repoRubricas;
		this.repoEventos = repoEventos;
		this.repoComentarios = repoComentarios;
		this.repoAjustes = repoAjustes;
		this.bonificacionFiscal = bonificacionFiscal;
		this.validadorEsquema = validadorEsquema;
		this.validadorRubrica = validadorRubrica;
		this.outbox = outbox;
		this.urlPublica = propiedades.urlPublica();
	}

	// --- Anuncios ----------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DtosAdmin.ResumenAnuncio> listarAnuncios() {
		return repoAnuncios.findAllByOrderByCreadoEnDesc().stream().map(this::aResumen).toList();
	}

	@Transactional(readOnly = true)
	public DtosAdmin.ResumenAnuncio anuncio(UUID id) {
		return aResumen(buscarAnuncio(id));
	}

	@Transactional
	public DtosAdmin.ResumenAnuncio crearAnuncio(DtosAdmin.PeticionAnuncio peticion) {
		exigirCampos(peticion);
		comprobarCompatibilidad(peticion.formSchemaId(), peticion.rubricaId());

		String slug = GeneradorTokens.slug();
		while (repoAnuncios.existsBySlug(slug)) {
			slug = GeneradorTokens.slug();
		}
		Anuncio anuncio = new Anuncio(slug, peticion.titulo(), peticion.rentaMensual(),
				peticion.formSchemaId(), peticion.rubricaId());
		aplicar(anuncio, peticion);
		repoAnuncios.save(anuncio);

		log.info("Anuncio creado: {} · {} · {} € · enlace /c/{}", anuncio.getId(), anuncio.getTitulo(),
				anuncio.getRentaMensual(), slug);
		return aResumen(anuncio);
	}

	private void exigirCampos(DtosAdmin.PeticionAnuncio peticion) {
		if (peticion.titulo() == null || peticion.titulo().isBlank()) {
			throw ExcepcionNegocio.invalido("TITULO", "El anuncio necesita un título.");
		}
		if (peticion.rentaMensual() == null || peticion.rentaMensual().signum() <= 0) {
			throw ExcepcionNegocio.invalido("RENTA", "La renta mensual tiene que ser mayor que cero.");
		}
		if (peticion.formSchemaId() == null || peticion.rubricaId() == null) {
			throw ExcepcionNegocio.invalido("CONFIGURACION",
					"Hay que elegir formulario y rúbrica para el anuncio.");
		}
	}

	/**
	 * Comprueba que la rúbrica elegida sabe leer el formulario elegido.
	 *
	 * <p>Sin esto se puede montar una combinación en la que los criterios apuntan a campos que
	 * ese formulario no tiene. No falla nada: simplemente puntúa cero en silencio, y eso se
	 * descubre tarde, cuando ya han entrado cien candidaturas mal ordenadas.
	 */
	private void comprobarCompatibilidad(UUID formSchemaId, UUID rubricaId) {
		if (formSchemaId == null || rubricaId == null) {
			return;
		}
		var formulario = repoEsquemas.findById(formSchemaId)
				.orElseThrow(() -> ExcepcionNegocio.invalido("FORMULARIO", "El formulario no existe."))
				.getDefinicion();
		var definicionRubrica = repoRubricas.findById(rubricaId)
				.orElseThrow(() -> ExcepcionNegocio.invalido("RUBRICA", "La rúbrica no existe."))
				.getDefinicion();

		List<String> problemas = validadorRubrica.validar(definicionRubrica, formulario);
		if (!problemas.isEmpty()) {
			throw ExcepcionNegocio.invalido("INCOMPATIBLES",
					"Esa rúbrica no encaja con ese formulario: " + String.join(" | ", problemas));
		}
	}

	@Transactional
	public DtosAdmin.ResumenAnuncio actualizarAnuncio(UUID id, DtosAdmin.PeticionAnuncio peticion) {
		Anuncio anuncio = buscarAnuncio(id);
		if (peticion.titulo() != null) {
			anuncio.setTitulo(peticion.titulo());
		}
		if (peticion.rentaMensual() != null) {
			anuncio.setRentaMensual(peticion.rentaMensual());
		}
		if (peticion.formSchemaId() != null) {
			anuncio.setFormSchemaId(peticion.formSchemaId());
		}
		if (peticion.rubricaId() != null) {
			anuncio.setRubricaId(peticion.rubricaId());
		}
		aplicar(anuncio, peticion);
		comprobarCompatibilidad(anuncio.getFormSchemaId(), anuncio.getRubricaId());

		log.info("Anuncio {} actualizado", anuncio.getId());
		return aResumen(repoAnuncios.save(anuncio));
	}

	/**
	 * Aplica sólo lo que viene en la petición.
	 *
	 * <p>Semántica de PATCH: un campo ausente significa «no lo toques», no «bórralo». El
	 * interruptor de altas de la lista de anuncios manda únicamente `aceptandoCandidaturas`, y
	 * con una asignación incondicional se llevaba por delante la dirección, las habitaciones y
	 * la fecha de disponibilidad sin que nada avisara.
	 *
	 * <p>Para vaciar la dirección se manda cadena vacía, que es distinto de no mandarla.
	 */
	private void aplicar(Anuncio anuncio, DtosAdmin.PeticionAnuncio peticion) {
		if (peticion.direccion() != null) {
			anuncio.setDireccion(peticion.direccion().isBlank() ? null : peticion.direccion());
		}
		if (peticion.habitaciones() != null) {
			anuncio.setHabitaciones(peticion.habitaciones());
		}
		if (peticion.disponibleDesde() != null) {
			anuncio.setDisponibleDesde(peticion.disponibleDesde());
		}
		if (peticion.idiomas() != null && !peticion.idiomas().isEmpty()) {
			anuncio.setIdiomas(peticion.idiomas());
		}
		if (peticion.idiomaPorDefecto() != null) {
			anuncio.setIdiomaPorDefecto(peticion.idiomaPorDefecto());
		}
		if (peticion.aceptandoCandidaturas() != null) {
			anuncio.setAceptandoCandidaturas(peticion.aceptandoCandidaturas());
		}
		if (peticion.umbralAlerta() != null) {
			anuncio.setUmbralAlerta(peticion.umbralAlerta());
		}
	}

	// --- Candidaturas ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DtosAdmin.FilaCandidatura> candidaturas(UUID anuncioId, boolean incluirBorradores) {
		// Los ajustes se leen UNA vez y se pasan a cada fila. Dejar que `aFila` los buscara por su
		// cuenta serían ciento cincuenta consultas idénticas para responder a una sola pantalla.
		Ajustes ajustes = repoAjustes.findFirstBy().orElse(null);
		return repoCandidaturas.paraTriaje(anuncioId, incluirBorradores).stream()
				.map(c -> aFila(c, ajustes))
				.toList();
	}

	@Transactional(readOnly = true)
	public DtosAdmin.FichaCandidatura ficha(UUID candidaturaId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		EsquemaFormulario esquema = repoEsquemas.findById(candidatura.getFormSchemaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esquema inexistente"));
		List<DtosAdmin.EvaluacionDto> evaluaciones =
				repoEvaluaciones.findByCandidaturaIdOrderByCreadaEnDesc(candidaturaId).stream()
						.map(this::aDto).toList();
		return new DtosAdmin.FichaCandidatura(aFila(candidatura), esquema.getDefinicion(),
				candidatura.getRespuestas(), evaluaciones, comentarios(candidaturaId),
				bonificacion(candidatura.getRespuestas()));
	}

	@Transactional
	public DtosAdmin.FilaCandidatura triar(UUID candidaturaId, DtosAdmin.PeticionTriaje peticion) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		EstadoCandidatura nuevo;
		try {
			nuevo = EstadoCandidatura.valueOf(peticion.estado());
		} catch (IllegalArgumentException e) {
			throw ExcepcionNegocio.invalido("ESTADO", "Estado desconocido: " + peticion.estado());
		}
		if (!nuevo.esTriada()) {
			throw ExcepcionNegocio.invalido("ESTADO", "Ese estado no es una decisión de triaje.");
		}
		candidatura.triar(nuevo, peticion.puntuacionManual());
		log.info("Triaje de {} ({}): {}{}", candidatura.getId(), candidatura.getNombre(), nuevo,
				peticion.puntuacionManual() == null ? "" : " · nota manual " + peticion.puntuacionManual());
		return aFila(repoCandidaturas.save(candidatura));
	}

	/**
	 * Reencola todas las candidaturas del anuncio.
	 *
	 * <p>Es lo que hace útil versionar la rúbrica: cambias las reglas, repuntúas en lote y
	 * comparas el ranking nuevo con el anterior, que sigue guardado.
	 */
	@Transactional
	public int reevaluar(UUID anuncioId) {
		List<Candidatura> candidaturas = repoCandidaturas.paraTriaje(anuncioId, false);
		candidaturas.forEach(c -> outbox.encolarEvaluacion(c.getId()));
		log.info("Reencoladas {} candidaturas del anuncio {} para repuntuar", candidaturas.size(), anuncioId);
		return candidaturas.size();
	}

	/**
	 * Borrado definitivo de una candidatura y todo su rastro.
	 *
	 * <p>Existe por el derecho de supresión del RGPD: el aviso de privacidad que firma el
	 * candidato promete que puede pedirla, y sin esto la única forma de cumplirlo sería entrar a
	 * la base de datos a mano.
	 *
	 * <p>No es lo mismo que descartar. Descartar es una decisión de triaje y conserva el registro,
	 * que es lo que evita volver a valorar a la misma persona dentro de tres semanas sin acordarte.
	 * Esto borra, y no se puede deshacer.
	 *
	 * <p>El log deja constancia de la supresión sin datos personales: para acreditar que se hizo
	 * basta el identificador, y volver a escribir el nombre en un fichero de log contradiría el
	 * propio borrado.
	 */
	@Transactional
	public void borrarCandidatura(UUID candidaturaId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));

		repoEvaluaciones.deleteByCandidaturaId(candidaturaId);
		repoEventos.deleteByCandidaturaId(candidaturaId);
		repoComentarios.deleteByCandidaturaId(candidaturaId);
		repoCandidaturas.delete(candidatura);

		log.warn("Candidatura {} del anuncio {} borrada de forma definitiva a petición del panel",
				candidaturaId, candidatura.getAnuncioId());
	}

	/** Lanza la evaluación con IA de una candidatura cuya llamada se había omitido. */
	@Transactional
	public void evaluarConIa(UUID candidaturaId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		outbox.encolarEvaluacion(candidatura.getId(), true);
		log.info("Evaluación con IA solicitada a mano para {}", candidaturaId);
	}

	/**
	 * Repuntúa una sola candidatura.
	 *
	 * <p>El lote por anuncio sirve cuando cambias la rúbrica, pero no cuando el problema es de
	 * una en concreto: el LLM falló y se quedó con la nota de las reglas, o acabas de corregir
	 * un criterio mirando precisamente a esa persona. Reencolar las ciento cincuenta para
	 * arreglar una era la única salida, y con cupo gratuito ni siquiera cabía.
	 *
	 * <p>No fuerza el LLM: si se omitió por nombre aparentemente inventado, se sigue omitiendo.
	 * Esa decisión tiene su propio botón, para que repuntuar no gaste llamadas a escondidas.
	 */
	@Transactional
	public void reevaluarCandidatura(UUID candidaturaId, UUID proveedorId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		outbox.encolarEvaluacion(candidatura.getId(), false, proveedorId);
		log.info("Repuntuación solicitada a mano para {}{}", candidaturaId,
				proveedorId == null ? "" : " con el proveedor " + proveedorId);
	}

	/**
	 * Embudo de cumplimentación: dónde se cae la gente.
	 *
	 * <p>Con 150 personas rellenando desde el móvil, cada paso de más cuesta candidaturas — y las
	 * que abandonan son desproporcionadamente las que tienen otras opciones. Saber en qué paso
	 * concreto se caen es lo que dice si hay que acortar el formulario.
	 *
	 * <p>Sólo cuenta las que pasaron por el formulario: las sintéticas del sembrador se insertan
	 * directamente y no generan eventos, así que no ensucian la estadística.
	 */
	@Transactional(readOnly = true)
	public List<DtosAdmin.PasoEmbudo> embudo(UUID anuncioId) {
		Anuncio anuncio = buscarAnuncio(anuncioId);
		List<Paso> pasos = repoEsquemas.findById(anuncio.getFormSchemaId())
				.map(e -> e.getDefinicion().pasos()).orElse(List.of());
		if (pasos.isEmpty()) {
			return List.of();
		}

		Map<Integer, Long> completadasPorPaso = new HashMap<>();
		for (Object[] fila : repoEventos.embudoPorPaso(anuncioId)) {
			completadasPorPaso.put(((Number) fila[0]).intValue(), ((Number) fila[1]).longValue());
		}

		long altas = 0;
		long enviadas = 0;
		for (Object[] fila : repoEventos.conteosPorTipo(anuncioId)) {
			if ("ALTA".equals(fila[0])) {
				altas = ((Number) fila[1]).longValue();
			} else {
				enviadas = ((Number) fila[1]).longValue();
			}
		}

		List<DtosAdmin.PasoEmbudo> embudo = new ArrayList<>();
		// Quien llega a un paso es quien superó el anterior; al primero llegan todas las altas.
		long alcanzaron = altas;
		for (int i = 0; i < pasos.size(); i++) {
			long completaron = completadasPorPaso.getOrDefault(i, 0L);
			embudo.add(DtosAdmin.PasoEmbudo.de(i,
					pasos.get(i).titulo().texto(anuncio.getIdiomaPorDefecto()),
					alcanzaron, completaron));
			alcanzaron = completaron;
		}
		// Última etapa: completar el formulario no es lo mismo que enviarlo. Si aquí hay caída,
		// es que la validación final está rechazando algo.
		embudo.add(DtosAdmin.PasoEmbudo.de(pasos.size(), "Solicitud enviada", alcanzaron, enviadas));
		return embudo;
	}

	// --- Esquemas y rúbricas -----------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DtosAdmin.ResumenEsquema> listarEsquemas() {
		return repoEsquemas.findAllByOrderByNombreAscVersionDesc().stream()
				.map(e -> new DtosAdmin.ResumenEsquema(e.getId(), e.getNombre(), e.getVersion(),
						e.getPublicadoEn(), e.getDefinicion()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<DtosAdmin.ResumenRubrica> listarRubricas() {
		return repoRubricas.findAllByOrderByNombreAscVersionDesc().stream()
				.map(r -> new DtosAdmin.ResumenRubrica(r.getId(), r.getNombre(), r.getVersion(),
						r.getPublicadaEn(), r.getDefinicion()))
				.toList();
	}

	/**
	 * Copia un esquema.
	 *
	 * <p>No revalida: lo que se copia ya pasó la validación al guardarse. Es además la vía
	 * práctica para empezar uno nuevo — partir del que funciona y quitar lo que sobre cuesta
	 * mucho menos que escribir un JSON desde cero.
	 */
	@Transactional
	public DtosAdmin.ResumenEsquema duplicarEsquema(UUID id) {
		EsquemaFormulario original = repoEsquemas.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esquema inexistente"));
		EsquemaFormulario copia = new EsquemaFormulario(original.getNombre() + " (copia)",
				original.getVersion() + 1, original.getDefinicion());
		repoEsquemas.save(copia);
		log.info("Esquema {} duplicado en {}", id, copia.getId());
		return new DtosAdmin.ResumenEsquema(copia.getId(), copia.getNombre(), copia.getVersion(),
				copia.getPublicadoEn(), copia.getDefinicion());
	}

	@Transactional
	public DtosAdmin.ResumenRubrica duplicarRubrica(UUID id) {
		Rubrica original = repoRubricas.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Rúbrica inexistente"));
		Rubrica copia = new Rubrica(original.getNombre() + " (copia)", original.getVersion() + 1,
				original.getDefinicion());
		repoRubricas.save(copia);
		log.info("Rúbrica {} duplicada en {}", id, copia.getId());
		return new DtosAdmin.ResumenRubrica(copia.getId(), copia.getNombre(), copia.getVersion(),
				copia.getPublicadaEn(), copia.getDefinicion());
	}

	/** Esquema mínimo que pasa la validación, para empezar a escribir encima. */
	@Transactional
	public DtosAdmin.ResumenEsquema crearEsquemaVacio(List<String> idiomas) {
		List<String> lista = idiomas == null || idiomas.isEmpty() ? List.of("es") : idiomas;
		EsquemaFormulario esquema = new EsquemaFormulario("Formulario nuevo", 1,
				PlantillaFormulario.minima(lista));
		repoEsquemas.save(esquema);
		log.info("Esquema vacío creado: {}", esquema.getId());
		return new DtosAdmin.ResumenEsquema(esquema.getId(), esquema.getNombre(), esquema.getVersion(),
				esquema.getPublicadoEn(), esquema.getDefinicion());
	}

	@Transactional
	public DtosAdmin.ResultadoGuardado guardarEsquema(UUID id, DtosAdmin.PeticionEsquema peticion,
	                                                  List<String> idiomas) {
		List<String> problemas = validadorEsquema.validar(peticion.definicion(),
				idiomas == null || idiomas.isEmpty() ? List.of("es") : idiomas);
		if (!problemas.isEmpty()) {
			log.warn("Esquema {} rechazado con {} problemas: {}", id, problemas.size(), problemas);
			return new DtosAdmin.ResultadoGuardado(false, problemas, id);
		}
		log.info("Guardando esquema de formulario {} (v{})", peticion.nombre(), peticion.version());
		EsquemaFormulario esquema = id == null
				? new EsquemaFormulario(peticion.nombre(), valorOUno(peticion.version()), peticion.definicion())
				: repoEsquemas.findById(id)
						.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esquema inexistente"));
		if (id != null) {
			esquema.setNombre(peticion.nombre());
			esquema.setDefinicion(peticion.definicion());
			esquema.setVersion(valorOUno(peticion.version()));
		}
		esquema.publicar();
		return new DtosAdmin.ResultadoGuardado(true, List.of(), repoEsquemas.save(esquema).getId());
	}

	@Transactional
	public DtosAdmin.ResultadoGuardado guardarRubrica(UUID id, DtosAdmin.PeticionRubrica peticion,
	                                                  UUID formSchemaId) {
		var formulario = repoEsquemas.findById(formSchemaId)
				.map(EsquemaFormulario::getDefinicion)
				.orElseThrow(() -> ExcepcionNegocio.invalido("ESQUEMA",
						"Hay que indicar contra qué formulario se valida la rúbrica."));

		List<String> problemas = validadorRubrica.validar(peticion.definicion(), formulario);
		if (!problemas.isEmpty()) {
			log.warn("Rúbrica {} rechazada con {} problemas: {}", id, problemas.size(), problemas);
			return new DtosAdmin.ResultadoGuardado(false, problemas, id);
		}
		log.info("Guardando rúbrica {} (v{})", peticion.nombre(), peticion.version());
		Rubrica rubrica = id == null
				? new Rubrica(peticion.nombre(), valorOUno(peticion.version()), peticion.definicion())
				: repoRubricas.findById(id)
						.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Rúbrica inexistente"));
		if (id != null) {
			rubrica.setNombre(peticion.nombre());
			rubrica.setDefinicion(peticion.definicion());
			rubrica.setVersion(valorOUno(peticion.version()));
		}
		rubrica.publicar();
		return new DtosAdmin.ResultadoGuardado(true, List.of(), repoRubricas.save(rubrica).getId());
	}

	// --- Conversión --------------------------------------------------------------------

	private Anuncio buscarAnuncio(UUID id) {
		return repoAnuncios.findById(id)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Anuncio inexistente"));
	}

	private DtosAdmin.ResumenAnuncio aResumen(Anuncio anuncio) {
		// Sin triar = lo que aún espera una decisión tuya. Es el número que importa cuando
		// entran 150 en veinte horas.
		long sinTriar = repoCandidaturas.countByAnuncioIdAndEstadoIn(anuncio.getId(),
				List.of(EstadoCandidatura.ENVIADA, EstadoCandidatura.EVALUADA));
		long total = repoCandidaturas.countByAnuncioIdAndEstadoIn(anuncio.getId(),
				List.of(EstadoCandidatura.ENVIADA, EstadoCandidatura.EVALUADA, EstadoCandidatura.DESCARTADA,
						EstadoCandidatura.CITADA, EstadoCandidatura.SELECCIONADA));
		return new DtosAdmin.ResumenAnuncio(
				anuncio.getId(), anuncio.getSlug(), anuncio.getTitulo(), anuncio.getDireccion(),
				anuncio.getRentaMensual(), anuncio.getHabitaciones(), anuncio.getDisponibleDesde(),
				anuncio.getIdiomas(), anuncio.getIdiomaPorDefecto(), anuncio.getFormSchemaId(),
				anuncio.getRubricaId(), anuncio.isAceptandoCandidaturas(), anuncio.getUmbralAlerta(),
				anuncio.getUltimoDigestEn(), total, sinTriar,
				urlPublica + "/c/" + anuncio.getSlug());
	}

	/**
	 * Si este contrato entraría en la bonificación por edad.
	 *
	 * <p>Devuelve null cuando no la tienes activada en Ajustes, para que la ficha no enseñe un
	 * bloque vacío. El cálculo es sólo de edades contra el tramo que declaraste: la aplicación no
	 * sabe de fiscalidad, y el porcentaje que se muestra es el texto que escribiste tú.
	 */
	private DtosAdmin.BonificacionDto bonificacion(Map<String, Object> respuestas) {
		BonificacionFiscal.Resultado resultado =
				bonificacionFiscal.evaluar(repoAjustes.findFirstBy().orElse(null), respuestas);
		return resultado.hayQueMostrarlo()
				? new DtosAdmin.BonificacionDto(resultado.grado().name(), resultado.aplicable(),
						resultado.resumen(), resultado.nota())
				: null;
	}

	// --- Comentarios ---------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<DtosAdmin.ComentarioDto> comentarios(UUID candidaturaId) {
		return repoComentarios.findByCandidaturaIdOrderByCreadoEnDesc(candidaturaId).stream()
				.map(c -> new DtosAdmin.ComentarioDto(c.getId(), c.getTexto(), c.getCreadoEn()))
				.toList();
	}

	/**
	 * Apunta algo sobre una candidatura.
	 *
	 * <p>El log deja constancia sin el texto: son notas personales sobre alguien identificado y
	 * volcarlas al log las duplicaría en un sitio que no se purga con la candidatura.
	 */
	@Transactional
	public DtosAdmin.ComentarioDto comentar(UUID candidaturaId, DtosAdmin.PeticionComentario peticion) {
		if (peticion.texto() == null || peticion.texto().isBlank()) {
			throw ExcepcionNegocio.invalido("TEXTO", "El comentario no puede estar vacío.");
		}
		if (!repoCandidaturas.existsById(candidaturaId)) {
			throw ExcepcionNegocio.noEncontrado("Candidatura inexistente");
		}
		ComentarioCandidatura comentario = repoComentarios.save(
				new ComentarioCandidatura(candidaturaId, peticion.texto().trim()));
		log.info("Comentario añadido a la candidatura {}", candidaturaId);
		return new DtosAdmin.ComentarioDto(comentario.getId(), comentario.getTexto(),
				comentario.getCreadoEn());
	}

	@Transactional
	public void borrarComentario(UUID comentarioId) {
		repoComentarios.deleteById(comentarioId);
		log.info("Comentario {} borrado", comentarioId);
	}

	/**
	 * Pone tu nota sin tocar el estado de triaje.
	 *
	 * <p>A diferencia del atajo de teclado, que además pasa la candidatura a CITADA. Puntuar y
	 * decidir son cosas distintas: querer anotar «un 3» no debería meter a nadie en la lista de
	 * visitas por su cuenta.
	 */
	@Transactional
	public DtosAdmin.FilaCandidatura anotarNota(UUID candidaturaId, DtosAdmin.PeticionNota peticion) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		candidatura.ponerNotaManual(peticion.puntuacionManual());
		log.info("Nota manual de {}: {}", candidaturaId, peticion.puntuacionManual());
		return aFila(repoCandidaturas.save(candidatura));
	}

	private DtosAdmin.FilaCandidatura aFila(Candidatura candidatura) {
		return aFila(candidatura, repoAjustes.findFirstBy().orElse(null));
	}

	private DtosAdmin.FilaCandidatura aFila(Candidatura candidatura, Ajustes ajustes) {
		BonificacionFiscal.Resultado fiscal =
				bonificacionFiscal.evaluar(ajustes, candidatura.getRespuestas());
		Optional<Evaluacion> principal = repoEvaluaciones
				.findFirstByCandidaturaIdAndRolOrderByCreadaEnDesc(candidatura.getId(), RolEvaluacion.PRINCIPAL);
		return new DtosAdmin.FilaCandidatura(
				candidatura.getId(), candidatura.getAnuncioId(), candidatura.getNombre(),
				candidatura.getVerificacionNombre(), candidatura.getMotivoNombre(),
				candidatura.isLlmOmitidoPorNombre(),
				candidatura.getPuntuacion(),
				candidatura.getPuntuacionManual(), candidatura.getEstado().name(),
				candidatura.isNoCumpleMinimos(), candidatura.getMotivosMinimos(), candidatura.isSintetica(),
				candidatura.getEnviadaEn(), candidatura.getActualizadaEn(),
				candidatura.getSegundosCumplimentacion(),
				principal.map(Evaluacion::getResumen).orElse(null),
				principal.map(Evaluacion::getValoracion).orElse(null),
				principal.map(Evaluacion::getBanderas).orElse(List.of()),
				principal.map(Evaluacion::getPreguntasPendientes).orElse(List.of()),
				principal.map(Evaluacion::getConfianza).orElse(null),
				principal.map(Evaluacion::getProveedor).orElse(null),
				principal.map(Evaluacion::getModelo).orElse(null),
				fiscal.grado() == null ? null : fiscal.grado().name(),
				fiscal.resumen(),
				principal.map(Evaluacion::getError).orElse(null),
				candidatura.getTelefonoNormalizado(), candidatura.getEmail());
	}

	private DtosAdmin.EvaluacionDto aDto(Evaluacion e) {
		return new DtosAdmin.EvaluacionDto(e.getId(), e.getRol().name(), e.getProveedor(), e.getModelo(),
				e.getPuntuacionDeterminista(), e.getAjusteLlm(), e.getPuntuacionTotal(), e.getDesglose(),
				e.getResumen(), e.getValoracion(), e.getBanderas(), e.getPreguntasPendientes(),
				e.getConfianza(),
				e.getRubricaVersion(), e.getPromptVersion(), e.getTokensEntrada(), e.getTokensSalida(),
				e.getLatenciaMs(), e.getError(), e.getCreadaEn());
	}

	private static int valorOUno(Integer version) {
		return version == null || version <= 0 ? 1 : version;
	}
}
