package es.agata.renthelper.puntuacion;

import es.agata.renthelper.dominio.Anuncio;

import java.time.LocalDate;

/**
 * Los datos del anuncio a los que puede referirse la rúbrica con {@code @anuncio.*}.
 * Es una lista cerrada por el mismo motivo que los tipos de criterio: nada de expresiones.
 */
public record ContextoAnuncio(double renta, Integer habitaciones, LocalDate disponibleDesde) {

	public static final String REF_RENTA = "@anuncio.renta";
	public static final String REF_HABITACIONES = "@anuncio.habitaciones";
	public static final String REF_DISPONIBLE_DESDE = "@anuncio.disponibleDesde";

	public static ContextoAnuncio de(Anuncio anuncio) {
		return new ContextoAnuncio(
				anuncio.getRentaMensual() == null ? 0d : anuncio.getRentaMensual().doubleValue(),
				anuncio.getHabitaciones(),
				anuncio.getDisponibleDesde());
	}

	public Double numero(String referencia) {
		return switch (referencia == null ? "" : referencia) {
			case REF_RENTA -> renta;
			case REF_HABITACIONES -> habitaciones == null ? null : habitaciones.doubleValue();
			default -> null;
		};
	}

	public LocalDate fecha(String referencia) {
		return REF_DISPONIBLE_DESDE.equals(referencia) ? disponibleDesde : null;
	}
}
