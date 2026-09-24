package es.agata.renthelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cifra los secretos que se configuran desde el panel.
 *
 * <p>Claves de API, contraseña del SMTP y token del bot acabaron en la base de datos para poder
 * añadir proveedores sin tocar ficheros ni reiniciar. En claro no pueden estar: una copia de
 * seguridad, un volcado para depurar o el propio fichero de H2 se llevarían las claves con él.
 *
 * <p>La clave maestra <b>no</b> se guarda en la base de datos, que es lo único que hace útil el
 * cifrado: quien se lleve la base sin el fichero de entorno no tiene nada que hacer con ella.
 *
 * <p>No hay criptografía propia aquí. {@link Encryptors#delux} es AES-256 en modo GCM con
 * derivación PBKDF2, y cada llamada genera su propio vector de inicialización, así que dos
 * claves iguales no producen el mismo texto cifrado.
 */
@Component
public class Cifrador {

	/**
	 * Marca de lo ya cifrado.
	 *
	 * <p>Permite distinguir un valor cifrado de uno que se metió a mano en la base de datos, y
	 * sobre todo hace idempotente el cifrar dos veces, que es lo que pasaría al guardar un
	 * formulario donde el secreto no se ha tocado.
	 */
	private static final String PREFIJO = "cif:";

	private static final Logger log = LoggerFactory.getLogger(Cifrador.class);

	/** Para avisar una sola vez por secreto ilegible y no llenar el log en cada evaluación. */
	private final Set<String> ilegibles = ConcurrentHashMap.newKeySet();

	private final TextEncryptor cifrador;
	private final boolean configurado;

	public Cifrador(@Value("${renthelper.clave-maestra:}") String claveMaestra,
	                @Value("${renthelper.clave-maestra-sal:}") String sal) {
		this.configurado = claveMaestra != null && !claveMaestra.isBlank();
		// Sin clave maestra se usa una aleatoria de un solo uso: la aplicación arranca y el panel
		// funciona, pero lo cifrado en esta ejecución no se podrá descifrar en la siguiente. Es
		// deliberado: preferible que se note en desarrollo a guardar secretos en claro sin avisar.
		String efectiva = configurado ? claveMaestra : HexFormat.of().formatHex(aleatorio(32));
		String salEfectiva = sal == null || sal.isBlank()
				? "72656e7468656c70" // «renthelp» en hexadecimal: la sal no es secreta, pero sí tiene que ser hex
				: sal;
		this.cifrador = Encryptors.delux(efectiva, salEfectiva);
	}

	public boolean configurado() {
		return configurado;
	}

	/** Devuelve el valor cifrado. Si ya lo estaba, lo deja como está. */
	public String cifrar(String claro) {
		if (claro == null || claro.isBlank()) {
			return null;
		}
		if (claro.startsWith(PREFIJO)) {
			return claro;
		}
		return PREFIJO + cifrador.encrypt(claro);
	}

	/**
	 * Devuelve el valor en claro, o {@code null} si no se puede descifrar.
	 *
	 * <p>No propaga la excepción a propósito. Un secreto ilegible es un problema de
	 * configuración —cambió la clave maestra, o se guardó sin ella y se perdió al reiniciar—, y
	 * si esto reventara, la aplicación entera se quedaría sin arrancar por una clave de API que
	 * ni siquiera es imprescindible: el motor de reglas puntúa igual sin LLM.
	 *
	 * <p>Devolviendo null, el proveedor queda como «sin clave», el panel lo enseña en rojo y se
	 * arregla escribiéndola otra vez. El aviso sale una sola vez por valor para no llenar el log
	 * en cada evaluación.
	 *
	 * <p>Un valor sin el prefijo se devuelve tal cual: así sigue funcionando lo que se hubiese
	 * metido a mano en la base de datos antes de que existiera esto.
	 */
	public String descifrar(String guardado) {
		if (guardado == null || guardado.isBlank()) {
			return null;
		}
		if (!guardado.startsWith(PREFIJO)) {
			return guardado;
		}
		try {
			return cifrador.decrypt(guardado.substring(PREFIJO.length()));
		} catch (RuntimeException e) {
			if (ilegibles.add(guardado)) {
				log.error("Hay un secreto que no se puede descifrar con la clave maestra actual."
						+ " Si acabas de poner RENTHELPER_CLAVE_MAESTRA, lo que se guardó antes se cifró"
						+ " con una clave de un solo uso y hay que volver a escribirlo en Ajustes.");
			}
			return null;
		}
	}

	/** Para la interfaz: si hay secreto y cuáles son sus últimos caracteres, nunca el valor. */
	public String pista(String guardado) {
		String claro = descifrar(guardado);
		if (claro == null || claro.isBlank()) {
			return null;
		}
		return claro.length() <= 4 ? "····" : "····" + claro.substring(claro.length() - 4);
	}

	private static byte[] aleatorio(int bytes) {
		byte[] datos = new byte[bytes];
		new SecureRandom().nextBytes(datos);
		return datos;
	}
}
