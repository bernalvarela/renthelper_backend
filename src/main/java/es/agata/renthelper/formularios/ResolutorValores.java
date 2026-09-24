package es.agata.renthelper.formularios;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.formularios.modelo.Opcion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resuelve las rutas que usan los criterios de la rúbrica sobre las respuestas.
 *
 * <p>Dos formas, y sólo dos, a propósito:
 * <ul>
 *   <li>{@code num_personas} — un campo de primer nivel.</li>
 *   <li>{@code ocupantes[].tipo_contrato} — un subcampo de todos los elementos de un grupo.</li>
 * </ul>
 *
 * <p>No hay más sintaxis, ni índices, ni expresiones. Un campo de reglas editable desde el
 * panel que aceptase un lenguaje de expresiones sería ejecución de código arbitrario.
 */
public final class ResolutorValores {

	private static final String MARCA_GRUPO = "[]";

	private ResolutorValores() {
	}

	public static boolean esRutaDeGrupo(String ruta) {
		return ruta != null && ruta.contains(MARCA_GRUPO);
	}

	/** Valor único. Para rutas de grupo devuelve el primero, o {@code null} si no hay. */
	public static Object valor(Map<String, Object> respuestas, String ruta) {
		List<Object> todos = valores(respuestas, ruta);
		return todos.isEmpty() ? null : todos.getFirst();
	}

	/** Todos los valores de la ruta. Para un campo simple, una lista de cero o un elemento. */
	public static List<Object> valores(Map<String, Object> respuestas, String ruta) {
		if (respuestas == null || ruta == null || ruta.isBlank()) {
			return List.of();
		}
		if (!esRutaDeGrupo(ruta)) {
			Object valor = respuestas.get(ruta);
			return valor == null ? List.of() : List.of(valor);
		}
		int corte = ruta.indexOf(MARCA_GRUPO);
		String idGrupo = ruta.substring(0, corte);
		String subcampo = ruta.substring(corte + MARCA_GRUPO.length()).replaceFirst("^\\.", "");

		if (!(respuestas.get(idGrupo) instanceof List<?> elementos)) {
			return List.of();
		}
		List<Object> resultado = new ArrayList<>();
		for (Object elemento : elementos) {
			if (elemento instanceof Map<?, ?> mapa) {
				Object valor = mapa.get(subcampo);
				if (valor != null) {
					resultado.add(valor);
				}
			}
		}
		return resultado;
	}

	/**
	 * Traduce los valores de una ruta a sus pesos numéricos.
	 *
	 * <p>Es lo que permite pedir "1.500 – 2.000 €" en vez de la nómina exacta y aun así
	 * calcular el ratio de solvencia.
	 */
	public static List<Double> pesos(DefinicionFormulario definicion, Map<String, Object> respuestas, String ruta) {
		Campo campo = campoDeRuta(definicion, ruta);
		if (campo == null) {
			return List.of();
		}
		List<Double> pesos = new ArrayList<>();
		for (Object valor : valores(respuestas, ruta)) {
			Opcion opcion = campo.opcion(String.valueOf(valor));
			if (opcion != null) {
				pesos.add(opcion.pesoODefecto());
			} else {
				Double numero = ValidadorRespuestas.aNumero(valor);
				if (numero != null) {
					pesos.add(numero);
				}
			}
		}
		return pesos;
	}

	/** Valores numéricos directos, sin pasar por las opciones. */
	public static List<Double> numeros(Map<String, Object> respuestas, String ruta) {
		List<Double> numeros = new ArrayList<>();
		for (Object valor : valores(respuestas, ruta)) {
			Double numero = ValidadorRespuestas.aNumero(valor);
			if (numero != null) {
				numeros.add(numero);
			}
		}
		return numeros;
	}

	public static Campo campoDeRuta(DefinicionFormulario definicion, String ruta) {
		if (definicion == null || ruta == null) {
			return null;
		}
		String id = esRutaDeGrupo(ruta)
				? ruta.substring(ruta.indexOf(MARCA_GRUPO) + MARCA_GRUPO.length()).replaceFirst("^\\.", "")
				: ruta;
		return definicion.campoPorId(id).orElse(null);
	}
}
