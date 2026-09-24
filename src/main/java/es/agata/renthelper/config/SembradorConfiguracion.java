package es.agata.renthelper.config;

import es.agata.renthelper.dominio.Ajustes;
import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.repositorio.RepositorioAjustes;
import es.agata.renthelper.repositorio.RepositorioProveedorLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traslada a la base de datos la configuración que antes vivía en el fichero de entorno.
 *
 * <p>Se ejecuta una sola vez: si ya hay proveedores, no toca nada. La gracia es que al actualizar
 * no se pierde lo que ya funcionaba —la cadena de modelos, el SMTP y el bot siguen en pie sin que
 * haya que reescribirlos a mano en el panel—, y a partir de ahí las variables del {@code .env}
 * sobran.
 *
 * <p>Siembra la cadena aunque no haya claves: así el panel arranca con las filas hechas y sólo
 * hay que pegar la clave en cada una, que es bastante menos trabajo que crearlas de cero.
 */
@Configuration
public class SembradorConfiguracion {

	private static final Logger log = LoggerFactory.getLogger(SembradorConfiguracion.class);

	/** Gemini por su endpoint compatible con OpenAI, que es el que entiende el cliente. */
	private static final String URL_GEMINI =
			"https://generativelanguage.googleapis.com/v1beta/openai";
	private static final String URL_GROQ = "https://api.groq.com/openai/v1";

	/**
	 * Nota de partida sobre la bonificación, en IRPF y para el arrendador.
	 *
	 * <p>Es un texto del propietario, no una afirmación de la aplicación: se muestra tal cual y
	 * nadie lo interpreta. Los porcentajes y el concepto de «zona tensionada» cambian, así que
	 * está aquí sólo como punto de partida y se edita en Ajustes.
	 */
	private static final String NOTA_BONIFICACION = """
			Reducción del rendimiento neto del alquiler cuando el ARRENDATARIO tiene entre 18 y 35 \
			años. General del 60%; hasta el 70% si la vivienda está en zona tensionada y se alquila \
			por primera vez a un joven de ese tramo; hasta el 90% si además se rebaja la renta más \
			de un 5% en zona tensionada. Revisar los requisitos de zona tensionada antes de contar \
			con ello. La edad del propietario no influye. Las deducciones autonómicas por edad \
			(Madrid y otras) son del inquilino, no del arrendador.""";

	@Bean
	@Order(2)
	@Transactional
	public ApplicationRunner sembrarConfiguracion(RepositorioProveedorLlm repoProveedores,
	                                              RepositorioAjustes repoAjustes,
	                                              Cifrador cifrador,
	                                              Environment entorno) {
		return args -> {
			if (repoProveedores.count() == 0) {
				sembrarCadena(repoProveedores, cifrador, entorno);
			}
			if (repoAjustes.findFirstBy().isEmpty()) {
				sembrarAjustes(repoAjustes, cifrador, entorno);
			}
			if (!cifrador.configurado()) {
				log.warn("Sin RENTHELPER_CLAVE_MAESTRA: los secretos que guardes desde el panel NO se"
						+ " podrán descifrar tras reiniciar. Ponla en el .env antes de configurar nada.");
			}
		};
	}

	private void sembrarCadena(RepositorioProveedorLlm repo, Cifrador cifrador, Environment entorno) {
		String claveGemini = entorno.getProperty("GEMINI_API_KEY", "");
		String claveGroq = entorno.getProperty("GROQ_API_KEY", "");

		// Los `flash-lite` delante porque dan 500 peticiones al día frente a las 20 de un `flash`,
		// y Groq detrás porque su cupo es por hora. Ninguno apto para datos reales: sus tiers
		// gratuitos se usan para entrenar.
		crear(repo, cifrador, "GEMINI_35_LITE", URL_GEMINI, "gemini-3.5-flash-lite", claveGemini, 0, true);
		crear(repo, cifrador, "GEMINI_31_LITE", URL_GEMINI, "gemini-3.1-flash-lite", claveGemini, 1, true);
		crear(repo, cifrador, "GROQ", URL_GROQ, "openai/gpt-oss-120b", claveGroq, 2, true);

		// Los de abajo se crean DESACTIVADOS. Son mejores de lo que sugiere su posición, pero su
		// cupo gratuito es de 20 peticiones al día: en la cadena automática se agotarían con las
		// primeras candidaturas y a partir de ahí sólo gastarían un intento fallido por evaluación.
		// Desactivados no estorban, y el selector de la ficha los ofrece igual para pedirles una
		// segunda opinión sobre alguien concreto, que es donde sí valen la pena.
		crear(repo, cifrador, "GEMINI_36_FLASH", URL_GEMINI, "gemini-3.6-flash", claveGemini, 3, false);
		crear(repo, cifrador, "GEMINI_37_FLASH", URL_GEMINI, "gemini-3.7-flash", claveGemini, 4, false);
		crear(repo, cifrador, "GEMINI_38_FLASH", URL_GEMINI, "gemini-3.8-flash", claveGemini, 5, false);

		log.info("Cadena de modelos sembrada: 3 activos y 3 disponibles para pedir segunda opinión."
				+ " Se configura en el panel, en Ajustes.");
	}

	private void crear(RepositorioProveedorLlm repo, Cifrador cifrador, String nombre, String url,
	                   String modelo, String clave, int orden, boolean enLaCadena) {
		ProveedorLlm proveedor = new ProveedorLlm(nombre, url, modelo);
		proveedor.setOrden(orden);
		proveedor.setAptoDatosReales(false);
		proveedor.setLimiteMensualLlamadas(15000);
		if (clave != null && !clave.isBlank()) {
			proveedor.setApiKeyCifrada(cifrador.cifrar(clave.trim()));
		}
		// Sin clave tampoco se activa: activo y sin poder llamar sólo serviría para que cada
		// evaluación gastase un intento condenado al fallo antes de pasar al siguiente.
		proveedor.setActivo(enLaCadena && clave != null && !clave.isBlank());
		repo.save(proveedor);
	}

	private void sembrarAjustes(RepositorioAjustes repo, Cifrador cifrador, Environment entorno) {
		Ajustes ajustes = new Ajustes();
		ajustes.setSmtpHost(entorno.getProperty("SPRING_MAIL_HOST"));
		ajustes.setSmtpPuerto(Integer.parseInt(entorno.getProperty("SPRING_MAIL_PORT", "587")));
		ajustes.setSmtpUsuario(entorno.getProperty("SPRING_MAIL_USERNAME"));
		ajustes.setCorreoRemitente(entorno.getProperty("CORREO_REMITENTE"));

		String password = entorno.getProperty("SPRING_MAIL_PASSWORD", "");
		if (!password.isBlank()) {
			ajustes.setSmtpPasswordCifrada(cifrador.cifrar(password));
		}
		String token = entorno.getProperty("TELEGRAM_BOT_TOKEN", "");
		if (!token.isBlank()) {
			ajustes.setTelegramTokenCifrado(cifrador.cifrar(token));
		}
		ajustes.setTelegramChatId(entorno.getProperty("TELEGRAM_CHAT_ID"));

		// Valores de partida, para que el apartado no arranque vacío. Son TUYOS: la aplicación no
		// sabe de fiscalidad y no comprueba nada de esto, sólo si las edades caen en el tramo.
		// Reviselos cuando cambie la norma; se edita en Ajustes sin desplegar.
		ajustes.setBonificacionActiva(true);
		ajustes.setBonificacionEdadMin(18);
		ajustes.setBonificacionEdadMax(35);
		ajustes.setBonificacionNota(NOTA_BONIFICACION);

		repo.save(ajustes);
		log.info("Ajustes sembrados · correo: {} · telegram: {}",
				ajustes.correoConfigurado() ? "importado del entorno" : "sin configurar",
				ajustes.telegramConfigurado() ? "importado del entorno" : "sin configurar");
	}
}
