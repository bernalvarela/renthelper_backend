package es.agata.renthelper.llm;

import java.util.regex.Pattern;

/**
 * Filtro previo sin LLM para la basura evidente.
 *
 * <p>«asdfgh», «xxx», «aaa» o «prueba 1» no necesitan un modelo: se detectan con cuatro reglas y
 * así ni se paga la llamada ni sale el texto del servidor.
 *
 * <p>Lo que NO hace, a propósito: juzgar si un nombre «suena» real. Eso es precisamente donde una
 * heurística escrita desde aquí discriminaría —cualquier regla sobre qué letras o cuántas
 * palabras tiene un nombre marca como raro medio mundo—. Para eso está la llamada al modelo, con
 * instrucciones explícitas de que lo infrecuente no es sospechoso.
 */
public final class HeuristicaNombre {

	private static final Pattern SOLO_LETRAS_Y_ESPACIOS =
			Pattern.compile("^[\\p{L}\\p{M}'·.\\-\\s]+$", Pattern.UNICODE_CASE);
	private static final Pattern TRES_REPETIDAS = Pattern.compile("(.)\\1{2,}");
	private static final Pattern RELLENO_EVIDENTE = Pattern.compile(
			"^(test|testing|prueba|pruebas|asdf\\w*|qwerty\\w*|xxx+|aaa+|nn|na|n/a|sin nombre|nombre)$",
			Pattern.CASE_INSENSITIVE);

	private HeuristicaNombre() {
	}

	/** Devuelve un veredicto sólo si es basura evidente; {@code null} si hay que preguntar al modelo. */
	public static VerificacionNombre revisar(String nombre) {
		if (nombre == null || nombre.isBlank()) {
			return VerificacionNombre.ficticio("Sin nombre");
		}
		String limpio = nombre.trim();

		if (limpio.length() < 3) {
			return VerificacionNombre.ficticio("Demasiado corto para ser un nombre");
		}
		if (RELLENO_EVIDENTE.matcher(limpio).matches()) {
			return VerificacionNombre.ficticio("Texto de relleno");
		}
		if (!SOLO_LETRAS_Y_ESPACIOS.matcher(limpio).matches()) {
			return VerificacionNombre.ficticio("Contiene cifras o símbolos que no son de un nombre");
		}
		if (TRES_REPETIDAS.matcher(limpio).find()) {
			return VerificacionNombre.ficticio("Letras repetidas, parece tecleado al azar");
		}
		return null;
	}
}
