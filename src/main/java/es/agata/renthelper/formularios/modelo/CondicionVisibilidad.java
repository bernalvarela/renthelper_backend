package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/**
 * Condición para mostrar un campo. Dentro de un GRUPO_REPETIBLE el campo referenciado es el
 * hermano del mismo elemento, no uno del formulario completo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CondicionVisibilidad(String campo, Operador op, String valor, List<String> valores) {

	public enum Operador {
		IGUAL,
		DISTINTO,
		EN,
		MAYOR_QUE,
		MENOR_QUE,
		RELLENO
	}

	public CondicionVisibilidad {
		valores = valores == null ? List.of() : List.copyOf(valores);
		op = op == null ? Operador.IGUAL : op;
	}

	public boolean seCumple(Object valorActual) {
		String actual = valorActual == null ? null : String.valueOf(valorActual);
		return switch (op) {
			case IGUAL -> Objects.equals(actual, valor);
			case DISTINTO -> !Objects.equals(actual, valor);
			case EN -> actual != null && valores.contains(actual);
			case RELLENO -> actual != null && !actual.isBlank();
			case MAYOR_QUE -> comparaNumerico(actual) > 0;
			case MENOR_QUE -> comparaNumerico(actual) < 0;
		};
	}

	private int comparaNumerico(String actual) {
		try {
			return Double.compare(Double.parseDouble(actual), Double.parseDouble(valor));
		} catch (NumberFormatException | NullPointerException e) {
			return 0;
		}
	}
}
