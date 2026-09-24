package es.agata.renthelper.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lo que se le pide al modelo en <b>una sola llamada</b>.
 *
 * <p>Deliberadamente NO se le pide la puntuación total: eso lo calcula el motor determinista. El
 * modelo sólo aporta un ajuste acotado sobre lo cualitativo, para que el ranking siga siendo
 * reproducible y explicable.
 *
 * <p>Antes eran dos llamadas —una para el nombre, otra para la evaluación— y se juntaron para
 * gastar la mitad de cuota. El precio está dicho en {@link #verificacionNombre}: con dos llamadas
 * el modelo que puntuaba no veía el nombre y esa garantía era estructural; ahora es una
 * instrucción, que es más débil.
 *
 * <p>{@code banderasRojas} y {@code preguntasPendientes} valen más que el número: el número
 * ordena, pero el texto es lo que te hace decidir a quién llamas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AjusteEvaluacion(
		/**
		 * Ajuste sobre la puntuación determinista, dentro del margen que fija la rúbrica.
		 *
		 * <p>Envuelto y no primitivo a propósito: lo rellena un modelo, y que omita un campo es
		 * un fallo habitual. Aquí no vale el ajuste global de Jackson, porque el JSON lo parsea
		 * el conversor de Spring AI con su propio mapper. Sin ajuste, cero.
		 */
		Integer ajuste,
		/**
		 * Los hechos, sin juicio: quiénes son, qué ingresan, cuándo entrarían.
		 *
		 * <p>Es lo que se lee en el móvil para hacerse una idea en cinco segundos sin abrir la
		 * ficha. Va separado de la valoración porque mezclarlos producía un refrito: al pedir una
		 * sola cosa, el modelo resumía y se ahorraba el juicio, que es la parte que no se puede
		 * sacar de la tabla.
		 */
		String resumen,
		/** El juicio cualitativo: lo que los datos no dicen por sí solos. */
		String valoracion,
		/**
		 * Si el nombre parece real o inventado.
		 *
		 * <p>Nunca da ni quita puntos: es una <b>marca</b> para el panel. Un nombre raro no es un
		 * mal inquilino, y convertir «me suena inventado» en puntuación es el camino directo a
		 * penalizar nombres extranjeros.
		 */
		VerificacionNombre verificacionNombre,
		List<String> banderasRojas,
		/** Lo que falta por preguntar antes de decidir. */
		List<String> preguntasPendientes,
		/** ALTA | MEDIA | BAJA */
		String confianza) {

	public AjusteEvaluacion {
		ajuste = ajuste == null ? 0 : ajuste;
		banderasRojas = banderasRojas == null ? List.of() : List.copyOf(banderasRojas);
		preguntasPendientes = preguntasPendientes == null ? List.of() : List.copyOf(preguntasPendientes);
		confianza = confianza == null ? "MEDIA" : confianza;
		verificacionNombre = verificacionNombre == null
				? VerificacionNombre.noVerificado() : verificacionNombre;
	}

	public AjusteEvaluacion acotado(int maximo) {
		return new AjusteEvaluacion(Math.clamp(ajuste, -maximo, maximo), resumen, valoracion,
				verificacionNombre, banderasRojas, preguntasPendientes, confianza);
	}
}
