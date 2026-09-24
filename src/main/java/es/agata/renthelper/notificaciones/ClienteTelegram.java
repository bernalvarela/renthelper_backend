package es.agata.renthelper.notificaciones;

import es.agata.renthelper.config.Cifrador;
import es.agata.renthelper.dominio.Ajustes;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Cliente mínimo del Bot API. Sin librería de terceros: es una llamada HTTP.
 *
 * <p>Usa {@code parse_mode=HTML} en vez de MarkdownV2 porque escapar MarkdownV2 es una fuente
 * inagotable de mensajes rotos (guiones, puntos y paréntesis son todos especiales).
 *
 * <p>Token y chat salen de la base de datos, no del fichero de entorno: se configuran desde el
 * panel y el cambio surte efecto en el envío siguiente, sin reiniciar. Sin configurar, escribe
 * en el log en vez de enviar, para poder desarrollar sin dar de alta un bot.
 */
@Component
public class ClienteTelegram {

	private static final Logger log = LoggerFactory.getLogger(ClienteTelegram.class);

	private final RestClient cliente = RestClient.create();
	private final RepositorioAjustes repoAjustes;
	private final Cifrador cifrador;

	public ClienteTelegram(RepositorioAjustes repoAjustes, Cifrador cifrador) {
		this.repoAjustes = repoAjustes;
		this.cifrador = cifrador;
	}

	@Transactional(readOnly = true)
	public void enviar(String textoHtml) {
		Optional<Ajustes> ajustes = repoAjustes.findFirstBy().filter(Ajustes::telegramConfigurado);
		if (ajustes.isEmpty()) {
			log.info("[telegram no configurado] {}", textoHtml);
			return;
		}
		Ajustes config = ajustes.get();
		cliente.post()
				.uri("https://api.telegram.org/bot{token}/sendMessage",
						cifrador.descifrar(config.getTelegramTokenCifrado()))
				.body(Map.of(
						"chat_id", config.getTelegramChatId(),
						"text", textoHtml,
						"parse_mode", "HTML",
						"disable_web_page_preview", true))
				.retrieve()
				.toBodilessEntity();
	}

	/** Escapa lo que exige el parse_mode HTML de Telegram. Nada más es especial. */
	public static String escapar(String texto) {
		if (texto == null) {
			return "";
		}
		return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
