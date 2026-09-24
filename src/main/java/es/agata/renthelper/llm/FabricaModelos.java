package es.agata.renthelper.llm;

import es.agata.renthelper.config.Cifrador;
import es.agata.renthelper.dominio.ProveedorLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Construye un {@link ChatModel} a partir de una fila de la base de datos.
 *
 * <p>Antes los modelos eran beans que Spring autoconfiguraba al arrancar desde
 * {@code application.yml}, y eso hacía imposible lo que se quería: añadir proveedores desde el
 * panel. Aquí se construyen a demanda, así que una fila nueva entra en la cadena sin reiniciar.
 *
 * <p>Todos se construyen con el cliente de OpenAI. No es un atajo: Groq, OpenRouter, Together,
 * Ollama y también Gemini publican un endpoint compatible, de modo que url base + clave + nombre
 * de modelo describe a cualquiera de ellos. Un adaptador por proveedor habría multiplicado el
 * código por cada API nueva sin dar nada a cambio.
 *
 * <p>Se cachean por {@code actualizadoEn}: construir un cliente HTTP en cada evaluación sería
 * tirar conexiones, y usar sólo el id dejaría en pie el modelo viejo tras editarlo en el panel,
 * que es justo el momento en que quieres ver el cambio.
 */
@Component
public class FabricaModelos {

	private static final Logger log = LoggerFactory.getLogger(FabricaModelos.class);

	private final Cifrador cifrador;
	private final Double temperatura;
	private final Integer maxTokens;
	private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

	public FabricaModelos(Cifrador cifrador,
	                      @Value("${renthelper.llm.temperatura:0.2}") Double temperatura,
	                      @Value("${renthelper.llm.max-tokens-salida:1200}") Integer maxTokens) {
		this.cifrador = cifrador;
		this.temperatura = temperatura;
		this.maxTokens = maxTokens;
	}

	/** Devuelve null si al proveedor le falta algo para poder llamar. */
	public ChatModel modelo(ProveedorLlm proveedor) {
		if (proveedor == null || proveedor.getUrlBase() == null || proveedor.getUrlBase().isBlank()
				|| proveedor.getModelo() == null || proveedor.getModelo().isBlank()) {
			return null;
		}
		String clave = cifrador.descifrar(proveedor.getApiKeyCifrada());
		if (clave == null || clave.isBlank()) {
			return null;
		}

		String llaveCache = proveedor.getId() + "@" + proveedor.getActualizadoEn().toEpochMilli();
		return cache.computeIfAbsent(llaveCache, k -> construir(proveedor, clave));
	}

	private ChatModel construir(ProveedorLlm proveedor, String clave) {
		log.info("Construyendo cliente para {} · {} en {}", proveedor.getNombre(),
				proveedor.getModelo(), proveedor.getUrlBase());

		// `maxCompletionTokens` y no `maxTokens`: los modelos de razonamiento rechazan el segundo,
		// y su cadena de pensamiento cuenta como salida aunque no se vea. Poner el techo aquí
		// evita que una respuesta desbocada se lleve la cuota de golpe.
		return OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder()
						.baseUrl(proveedor.getUrlBase())
						.apiKey(clave)
						.model(proveedor.getModelo())
						.temperature(temperatura)
						.maxCompletionTokens(maxTokens)
						.build())
				.build();
	}

	/** Se llama al guardar desde el panel: la caché vieja deja de tener sentido. */
	public void olvidar() {
		cache.clear();
	}
}
