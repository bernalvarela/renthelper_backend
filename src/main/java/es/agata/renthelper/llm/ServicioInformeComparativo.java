package es.agata.renthelper.llm;

import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.api.admin.DtosAdmin;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.EstadoCandidatura;
import es.agata.renthelper.dominio.InformeComparativo;
import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.dominio.Rubrica;
import es.agata.renthelper.puntuacion.ContextoAnuncio;
import es.agata.renthelper.puntuacion.MotorDeterminista;
import es.agata.renthelper.puntuacion.NotaCombinada;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEsquemaFormulario;
import es.agata.renthelper.repositorio.RepositorioInformeComparativo;
import es.agata.renthelper.repositorio.RepositorioRubrica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Informe que compara a los finalistas de un anuncio en una sola llamada al modelo.
 *
 * <p>Finalistas son las candidaturas citadas o seleccionadas: las que ya has filtrado y entre las
 * que dudas. Hay un máximo (renthelper.comparativa.max-finalistas) porque esto es para decidir
 * entre los últimos: con veinte el modelo se fija en unos pocos e ignora al resto. Si hay más,
 * el panel pide elegir cuáles.
 *
 * <p>Se mandan barajadas, con etiqueta (C1, C2...) y sin nombre, y con las mismas salvaguardas que la
 * evaluación individual: si alguna es real, sólo modelos aptos para datos reales.
 *
 * <p>La llamada va en la petición y no por el outbox: se pide a mano y se espera el resultado
 * mirando la pantalla. Y NO dentro de una transacción: sería tener conexiones de base de datos
 * abiertas el minuto que tarda el modelo.
 */
@Service
public class ServicioInformeComparativo {

	private static final Logger log = LoggerFactory.getLogger(ServicioInformeComparativo.class);

	private static final Set<EstadoCandidatura> FINALISTAS = Set.of(EstadoCandidatura.CITADA,
			EstadoCandidatura.SELECCIONADA);

	private final RepositorioAnuncio repoAnuncios;
	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioEsquemaFormulario repoEsquemas;
	private final RepositorioRubrica repoRubricas;
	private final RepositorioInformeComparativo repoInformes;
	private final MotorDeterminista motor;
	private final ConstructorPrompt constructorPrompt;
	private final EvaluadorLlm evaluador;
	private final OrquestadorEvaluacion orquestador;
	private final PropiedadesRentHelper propiedades;

	public ServicioInformeComparativo(RepositorioAnuncio repoAnuncios, RepositorioCandidatura repoCandidaturas,
	                                  RepositorioEsquemaFormulario repoEsquemas, RepositorioRubrica repoRubricas,
	                                  RepositorioInformeComparativo repoInformes, MotorDeterminista motor,
	                                  ConstructorPrompt constructorPrompt, EvaluadorLlm evaluador,
	                                  OrquestadorEvaluacion orquestador, PropiedadesRentHelper propiedades) {
		this.repoAnuncios = repoAnuncios;
		this.repoCandidaturas = repoCandidaturas;
		this.repoEsquemas = repoEsquemas;
		this.repoRubricas = repoRubricas;
		this.repoInformes = repoInformes;
		this.motor = motor;
		this.constructorPrompt = constructorPrompt;
		this.evaluador = evaluador;
		this.orquestador = orquestador;
		this.propiedades = propiedades;
	}

	/** Los finalistas que se pueden comparar, el máximo y el último informe. */
	@Transactional(readOnly = true)
	public DtosAdmin.EstadoComparativa estado(UUID anuncioId) {
		List<Candidatura> finalistas = finalistas(anuncioId);
		DtosAdmin.InformeComparativoDto ultimo = repoInformes.findFirstByAnuncioIdOrderByCreadoEnDesc(anuncioId)
				.map(informe -> aDto(informe, finalistas))
				.orElse(null);
		return new DtosAdmin.EstadoComparativa(
				propiedades.comparativa().maxFinalistas(),
				finalistas.stream()
						.map(c -> new DtosAdmin.FinalistaElegible(c.getId(), c.getNombre(), c.getPuntuacion(),
								c.getPuntuacionManual(), combinada(c), c.getEstado().name()))
						.toList(),
				ultimo);
	}

	/**
	 * Genera un informe nuevo con esas candidaturas. Tienen que ser finalistas de ese anuncio, y
	 * entre dos y el máximo configurado.
	 */
	public DtosAdmin.InformeComparativoDto generar(UUID anuncioId, List<UUID> candidaturaIds) {
		Anuncio anuncio = repoAnuncios.findById(anuncioId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Anuncio inexistente"));
		int maximo = propiedades.comparativa().maxFinalistas();

		// Sin repetidas ni vacías: un id vacío llega como null y List.copyOf no los admite.
		List<UUID> pedidas = candidaturaIds == null ? List.of()
				: candidaturaIds.stream().filter(Objects::nonNull).distinct().toList();
		if (pedidas.size() < 2) {
			throw ExcepcionNegocio.invalido("POCOS_FINALISTAS", "Para comparar hacen falta al menos dos finalistas.");
		}
		if (pedidas.size() > maximo) {
			throw ExcepcionNegocio.invalido("DEMASIADOS_FINALISTAS",
					"Se pueden comparar como mucho %d a la vez; has elegido %d.".formatted(maximo, pedidas.size()));
		}

		Map<UUID, Candidatura> finalistas = finalistas(anuncioId).stream()
				.collect(Collectors.toMap(Candidatura::getId, Function.identity()));
		List<Candidatura> elegidas = new ArrayList<>();
		for (UUID id : pedidas) {
			Candidatura candidatura = finalistas.get(id);
			if (candidatura == null) {
				throw ExcepcionNegocio.invalido("NO_FINALISTA",
						"Sólo se comparan candidaturas citadas o seleccionadas de este anuncio.");
			}
			elegidas.add(candidatura);
		}

		// Barajadas: los modelos favorecen al primero o al último que leen. Y con etiqueta (C1,
		// C2...) en vez de nombre, que al modelo no le hace falta para comparar; al leer el
		// informe se cambian de vuelta por los nombres.
		Collections.shuffle(elegidas);
		Rubrica rubrica = repoRubricas.findById(anuncio.getRubricaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Rúbrica inexistente"));
		Map<String, SolicitudEvaluacion> porEtiqueta = new LinkedHashMap<>();
		Map<String, String> etiquetas = new LinkedHashMap<>();
		for (int i = 0; i < elegidas.size(); i++) {
			String etiqueta = EtiquetasFinalistas.etiqueta(i);
			Candidatura candidatura = elegidas.get(i);
			porEtiqueta.put(etiqueta, solicitud(candidatura, anuncio, rubrica));
			etiquetas.put(etiqueta, candidatura.getId().toString());
		}

		boolean todasSinteticas = elegidas.stream().allMatch(Candidatura::isSintetica);
		Map.Entry<List<ProveedorLlm>, List<String>> cadena = orquestador.cadenaPara(todasSinteticas);
		if (cadena.getKey().isEmpty()) {
			throw new ExcepcionNegocio(HttpStatus.CONFLICT, "SIN_MODELO",
					"No hay ningún modelo disponible para comparar: " + String.join(" | ", cadena.getValue()));
		}

		String sistema = constructorPrompt.sistemaComparativa(elegidas.size());
		String usuario = constructorPrompt.usuarioComparativa(porEtiqueta);
		List<String> fallos = new ArrayList<>();
		for (ProveedorLlm proveedor : cadena.getKey()) {
			log.info("Comparando {} finalistas del anuncio {} con {}", elegidas.size(), anuncio.getSlug(),
					OrquestadorEvaluacion.describir(proveedor));
			ResultadoInforme resultado = evaluador.comparar(sistema, usuario, proveedor,
					propiedades.comparativa().maxTokensSalida());
			orquestador.contarLlamada(proveedor.getNombre(), resultado.correcto());
			if (resultado.correcto()) {
				InformeComparativo informe = repoInformes.save(new InformeComparativo(anuncioId, etiquetas,
						resultado.informe(), resultado.proveedor(), resultado.modelo(),
						propiedades.llm().promptVersion(), resultado.tokensEntrada(), resultado.tokensSalida(),
						resultado.latenciaMs()));
				return aDto(informe, finalistas(anuncioId));
			}
			fallos.add(proveedor.getNombre() + ": " + resultado.error());
		}
		throw new ExcepcionNegocio(HttpStatus.BAD_GATEWAY, "COMPARATIVA_FALLIDA",
				"Ningún modelo pudo hacer la comparación. " + String.join(" | ", fallos));
	}

	/**
	 * Borra los informes en los que aparece esa candidatura: hablan de ella. Lo llaman el borrado
	 * a mano y la purga del RGPD.
	 */
	@Transactional
	public void olvidarCandidatura(UUID anuncioId, UUID candidaturaId) {
		List<InformeComparativo> conElla = repoInformes.findByAnuncioId(anuncioId).stream()
				.filter(informe -> informe.incluye(candidaturaId))
				.toList();
		if (!conElla.isEmpty()) {
			repoInformes.deleteAll(conElla);
			log.info("Borrados {} informe(s) comparativo(s) que incluían la candidatura {}", conElla.size(),
					candidaturaId);
		}
	}

	/**
	 * Ordenadas por la nota combinada, como la tabla: el panel marca por defecto las primeras.
	 * Tu nota NO se manda al modelo: la comparativa tiene que ser una segunda opinión, y con tu
	 * nota delante tendería a darte la razón.
	 */
	private List<Candidatura> finalistas(UUID anuncioId) {
		return repoCandidaturas.paraTriaje(anuncioId, false).stream()
				.filter(c -> FINALISTAS.contains(c.getEstado()))
				.sorted(Comparator.comparing(this::combinada, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	private Integer combinada(Candidatura candidatura) {
		return NotaCombinada.calcular(candidatura.getPuntuacion(), candidatura.getPuntuacionManual(),
				propiedades.nota().pesoManual());
	}

	/** Lo mismo que se le da al modelo en la evaluación individual, sin nombre ni bonificación. */
	private SolicitudEvaluacion solicitud(Candidatura candidatura, Anuncio anuncio, Rubrica rubrica) {
		var esquema = repoEsquemas.findById(candidatura.getFormSchemaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esquema inexistente"));
		return new SolicitudEvaluacion(esquema.getDefinicion(), rubrica.getDefinicion(), candidatura.getRespuestas(),
				motor.puntuar(rubrica.getDefinicion(), esquema.getDefinicion(), candidatura.getRespuestas(),
						ContextoAnuncio.de(anuncio)),
				anuncio.getTitulo(), anuncio.getRentaMensual(), anuncio.getHabitaciones(),
				anuncio.getDisponibleDesde(), candidatura.isSintetica(), null, null);
	}

	/**
	 * Traduce las etiquetas a personas, también dentro del texto: el modelo escribe «C2 tiene
	 * menos margen que C4», y eso no le dice nada a quien lo lee. Desactualizado = alguna ya no es
	 * finalista, se ha borrado, o ha cambiado (repuntuada, respuestas corregidas) después.
	 */
	private DtosAdmin.InformeComparativoDto aDto(InformeComparativo informe, List<Candidatura> finalistasActuales) {
		Map<UUID, Candidatura> actuales = finalistasActuales.stream()
				.collect(Collectors.toMap(Candidatura::getId, Function.identity()));
		Map<String, UUID> idPorEtiqueta = new LinkedHashMap<>();
		informe.getCandidaturas().forEach((etiqueta, id) -> idPorEtiqueta.put(etiqueta, UUID.fromString(id)));
		Map<UUID, Candidatura> todas = repoCandidaturas.findAllById(idPorEtiqueta.values()).stream()
				.collect(Collectors.toMap(Candidatura::getId, Function.identity()));

		boolean desactualizado = idPorEtiqueta.values().stream().anyMatch(id -> {
			Candidatura actual = actuales.get(id);
			return actual == null || actual.getActualizadaEn().isAfter(informe.getCreadoEn());
		});

		Map<String, String> nombres = new LinkedHashMap<>();
		idPorEtiqueta.forEach((etiqueta, id) -> nombres.put(etiqueta, nombre(todas.get(id))));
		Function<String, String> conNombres = texto -> EtiquetasFinalistas.sustituir(texto, nombres);

		InformeComparativoLlm contenido = informe.getContenido();
		List<DtosAdmin.FinalistaInforme> finalistas = contenido.finalistas().stream()
				.map(f -> {
					UUID id = idPorEtiqueta.get(f.etiqueta());
					Candidatura candidatura = id == null ? null : todas.get(id);
					return new DtosAdmin.FinalistaInforme(f.etiqueta(), id, nombre(candidatura),
							candidatura == null ? null : candidatura.getPuntuacion(),
							candidatura == null ? null : candidatura.getPuntuacionManual(),
							candidatura == null ? null : combinada(candidatura),
							candidatura == null ? null : candidatura.getEstado().name(),
							conNombres.apply(f.datosClave()), conNombres.apply(f.puntoFuerte()),
							conNombres.apply(f.puntoDebil()));
				})
				.toList();
		List<DtosAdmin.PosicionInforme> orden = contenido.ordenSugerido().stream()
				.map(p -> {
					UUID id = idPorEtiqueta.get(p.etiqueta());
					return new DtosAdmin.PosicionInforme(p.etiqueta(), id,
							nombre(id == null ? null : todas.get(id)), conNombres.apply(p.motivo()));
				})
				.toList();

		return new DtosAdmin.InformeComparativoDto(informe.getId(), informe.getCreadoEn(), informe.getProveedor(),
				informe.getModelo(), informe.getPromptVersion(), informe.getLatenciaMs(), desactualizado,
				conNombres.apply(contenido.panorama()), finalistas, conNombres.apply(contenido.riesgos()),
				contenido.preguntas().stream().map(conNombres).toList(), orden);
	}

	private static String nombre(Candidatura candidatura) {
		return candidatura == null ? "(candidatura borrada)" : Objects.requireNonNullElse(candidatura.getNombre(), "—");
	}
}
