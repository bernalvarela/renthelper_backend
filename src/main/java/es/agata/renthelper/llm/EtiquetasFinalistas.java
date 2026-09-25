package es.agata.renthelper.llm;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cambia las etiquetas de los finalistas por sus nombres en el texto del informe comparativo.
 *
 * <p>Al modelo le llegan sin nombre, con etiqueta; lo que escribe («C2 tiene menos margen que
 * C4») no le dice nada a quien lo lee. Esto lo devuelve con los nombres, en el servidor, antes de
 * que llegue al panel.
 *
 * <p>Las etiquetas son C1, C2... y no letras sueltas porque no se confunden con ninguna palabra:
 * «A» y «E» también lo son en castellano («A partir de...»), y cambiarlas a ciegas estropeaba el
 * texto.
 */
final class EtiquetasFinalistas {

	private EtiquetasFinalistas() {
	}

	static String etiqueta(int posicion) {
		return "C" + (posicion + 1);
	}

	/**
	 * @param nombres etiqueta → nombre. Se sustituyen las más largas primero, para que «C1» no se
	 *                coma el principio de «C10».
	 */
	static String sustituir(String texto, Map<String, String> nombres) {
		if (texto == null || texto.isBlank() || nombres.isEmpty()) {
			return texto;
		}
		String resultado = texto;
		for (String etiqueta : nombres.keySet().stream()
				.sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
			// Sólo la etiqueta entera: ni «C12» cuando se busca «C1», ni «C1» dentro de otra palabra.
			Pattern patron = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(etiqueta) + "(?![\\p{L}\\p{N}])");
			resultado = patron.matcher(resultado).replaceAll(Matcher.quoteReplacement(nombres.get(etiqueta)));
		}
		return resultado;
	}
}
