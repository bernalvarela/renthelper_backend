package es.agata.renthelper.llm;

import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.api.ExcepcionNegocio;
import es.agata.renthelper.config.PropiedadesRentHelper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.ConsumoLlm;
import es.agata.renthelper.dominio.EsquemaFormulario;
import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.dominio.RolEvaluacion;
import es.agata.renthelper.dominio.Rubrica;
import es.agata.renthelper.fiscal.BonificacionFiscal;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import es.agata.renthelper.puntuacion.ContextoAnuncio;
import es.agata.renthelper.puntuacion.MotorDeterminista;
import es.agata.renthelper.puntuacion.modelo.ResultadoDeterminista;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioConsumoLlm;
import es.agata.renthelper.repositorio.RepositorioEsquemaFormulario;
import es.agata.renthelper.repositorio.RepositorioEvaluacion;
import es.agata.renthelper.repositorio.RepositorioProveedorLlm;
import es.agata.renthelper.repositorio.RepositorioRubrica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decide qué proveedores evalúan cada candidatura y persiste el resultado.
 *
 * <p>Tres reglas gobiernan la elección:
 *
 * <ol>
 *   <li><b>Aptitud para datos reales.</b> El tier gratuito se usa para entrenar; con datos de
 *       candidatos reales no hay base legal para esa cesión. Un proveedor no apto se salta si la
 *       candidatura no es sintética, y así no se cuela en producción por un despiste.</li>
 *   <li><b>Cadena de respaldo.</b> Los proveedores no marcados como sombra se prueban en orden
 *       hasta que uno responde; el primero que lo consigue se lleva el rol PRINCIPAL.</li>
 *   <li><b>Modo sombra.</b> Los marcados como sombra se ejecutan además, se guardan y no tocan
 *       el ranking. Es lo que alimenta la vista de desacuerdos y la correlación.</li>
 * </ol>
 *
 * <p>Si ningún proveedor responde, la candidatura <b>igualmente</b> queda puntuada con el motor
 * determinista. El LLM es un ajuste, no un requisito: un proveedor caído no puede dejar 150
 * candidaturas sin ordenar.
 */
@Service
public class OrquestadorEvaluacion {

	private static final Logger log = LoggerFactory.getLogger(OrquestadorEvaluacion.class);
	private static final String PROVEEDOR_SIN_LLM = "DETERMINISTA";

	private final RepositorioCandidatura repoCandidaturas;
	private final RepositorioAnuncio repoAnuncios;
	private final RepositorioEsquemaFormulario repoEsquemas;
	private final RepositorioRubrica repoRubricas;
	private final RepositorioEvaluacion repoEvaluaciones;
	private final RepositorioConsumoLlm repoConsumo;
	private final RepositorioProveedorLlm repoProveedores;
	private final RepositorioAjustes repoAjustes;
	private final BonificacionFiscal bonificacionFiscal;
	private final MotorDeterminista motor;
	private final EvaluadorLlm evaluador;
	private final PropiedadesRentHelper propiedades;

	public OrquestadorEvaluacion(RepositorioCandidatura repoCandidaturas, RepositorioAnuncio repoAnuncios,
	                             RepositorioEsquemaFormulario repoEsquemas, RepositorioRubrica repoRubricas,
	                             RepositorioEvaluacion repoEvaluaciones, RepositorioConsumoLlm repoConsumo,
	                             RepositorioProveedorLlm repoProveedores, RepositorioAjustes repoAjustes,
	                             BonificacionFiscal bonificacionFiscal,
	                             MotorDeterminista motor, EvaluadorLlm evaluador,
	                             PropiedadesRentHelper propiedades) {
		this.repoCandidaturas = repoCandidaturas;
		this.repoAnuncios = repoAnuncios;
		this.repoEsquemas = repoEsquemas;
		this.repoRubricas = repoRubricas;
		this.repoEvaluaciones = repoEvaluaciones;
		this.repoConsumo = repoConsumo;
		this.repoProveedores = repoProveedores;
		this.repoAjustes = repoAjustes;
		this.bonificacionFiscal = bonificacionFiscal;
		this.motor = motor;
		this.evaluador = evaluador;
		this.propiedades = propiedades;
	}

	@Transactional
	public Evaluacion evaluar(UUID candidaturaId) {
		return evaluar(candidaturaId, false);
	}

	/**
	 * @param forzarLlm vestigio de cuando el veredicto del nombre venía en una llamada aparte y
	 *                  podía ahorrarse la evaluación completa. Con una sola llamada no hay nada
	 *                  que forzar: el nombre se juzga en la misma petición que puntúa.
	 */
	@Transactional
	public Evaluacion evaluar(UUID candidaturaId, boolean forzarLlm) {
		return evaluar(candidaturaId, forzarLlm, null);
	}

	/**
	 * @param proveedorId si viene, se evalúa con ESE modelo aunque no sea el primero de la
	 *                    cadena y aunque esté marcado como sombra. Lo usa el selector de la ficha,
	 *                    para poder pedir la opinión de uno concreto y compararla con otra.
	 */
	@Transactional
	public Evaluacion evaluar(UUID candidaturaId, boolean forzarLlm, UUID proveedorId) {
		Candidatura candidatura = repoCandidaturas.findById(candidaturaId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Candidatura inexistente"));
		Anuncio anuncio = repoAnuncios.findById(candidatura.getAnuncioId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Anuncio inexistente"));
		EsquemaFormulario esquema = repoEsquemas.findById(candidatura.getFormSchemaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Esquema inexistente"));
		Rubrica rubrica = repoRubricas.findById(anuncio.getRubricaId())
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Rúbrica inexistente"));

		log.info("Evaluando candidatura {} ({}) del anuncio {} · rúbrica v{} · sintética: {}",
				candidatura.getId(), candidatura.getNombre(), anuncio.getSlug(), rubrica.getVersion(),
				candidatura.isSintetica());

		ResultadoDeterminista determinista = motor.puntuar(rubrica.getDefinicion(), esquema.getDefinicion(),
				candidatura.getRespuestas(), ContextoAnuncio.de(anuncio));

		log.info("Reglas: {} puntos · {}", determinista.puntuacion(),
				determinista.noCumpleMinimos()
						? "no cumple mínimos (" + String.join("; ", determinista.motivosMinimos()) + ")"
						: "cumple mínimos");

		SolicitudEvaluacion solicitud = new SolicitudEvaluacion(
				esquema.getDefinicion(), rubrica.getDefinicion(), candidatura.getRespuestas(), determinista,
				anuncio.getTitulo(), anuncio.getRentaMensual(), anuncio.getHabitaciones(),
				anuncio.getDisponibleDesde(), candidatura.isSintetica(),
				propiedades.llm().verificarNombre() ? candidatura.getNombre() : null,
				bonificacionFiscal.evaluar(repoAjustes.findFirstBy().orElse(null),
						candidatura.getRespuestas()).resumen());

		Eleccion eleccion = proveedorId == null
				? elegibles(candidatura, false)
				: soloEste(candidatura, proveedorId);

		// La heurística local sigue delante de todo: caza la basura evidente («asdf», cifras,
		// símbolos) sin gastar ni una llamada. Lo que ya no hay es una llamada aparte sólo para
		// el nombre; ese veredicto viene ahora dentro de la evaluación.
		VerificacionNombre porHeuristica = HeuristicaNombre.revisar(candidatura.getNombre());
		if (porHeuristica != null) {
			candidatura.anotarVerificacionNombre(porHeuristica.veredicto(), porHeuristica.motivo());
			log.info("Nombre descartado por heurística: {}", porHeuristica.motivo());
		}

		candidatura.marcarLlmOmitido(false);
		Evaluacion principal = evaluarPrincipal(candidatura, rubrica, determinista, solicitud, eleccion);

		// Las sombras sólo en la evaluación automática. Si has pedido un modelo concreto, quieres
		// ese y nada más: lanzar además las de comparación gastaría cuota que no has pedido.
		for (ProveedorLlm proveedor : proveedorId == null
				? elegibles(candidatura, true).elegibles()
				: List.<ProveedorLlm>of()) {
			log.info("Evaluación en sombra con {} para comparar modelos", proveedor.getNombre());
			ResultadoLlm resultado = evaluador.evaluar(solicitud, proveedor);
			registrarConsumo(proveedor.getNombre(), resultado.correcto());
			repoEvaluaciones.save(construir(candidatura, RolEvaluacion.SOMBRA, rubrica, determinista, resultado));
		}

		// Sólo si este intento manda de verdad. Cuando falla y ya había una valoración buena,
		// `soloReglas` lo degrada a SOMBRA y la candidatura conserva su nota anterior hasta que
		// un reintento salga bien; pisarla aquí dejaría la puntuación sin el ajuste del modelo.
		if (principal.getRol() == RolEvaluacion.PRINCIPAL) {
			candidatura.aplicarEvaluacion(principal.getPuntuacionTotal(), determinista.noCumpleMinimos(),
					determinista.motivosMinimos());
		}
		repoCandidaturas.save(candidatura);

		log.info("Candidatura {} evaluada: {} puntos ({} de reglas {}{}) · proveedor {}",
				candidatura.getId(), principal.getPuntuacionTotal(), principal.getPuntuacionDeterminista(),
				principal.getAjusteLlm() >= 0 ? "+" : "", principal.getAjusteLlm(),
				principal.getProveedor());
		return principal;
	}

	private Evaluacion evaluarPrincipal(Candidatura candidatura, Rubrica rubrica,
	                                    ResultadoDeterminista determinista, SolicitudEvaluacion solicitud,
	                                    Eleccion eleccion) {
		String ultimoError = null;

		for (ProveedorLlm proveedor : eleccion.elegibles()) {
			log.info("Pidiendo evaluación a {} (modelo {})", proveedor.getNombre(), proveedor.getModelo());
			ResultadoLlm resultado = evaluador.evaluar(solicitud, proveedor);
			registrarConsumo(proveedor.getNombre(), resultado.correcto());

			if (resultado.correcto()) {
				return repoEvaluaciones.save(
						construir(candidatura, RolEvaluacion.PRINCIPAL, rubrica, determinista, resultado));
			}
			ultimoError = proveedor.getNombre() + ": " + resultado.error();
			log.error("{} no pudo evaluar la candidatura {}: {}. Paso al siguiente de la cadena.",
					proveedor.getNombre(), candidatura.getId(), resultado.error());
		}

		// Sin LLM utilizable, la puntuación determinista es la definitiva. El motivo concreto va
		// al log y a la propia evaluación: un mensaje genérico obliga a adivinar qué ha pasado.
		String motivo = ultimoError != null
				? ultimoError
				: eleccion.motivosDescarte().isEmpty()
						? "No hay ningún proveedor configurado en renthelper.llm.proveedores"
						: String.join(" | ", eleccion.motivosDescarte());

		log.warn("Candidatura {} puntuada sólo con las reglas. Motivo: {}", candidatura.getId(), motivo);
		return soloReglas(candidatura, rubrica, determinista, motivo);
	}

	/**
	 * Evaluación con la puntuación de reglas como definitiva, anotando por qué no hubo LLM.
	 *
	 * <p>El motivo va a `error` y NO a `resumen`. El resumen es la valoración del modelo; meter
	 * ahí un fallo lo disfraza de veredicto, y el panel acaba enseñando «cuota agotada» donde
	 * debería ir el juicio sobre el candidato. Sin resumen, la interfaz sabe que no lo hay y
	 * puede avisar del error como error.
	 *
	 * <p>Y si la candidatura ya tenía una valoración buena, este intento fallido NO se queda con
	 * el rol principal: la ficha y la tabla toman siempre la PRINCIPAL más reciente, así que un
	 * 503 pasajero borraba de la pantalla el resumen y la valoración y dejaba la nota en la de
	 * sólo reglas.
	 */
	private Evaluacion soloReglas(Candidatura candidatura, Rubrica rubrica,
	                              ResultadoDeterminista determinista, String motivo) {
		boolean hayValoracionPrevia = repoEvaluaciones
				.findFirstByCandidaturaIdAndRolAndErrorIsNullOrderByCreadaEnDesc(
						candidatura.getId(), RolEvaluacion.PRINCIPAL)
				.isPresent();
		RolEvaluacion rol = hayValoracionPrevia ? RolEvaluacion.SOMBRA : RolEvaluacion.PRINCIPAL;

		Evaluacion evaluacion = new Evaluacion(candidatura.getId(), rol, PROVEEDOR_SIN_LLM);
		evaluacion.setPuntuacionDeterminista(determinista.puntuacion());
		evaluacion.setAjusteLlm(0);
		evaluacion.setPuntuacionTotal(determinista.puntuacion());
		evaluacion.setDesglose(determinista.desglose());
		evaluacion.setRubricaVersion(rubrica.getVersion());
		evaluacion.setPromptVersion(propiedades.llm().promptVersion());
		evaluacion.setError(motivo);

		// Con valoración previa ni siquiera se persiste: el outbox reintenta cada hora hasta 24
		// veces, y guardar cada intento dejaría dos docenas de filas de fallo en «Qué dice cada
		// modelo». El rastro queda en el panel de trabajo en segundo plano, que es su sitio. Se
		// devuelve igualmente para que el procesador lea el error y reprograme.
		return hayValoracionPrevia ? evaluacion : repoEvaluaciones.save(evaluacion);
	}


	/** Proveedores utilizables y, si no hay, por qué se descartó cada uno. */
	private record Eleccion(List<ProveedorLlm> elegibles, List<String> motivosDescarte) {
	}

	private Evaluacion construir(Candidatura candidatura, RolEvaluacion rol, Rubrica rubrica,
	                             ResultadoDeterminista determinista, ResultadoLlm resultado) {
		Evaluacion evaluacion = new Evaluacion(candidatura.getId(), rol, resultado.proveedor());
		evaluacion.setModelo(resultado.modelo());
		evaluacion.setPuntuacionDeterminista(determinista.puntuacion());
		evaluacion.setDesglose(determinista.desglose());
		evaluacion.setRubricaVersion(rubrica.getVersion());
		evaluacion.setPromptVersion(propiedades.llm().promptVersion());
		evaluacion.setTokensEntrada(resultado.tokensEntrada());
		evaluacion.setTokensSalida(resultado.tokensSalida());
		evaluacion.setLatenciaMs(resultado.latenciaMs());

		if (resultado.correcto()) {
			AjusteEvaluacion ajuste = resultado.ajuste();
			int total = Math.clamp(determinista.puntuacion() + ajuste.ajuste(), 0,
					rubrica.getDefinicion().maximoODefecto());
			evaluacion.setAjusteLlm(ajuste.ajuste());
			evaluacion.setPuntuacionTotal(total);
			evaluacion.setResumen(ajuste.resumen());
			evaluacion.setValoracion(ajuste.valoracion());
			evaluacion.setBanderas(ajuste.banderasRojas());
			evaluacion.setPreguntasPendientes(ajuste.preguntasPendientes());
			evaluacion.setConfianza(ajuste.confianza());

			// El veredicto del nombre viene ahora dentro de la evaluación. Sólo se aplica si la
			// heurística local no había decidido ya: ésa no consulta a nadie y es más fiable para
			// la basura evidente, así que no conviene que el modelo la contradiga.
			if (rol == RolEvaluacion.PRINCIPAL && candidatura.getVerificacionNombre() == null) {
				VerificacionNombre veredicto = ajuste.verificacionNombre();
				candidatura.anotarVerificacionNombre(veredicto.veredicto(), veredicto.motivo());
				if (veredicto.merecePena()) {
					log.info("Nombre marcado como {}: {}", veredicto.veredicto(), veredicto.motivo());
				}
			}
		} else {
			evaluacion.setAjusteLlm(0);
			evaluacion.setPuntuacionTotal(determinista.puntuacion());
			evaluacion.setError(resultado.error());
		}
		if (resultado.textoCrudo() != null) {
			// Sin la respuesta cruda no se puede auditar una puntuación rara meses después.
			Map<String, Object> crudo = new HashMap<>();
			crudo.put("texto", resultado.textoCrudo());
			evaluacion.setRawResponse(crudo);
		}
		return evaluacion;
	}
	/**
	 * Un único proveedor, el que hayas elegido a mano.
	 *
	 * <p>Se salta el orden de la cadena y la marca de sombra —si lo pides expresamente, lo quieres
	 * aunque esté el penúltimo—, pero <b>no</b> la salvaguarda de datos reales: un proveedor no
	 * apto sigue sin ver candidaturas que no sean sintéticas, por mucho que lo elijas en un
	 * desplegable. Esa comprobación protege a terceros, no a ti, así que no es tuya para saltártela.
	 */
	private Eleccion soloEste(Candidatura candidatura, UUID proveedorId) {
		ProveedorLlm proveedor = repoProveedores.findById(proveedorId)
				.orElseThrow(() -> ExcepcionNegocio.noEncontrado("Proveedor inexistente"));

		if (!evaluador.disponible(proveedor)) {
			return new Eleccion(List.of(), List.of(proveedor.getNombre()
					+ ": le falta la url base, el modelo o la clave de API"));
		}
		if (!proveedor.isAptoDatosReales() && !candidatura.isSintetica()) {
			return new Eleccion(List.of(), List.of(proveedor.getNombre()
					+ ": no es apto para datos reales y esta candidatura no es sintética"));
		}
		return new Eleccion(List.of(proveedor), List.of());
	}


	private Eleccion elegibles(Candidatura candidatura, boolean sombra) {
		String periodo = YearMonth.now().toString();
		List<ProveedorLlm> elegibles = new ArrayList<>();
		List<String> motivos = new ArrayList<>();

		for (ProveedorLlm proveedor : repoProveedores.findAllByOrderByOrdenAscNombreAsc()) {
			if (proveedor.isSombra() != sombra) {
				continue;
			}
			if (!proveedor.isActivo()) {
				motivos.add(proveedor.getNombre() + ": desactivado en el panel");
				continue;
			}
			if (!evaluador.disponible(proveedor)) {
				motivos.add(proveedor.getNombre() + ": le falta la url base, el modelo o la clave de API"
						+ " (se configura en Ajustes)");
				continue;
			}
			if (!proveedor.isAptoDatosReales() && !candidatura.isSintetica()) {
				motivos.add(proveedor.getNombre() + ": marcado como no apto para datos reales y esta"
						+ " candidatura no es sintética");
				continue;
			}
			int consumidas = repoConsumo.findByProveedorAndPeriodo(proveedor.getNombre(), periodo)
					.map(ConsumoLlm::getLlamadas).orElse(0);
			if (consumidas >= proveedor.getLimiteMensualLlamadas()) {
				motivos.add(proveedor.getNombre() + ": agotado el tope mensual (" + consumidas + " llamadas)");
				continue;
			}
			elegibles.add(proveedor);
		}
		return new Eleccion(elegibles, motivos);
	}

	private void registrarConsumo(String proveedor, boolean ok) {
		String periodo = YearMonth.now().toString();
		ConsumoLlm consumo = repoConsumo.findByProveedorAndPeriodo(proveedor, periodo)
				.orElseGet(() -> new ConsumoLlm(proveedor, periodo));
		consumo.registrarLlamada(ok);
		repoConsumo.save(consumo);
	}
}
