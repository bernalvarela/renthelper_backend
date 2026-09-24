package es.agata.renthelper.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tapa teléfonos y correos escritos dentro del texto libre.
 *
 * <p>Los campos de contacto ya se excluyen por tipo en {@link ConstructorPrompt}, pero ese filtro
 * mira la <b>declaración</b> del campo, no su contenido. En «¿Algo que debamos saber para cuadrar
 * la visita?» es bastante probable que alguien escriba «llámame al 600 12 34 56», y eso viajaba
 * al proveedor con el resto de la respuesta.
 *
 * <p>No cambia nada de lo que el modelo necesita para juzgar: sigue leyendo que la persona
 * prefiere que la llamen y a qué horas; sólo deja de ver el número.
 */
final class Anonimizador {

	/** Suficiente para lo habitual; no pretende cubrir el RFC entero. */
	private static final Pattern CORREO =
			Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}");

	/**
	 * Secuencias de dígitos con separadores típicos de un teléfono.
	 *
	 * <p>No se fija el formato porque la gente los escribe de mil maneras: con prefijo, con
	 * espacios de dos en dos, con guiones o del tirón. Se capturan candidatos anchos y luego se
	 * cuentan los dígitos, que es lo que de verdad distingue un teléfono de un número cualquiera.
	 */
	private static final Pattern POSIBLE_TELEFONO =
			Pattern.compile("\\+?\\d[\\d\\s.\\-()]{7,}\\d");

	/** Nueve dígitos es el mínimo de un teléfono español; quince, el máximo internacional. */
	private static final int DIGITOS_MIN = 9;
	private static final int DIGITOS_MAX = 15;

	private Anonimizador() {
	}

	public static String limpiar(String texto) {
		if (texto == null || texto.isBlank()) {
			return texto;
		}
		String sinCorreos = CORREO.matcher(texto).replaceAll("[correo]");
		return taparTelefonos(sinCorreos);
	}

	/**
	 * Sustituye sólo lo que tiene pinta de teléfono por el número de dígitos.
	 *
	 * <p>El recuento es lo que evita cargarse datos legítimos: un importe como «1.500 €», una
	 * fecha o «2 habitaciones» se quedan muy por debajo del mínimo y pasan intactos. Un número
	 * larguísimo —un IBAN, por ejemplo— también se tapa, y eso es deseable.
	 */
	private static String taparTelefonos(String texto) {
		Matcher encontrado = POSIBLE_TELEFONO.matcher(texto);
		StringBuilder salida = new StringBuilder();
		while (encontrado.find()) {
			long digitos = encontrado.group().chars().filter(Character::isDigit).count();
			boolean esTelefono = digitos >= DIGITOS_MIN && digitos <= DIGITOS_MAX;
			encontrado.appendReplacement(salida,
					Matcher.quoteReplacement(esTelefono ? "[teléfono]" : encontrado.group()));
		}
		encontrado.appendTail(salida);
		return salida.toString();
	}
}
