package es.agata.renthelper.fiscal;

import es.agata.renthelper.dominio.Ajustes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Comprueba si las edades declaradas caen en el tramo bonificado.
 *
 * <p>Deliberadamente tonto: no sabe nada de fiscalidad. El tramo y la nota los declara el
 * propietario en Ajustes y aquí sólo se cuentan edades. Pedirle la norma al modelo era la
 * alternativa y no vale: sin acceso a internet y sin saber qué día es, reproduciría porcentajes
 * de su entrenamiento sin avisar de que se los inventa, y esto acaba en una declaración.
 *
 * <p>El resultado es <b>informativo</b>. No suma ni resta puntos, y así tiene que seguir: la
 * bonificación tiene base legal, pero ordenar candidatos por edad en el acceso a la vivienda es
 * justo lo que prohíbe la Ley 12/2023. Una cosa es saber que el contrato desgravaría y otra
 * colocar a un candidato por debajo de otro por haber nacido antes.
 */
@Component
public class BonificacionFiscal {

	/** Sólo cuentan los adultos: la bonificación mira al arrendatario, que firma el contrato. */
	private static final int MAYORIA_EDAD = 18;

	/**
	 * @param grado   hasta dónde llega: total, parcial, ninguna o sin datos para saberlo.
	 * @param resumen una línea para la ficha y para el prompt.
	 * @param nota    lo que escribió el propietario: porcentaje y requisitos.
	 */
	public enum Grado {
		/** Todas las personas adultas entran en el tramo. */
		TOTAL,
		/** Algunas sí y otras no: la reducción suele aplicarse en proporción. */
		PARCIAL,
		NINGUNA,
		/** No hay edades declaradas con las que comprobarlo. */
		DESCONOCIDO
	}

	public record Resultado(Grado grado, String resumen, String nota) {

		public static Resultado noProcede() {
			return new Resultado(null, null, null);
		}

		/** Total o parcial: en ambos casos el contrato desgrava algo. */
		public boolean aplicable() {
			return grado == Grado.TOTAL || grado == Grado.PARCIAL;
		}

		public boolean hayQueMostrarlo() {
			return resumen != null;
		}
	}

	public Resultado evaluar(Ajustes ajustes, Map<String, Object> respuestas) {
		if (ajustes == null || !ajustes.isBonificacionActiva()) {
			return Resultado.noProcede();
		}

		List<Integer> adultos = edadesAdultas(respuestas);
		if (adultos.isEmpty()) {
			return new Resultado(Grado.DESCONOCIDO, "No se puede comprobar: no hay edades declaradas.",
					ajustes.getBonificacionNota());
		}

		int min = ajustes.getBonificacionEdadMin();
		int max = ajustes.getBonificacionEdadMax();
		long dentro = adultos.stream().filter(edad -> edad >= min && edad <= max).count();
		String tramo = "%d-%d años".formatted(min, max);

		// Se distingue «todos» de «algunos» porque la reducción suele aplicarse en proporción a
		// los arrendatarios que cumplen, no en bloque. Decir sólo «sí» o «no» dejaría fuera el
		// caso más habitual: una pareja donde uno entra en el tramo y el otro no.
		Grado grado;
		String resumen;
		if (dentro == 0) {
			grado = Grado.NINGUNA;
			resumen = "No aplicable: ninguna de las %d personas adultas está en el tramo %s."
					.formatted(adultos.size(), tramo);
		} else if (dentro == adultos.size()) {
			grado = Grado.TOTAL;
			resumen = "Aplicable: las %d personas adultas están en el tramo %s."
					.formatted(adultos.size(), tramo);
		} else {
			grado = Grado.PARCIAL;
			resumen = "Aplicable en parte: %d de %d personas adultas están en el tramo %s."
					.formatted(dentro, adultos.size(), tramo);
		}
		return new Resultado(grado, resumen, ajustes.getBonificacionNota());
	}

	/**
	 * Las edades de la tabla de ocupantes.
	 *
	 * <p>Se leen del grupo repetible y no de un campo suelto porque es donde están. Si el
	 * formulario cambia y deja de haber `ocupantes` con `edad`, esto devuelve vacío y la ficha
	 * dice que no se puede comprobar, que es mejor que inventarse una respuesta.
	 */
	private List<Integer> edadesAdultas(Map<String, Object> respuestas) {
		List<Integer> edades = new ArrayList<>();
		if (respuestas == null || !(respuestas.get("ocupantes") instanceof List<?> ocupantes)) {
			return edades;
		}
		for (Object ocupante : ocupantes) {
			if (!(ocupante instanceof Map<?, ?> mapa)) {
				continue;
			}
			Object edad = mapa.get("edad");
			if (edad instanceof Number numero && numero.intValue() >= MAYORIA_EDAD) {
				edades.add(numero.intValue());
			}
		}
		return edades;
	}
}
