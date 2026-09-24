package es.agata.renthelper.formularios;

import java.util.regex.Pattern;

/**
 * Normaliza a E.164 asumiendo España cuando no hay prefijo. Sirve para deduplicar: con 150
 * contactos habrá gente que abra el enlace tres veces y escriba el número de formas distintas.
 *
 * <p>Deliberadamente simple: no valida operadoras ni portabilidades, sólo unifica la forma.
 */
public final class NormalizadorTelefono {

	private static final Pattern MOVIL_O_FIJO_ES = Pattern.compile("^[6-9]\\d{8}$");
	private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

	private NormalizadorTelefono() {
	}

	/** Devuelve el número en E.164, o {@code null} si no tiene forma de teléfono válido. */
	public static String normalizar(String bruto) {
		if (bruto == null || bruto.isBlank()) {
			return null;
		}
		String limpio = bruto.replaceAll("[\\s.\\-()/]", "");
		if (limpio.startsWith("00")) {
			limpio = "+" + limpio.substring(2);
		}
		if (MOVIL_O_FIJO_ES.matcher(limpio).matches()) {
			return "+34" + limpio;
		}
		if (limpio.startsWith("34") && MOVIL_O_FIJO_ES.matcher(limpio.substring(2)).matches()) {
			return "+" + limpio;
		}
		return E164.matcher(limpio).matches() ? limpio : null;
	}

	public static boolean valido(String bruto) {
		return normalizar(bruto) != null;
	}
}
