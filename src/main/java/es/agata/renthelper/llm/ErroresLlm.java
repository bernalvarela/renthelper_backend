package es.agata.renthelper.llm;

import java.util.Locale;

/**
 * Distingue los fallos que se arreglan solos de los que no.
 *
 * <p>Importa porque la reacción es opuesta. Ante una cuota agotada o un pico de demanda hay que
 * esperar y reintentar: la candidatura acabará evaluada. Ante una clave inválida o un modelo
 * retirado, reintentar es quemar tiempo; lo que toca es quedarse con la puntuación de reglas y
 * que el log lo diga claro.
 */
public final class ErroresLlm {

	private static final String[] SENALES_TRANSITORIAS = {
			"429", "quota", "resource_exhausted", "rate limit", "rate-limit",
			"503", "unavailable", "high demand", "overloaded",
			"500", "internal error", "timeout", "timed out", "deadline"
	};

	private ErroresLlm() {
	}

	public static boolean esTransitorio(String mensaje) {
		if (mensaje == null || mensaje.isBlank()) {
			return false;
		}
		String normalizado = mensaje.toLowerCase(Locale.ROOT);
		for (String senal : SENALES_TRANSITORIAS) {
			if (normalizado.contains(senal)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Aplana la cadena de causas en un solo mensaje.
	 *
	 * <p>Sin esto sólo se veía «Failed to generate content», que es la excepción envoltorio de
	 * Spring AI y no dice nada: el 429 con la cuota y el tiempo de espera están dos niveles por
	 * debajo, en la excepción del cliente de Google.
	 */
	public static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		Throwable actual = error;
		int profundidad = 0;

		while (actual != null && profundidad < 5) {
			if (!sb.isEmpty()) {
				sb.append(" · ");
			}
			sb.append(actual.getClass().getSimpleName());
			if (actual.getMessage() != null) {
				sb.append(": ").append(primeraLinea(actual.getMessage()));
			}
			actual = actual.getCause();
			profundidad++;
		}
		return sb.toString();
	}

	/** Los errores de Google traen varios bloques JSON detrás; con la primera línea basta. */
	private static String primeraLinea(String mensaje) {
		int salto = mensaje.indexOf('\n');
		String linea = salto > 0 ? mensaje.substring(0, salto) : mensaje;
		return linea.length() > 300 ? linea.substring(0, 300) + "…" : linea;
	}
}
