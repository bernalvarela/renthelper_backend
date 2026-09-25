package es.agata.renthelper.llm;

import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.config.PropiedadesRentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Implementación del puerto sobre Spring AI.
 *
 * <p>Los proveedores salen de la base de datos y se construyen a demanda con {@link
 * FabricaModelos}: una fila nueva en el panel entra en la cadena sin reiniciar y sin tocar este
 * código. Si le falta la url, el modelo o la clave, {@link #disponible} devuelve false y el
 * orquestador pasa al siguiente.
 *
 * <p>La salida estructurada no es igual de fiable en todos los proveedores: Anthropic y OpenAI
 * tienen structured outputs nativos con garantía de esquema, mientras que en otros Spring AI hace
 * prompt + parseo, que falla más. De ahí el reintento con instrucción de reparación.
 */
@Component
public class SpringAiEvaluadorLlm implements EvaluadorLlm {

	private static final Logger log = LoggerFactory.getLogger(SpringAiEvaluadorLlm.class);

	private final FabricaModelos fabrica;
	private final ConstructorPrompt constructorPrompt;
	private final int ajusteMaximoGlobal;
	private final ProgresoEvaluaciones progreso;

	public SpringAiEvaluadorLlm(FabricaModelos fabrica, ConstructorPrompt constructorPrompt,
	                            PropiedadesRentHelper propiedades, ProgresoEvaluaciones progreso) {
		this.progreso = progreso;
		this.fabrica = fabrica;
		this.constructorPrompt = constructorPrompt;
		this.ajusteMaximoGlobal = propiedades.llm().ajusteMaximo();
	}

	@Override
	public boolean disponible(ProveedorLlm proveedor) {
		return modelo(proveedor) != null;
	}

	private ChatModel modelo(ProveedorLlm proveedor) {
		return fabrica.modelo(proveedor);
	}

	/**
	 * Petición sobre el modelo ya construido para ese proveedor.
	 *
	 * <p>No hace falta pasarle opciones: {@link FabricaModelos} le ha puesto su url base, su
	 * clave y su nombre de modelo al construirlo, así que dos filas del mismo proveedor con
	 * modelos distintos son dos clientes distintos y no se pisan.
	 */
	private ChatClient.ChatClientRequestSpec peticion(ChatModel modelo, ProveedorLlm proveedor) {
		return ChatClient.create(modelo).prompt();
	}
	@Override
	public ResultadoLlm evaluar(SolicitudEvaluacion solicitud, ProveedorLlm proveedor) {
		ChatModel modelo = modelo(proveedor);
		long inicio = System.currentTimeMillis();
		if (modelo == null) {
			return ResultadoLlm.fallo(proveedor.getNombre(), proveedor.getModelo(), 0, "Proveedor no disponible");
		}

		int ajusteMaximo = solicitud.rubrica() != null && solicitud.rubrica().ajusteLlm() != null
				? solicitud.rubrica().ajusteLlm().maximoODefecto(ajusteMaximoGlobal)
				: ajusteMaximoGlobal;

		BeanOutputConverter<AjusteEvaluacion> conversor = new BeanOutputConverter<>(AjusteEvaluacion.class);
		String sistema = constructorPrompt.sistema(solicitud, ajusteMaximo);
		String usuario = constructorPrompt.usuario(solicitud) + "\n\n" + conversor.getFormat();

		String texto = null;
		try {
			ChatResponse respuesta = peticion(modelo, proveedor)
					.system(sistema).user(usuario).call().chatResponse();
			texto = textoDe(respuesta);
			progreso.paso("Leyendo la respuesta de " + OrquestadorEvaluacion.describir(proveedor));

			AjusteEvaluacion ajuste = convertirConReintento(modelo, proveedor, conversor, sistema, usuario, texto);
			int latencia = (int) (System.currentTimeMillis() - inicio);
			Integer entrada = tokens(respuesta, true);
			Integer salida = tokens(respuesta, false);

			log.info("{} respondió en {} ms · ajuste {}{} · confianza {} · tokens {}/{}",
					proveedor.getNombre(), latencia, ajuste.ajuste() >= 0 ? "+" : "", ajuste.ajuste(),
					ajuste.confianza(), entrada == null ? "?" : entrada, salida == null ? "?" : salida);

			return new ResultadoLlm(ajuste.acotado(ajusteMaximo), proveedor.getNombre(), proveedor.getModelo(),
					entrada, salida, latencia, texto, null);

		} catch (RuntimeException | LinkageError e) {
			// LinkageError también: en la imagen nativa una reflexión sin registrar lanza
			// MissingReflectionRegistrationError, que es un Error y no una excepción. Sin
			// capturarlo aquí atravesaba la transacción del outbox, la revertía entera y el
			// mensaje seguía en cola sin contar el intento: una llamada de pago al proveedor
			// cada quince segundos, sin fin. Aquí se queda en una evaluación fallida más.
			int latencia = (int) (System.currentTimeMillis() - inicio);
			String detalle = ErroresLlm.mensajeCompleto(e);

			if (ErroresLlm.esTransitorio(detalle)) {
				// Cuota agotada o proveedor saturado. Es esperable y se reintenta solo, así que
				// no merece una traza de sesenta líneas cada vez.
				log.warn("{} no disponible ahora mismo tras {} ms: {}", proveedor.getNombre(), latencia,
						detalle);
			} else {
				log.error("Fallo evaluando con {} (modelo {}) tras {} ms", proveedor.getNombre(),
						proveedor.getModelo(), latencia, e);
			}
			return new ResultadoLlm(null, proveedor.getNombre(), proveedor.getModelo(), null, null, latencia,
					texto, detalle);
		}
	}

	/**
	 * Compara a los finalistas en una sola llamada.
	 *
	 * <p>El techo de salida se sube sólo para esta llamada, con las opciones de la petición: el
	 * modelo que construye {@link FabricaModelos} lleva el de la evaluación individual, que no
	 * da para ocho personas. Un reintento de reparación si el JSON no se puede leer, como en la
	 * evaluación.
	 */
	@Override
	public ResultadoInforme comparar(String sistema, String usuario, ProveedorLlm proveedor, int maxTokensSalida) {
		ChatModel modelo = modelo(proveedor);
		if (modelo == null) {
			return new ResultadoInforme(null, proveedor.getNombre(), proveedor.getModelo(), null, null, 0,
					"Proveedor no disponible");
		}
		BeanOutputConverter<InformeComparativoLlm> conversor = new BeanOutputConverter<>(InformeComparativoLlm.class);
		String conFormato = usuario + "\n\n" + conversor.getFormat();
		// Spring AI 2 toma el builder, no las opciones construidas; se mezcla con las del modelo.
		var techo = OpenAiChatOptions.builder().maxCompletionTokens(maxTokensSalida);

		long inicio = System.currentTimeMillis();
		try {
			ChatResponse respuesta = peticion(modelo, proveedor).options(techo)
					.system(sistema).user(conFormato).call().chatResponse();
			String texto = textoDe(respuesta);
			InformeComparativoLlm informe;
			try {
				informe = conversor.convert(limpiar(texto));
			} catch (RuntimeException e) {
				log.warn("El informe comparativo de {} no era JSON convertible, reintento: {}",
						proveedor.getNombre(), e.getMessage());
				String segundo = textoDe(peticion(modelo, proveedor).options(techo).system(sistema)
						.user(conFormato + "\n\nTu respuesta anterior no era JSON válido. Devuelve SÓLO el"
								+ " objeto JSON, sin texto alrededor ni bloques de código.")
						.call().chatResponse());
				informe = conversor.convert(limpiar(segundo));
			}
			int latencia = (int) (System.currentTimeMillis() - inicio);
			log.info("Informe comparativo de {} en {} ms · tokens {}/{}", proveedor.getNombre(), latencia,
					tokens(respuesta, true), tokens(respuesta, false));
			return new ResultadoInforme(informe, proveedor.getNombre(), proveedor.getModelo(),
					tokens(respuesta, true), tokens(respuesta, false), latencia,
					informe == null ? "El proveedor no devolvió un JSON convertible" : null);
		} catch (RuntimeException | LinkageError e) {
			// LinkageError por lo mismo que en evaluar(): en la imagen nativa, una reflexión sin
			// registrar es un Error y se colaría hasta el usuario como un 500 sin explicación.
			int latencia = (int) (System.currentTimeMillis() - inicio);
			String detalle = ErroresLlm.mensajeCompleto(e);
			log.error("Fallo comparando finalistas con {} (modelo {}) tras {} ms", proveedor.getNombre(),
					proveedor.getModelo(), latencia, e);
			return new ResultadoInforme(null, proveedor.getNombre(), proveedor.getModelo(), null, null,
					latencia, detalle);
		}
	}

	/**
	 * Llamada mínima para comprobar que el proveedor contesta.
	 *
	 * <p>Existe porque el modo de fallo habitual no es un error al guardar sino el silencio: la
	 * configuración parece correcta, y la clave equivocada o la url sin {@code /v1} sólo se
	 * descubren horas después, cuando las candidaturas aparecen sin nota. Mejor saberlo en el
	 * momento de configurarlo.
	 *
	 * <p>El prompt es de una palabra a propósito: comprueba credenciales, url y nombre de modelo
	 * gastando lo mínimo de una cuota que suele ser justita.
	 */
	@Override
	public ResultadoPrueba probar(ProveedorLlm proveedor) {
		ChatModel modelo = modelo(proveedor);
		if (modelo == null) {
			return new ResultadoPrueba(false, "Falta la url base, el modelo o la clave de API", 0);
		}
		long inicio = System.currentTimeMillis();
		try {
			String texto = textoDe(ChatClient.create(modelo).prompt()
					.user("Responde únicamente con la palabra OK.")
					.call().chatResponse());
			int latencia = (int) (System.currentTimeMillis() - inicio);
			log.info("Prueba de {} ({}) correcta en {} ms", proveedor.getNombre(),
					proveedor.getModelo(), latencia);
			return new ResultadoPrueba(true, texto == null ? "" : texto.strip(), latencia);
		} catch (RuntimeException | LinkageError e) {
			int latencia = (int) (System.currentTimeMillis() - inicio);
			String detalle = ErroresLlm.mensajeCompleto(e);
			log.warn("Prueba de {} ({}) fallida tras {} ms: {}", proveedor.getNombre(),
					proveedor.getModelo(), latencia, detalle);
			return new ResultadoPrueba(false, detalle, latencia);
		}
	}

	/**
	 * Un reintento con la respuesta anterior delante. Es barato y recupera el caso habitual:
	 * el modelo ha contestado bien pero envuelto en prosa o en un bloque de código.
	 */
	private AjusteEvaluacion convertirConReintento(ChatModel modelo,
	                                               ProveedorLlm proveedor,
	                                               BeanOutputConverter<AjusteEvaluacion> conversor,
	                                               String sistema, String usuario, String texto) {
		String fallo = null;
		try {
			AjusteEvaluacion ajuste = conversor.convert(limpiar(texto));
			if (ajuste != null) {
				return ajuste;
			}
		} catch (RuntimeException e) {
			fallo = e.getMessage();
		}

		// Dos averías distintas con dos arreglos distintos.
		//
		// Si la respuesta se cortó a mitad de una cadena, es que chocó con el techo de tokens de
		// salida. Repetir «devuelve sólo JSON» no arregla nada: volvería a cortarse por el mismo
		// sitio y habría gastado dos llamadas para nada. Lo que puede funcionar es pedir lo mismo
		// más breve.
		//
		// Si es otra cosa —lo habitual, JSON envuelto en prosa o en un bloque de código—, sirve
		// el reintento de siempre.
		boolean cortada = truncada(fallo);
		log.warn(cortada
				? "La respuesta se cortó por el techo de tokens de salida, reintento pidiéndola más"
						+ " breve. Si se repite, sube renthelper.llm.max-tokens-salida: {}"
				: "La respuesta no era JSON convertible, reintento con instrucción de reparación: {}",
				fallo);

		String reparacion = cortada
				? usuario + "\n\nTu respuesta anterior se cortó antes de terminar. Devuelve el MISMO"
						+ " JSON pero más breve: una frase por campo de texto y como mucho dos"
						+ " preguntas pendientes."
				: usuario + "\n\nTu respuesta anterior no era JSON válido:\n" + texto
						+ "\n\nDevuelve SÓLO el objeto JSON, sin texto alrededor ni bloques de código.";

		progreso.paso(cortada
				? "La respuesta de " + OrquestadorEvaluacion.describir(proveedor)
						+ " se cortó; pidiéndola más breve"
				: "La respuesta de " + OrquestadorEvaluacion.describir(proveedor)
						+ " no era JSON válido; segundo intento");
		String segundo = textoDe(peticion(modelo, proveedor)
				.system(sistema).user(reparacion).call().chatResponse());
		AjusteEvaluacion ajuste = conversor.convert(limpiar(segundo));
		if (ajuste == null) {
			throw new IllegalStateException("El proveedor no devolvió un JSON convertible");
		}
		return ajuste;
	}

	/**
	 * Distingue «se cortó» de «venía mal escrito».
	 *
	 * <p>Jackson lo dice con un final de entrada inesperado: el JSON iba bien hasta que dejó de
	 * llegar. Casi siempre significa que la respuesta topó con el techo de tokens, no que el
	 * modelo escriba mal.
	 */
	private static boolean truncada(String mensaje) {
		if (mensaje == null) {
			return false;
		}
		String m = mensaje.toLowerCase();
		return m.contains("end-of-input") || m.contains("end of input");
	}

	private static String textoDe(ChatResponse respuesta) {
		if (respuesta == null || respuesta.getResult() == null
				|| respuesta.getResult().getOutput() == null) {
			return null;
		}
		return respuesta.getResult().getOutput().getText();
	}

	/**
	 * Quita lo que algunos modelos ponen alrededor del JSON.
	 *
	 * <p>Sobre todo el bloque de código con ```json, que es el envoltorio más habitual y el que
	 * hace fallar al conversor por una tontería.
	 */
	private static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.strip();
		if (limpio.startsWith("```")) {
			int primerSalto = limpio.indexOf(10);
			int ultimaValla = limpio.lastIndexOf("```");
			if (primerSalto > 0 && ultimaValla > primerSalto) {
				limpio = limpio.substring(primerSalto + 1, ultimaValla).strip();
			}
		}
		return limpio;
	}

	/** Los proveedores no siempre informan del consumo; sin dato, null y no cero. */
	private static Integer tokens(ChatResponse respuesta, boolean entrada) {
		if (respuesta == null || respuesta.getMetadata() == null
				|| respuesta.getMetadata().getUsage() == null) {
			return null;
		}
		var uso = respuesta.getMetadata().getUsage();
		return entrada ? uso.getPromptTokens() : uso.getCompletionTokens();
	}
}
