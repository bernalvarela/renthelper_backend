package es.agata.renthelper.llm;

import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import es.agata.renthelper.puntuacion.modelo.ResultadoDeterminista;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Entrada de una evaluación. Lleva las respuestas completas: la seudonimización la hace
 * {@link ConstructorPrompt} al renderizar, que es el único punto por el que salen datos.
 */
public record SolicitudEvaluacion(
		DefinicionFormulario formulario,
		DefinicionRubrica rubrica,
		Map<String, Object> respuestas,
		ResultadoDeterminista determinista,
		String tituloAnuncio,
		BigDecimal renta,
		Integer habitaciones,
		LocalDate disponibleDesde,
		boolean sintetica,
		/**
		 * El nombre, sólo para el veredicto de autenticidad.
		 *
		 * <p>Antes no salía nunca del servidor: el veredicto se pedía en una llamada aparte que
		 * recibía el nombre y nada más. Al juntar las dos llamadas para gastar la mitad de cuota,
		 * esa separación pasó de ser estructural a ser una instrucción del prompt.
		 */
		String nombre,
		/**
		 * Si el contrato entraría en la bonificación por edad, ya calculado.
		 *
		 * <p>Se le da hecho a propósito: el modelo no sabe de fiscalidad ni qué día es. Aquí sólo
		 * se le pide que lo mencione, nunca que lo deduzca ni que lo puntúe.
		 */
		String bonificacion) {
}
