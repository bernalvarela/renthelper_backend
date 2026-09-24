package es.agata.renthelper.puntuacion.modelo;

/**
 * Conjunto cerrado de tipos de criterio.
 *
 * <p>La alternativa habitual — un lenguaje de expresiones (SpEL, MVEL, JsonLogic) en un campo
 * editable desde el panel — se descartó por dos motivos: es ejecución de código arbitrario desde
 * un formulario de administración, y no se puede renderizar como una pantalla de configuración.
 *
 * <p>Seis tipos cubren todo lo objetivo. Lo demás va a {@code ajusteLlm.instrucciones}, en texto
 * libre y con margen acotado: esa es la válvula de escape.
 */
public enum TipoCriterio {

	/** Cociente entre dos operandos, puntuado por tramos. Solvencia, ocupación. */
	RATIO,
	/** Cada valor de opción vale unos puntos. Tipo de contrato, duración prevista. */
	MAPEO_OPCIONES,
	/** Valor numérico puntuado por tramos. Edades, número de ocupantes. */
	UMBRAL_NUMERICO,
	/** Distancia en días entre una fecha respondida y una de referencia del anuncio. */
	FECHA,
	/** Sí / no. Mascotas, fumadores. */
	BOOLEANO,
	/** Cuánto se ha molestado en contestar. Completitud y longitud del texto libre. */
	PRESENCIA
}
