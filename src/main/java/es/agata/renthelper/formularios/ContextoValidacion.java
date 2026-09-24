package es.agata.renthelper.formularios;

import java.time.LocalDate;

/**
 * Datos del anuncio que necesita la validación para resolver referencias como
 * {@code @anuncio.disponibleDesde}.
 *
 * <p>Se mantiene aparte de {@code ContextoAnuncio} (el de la puntuación) a propósito: aquí sólo
 * hacen falta las fechas, y así el motor de formularios no depende del de puntuación.
 */
public record ContextoValidacion(LocalDate disponibleDesde) {

	/** Cuándo queda libre el piso. Suelo natural de la fecha de ENTRADA. */
	public static final String REF_DISPONIBLE_DESDE = "@anuncio.disponibleDesde";

	/**
	 * Hoy. Suelo natural de la fecha de VISITA: enseñar el piso antes de que quede libre es
	 * posible —y conveniente, porque permite tener el filtrado hecho el día que se desocupa—,
	 * pero proponer una fecha ya pasada no.
	 */
	public static final String REF_HOY = "@hoy";

	public static final ContextoValidacion VACIO = new ContextoValidacion(null);

	public LocalDate fecha(String referencia) {
		if (REF_DISPONIBLE_DESDE.equals(referencia)) {
			return disponibleDesde;
		}
		if (REF_HOY.equals(referencia)) {
			// Un día de margen: el candidato puede estar en otro huso, o cruzar la medianoche
			// entre que abre el selector y envía. Rechazarlo por eso sería absurdo.
			return LocalDate.now().minusDays(1);
		}
		return null;
	}

	public static boolean referenciaConocida(String referencia) {
		return REF_DISPONIBLE_DESDE.equals(referencia) || REF_HOY.equals(referencia);
	}
}
