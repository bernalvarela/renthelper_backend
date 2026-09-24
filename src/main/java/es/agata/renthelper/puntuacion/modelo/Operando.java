package es.agata.renthelper.puntuacion.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Uno de los dos lados de un criterio RATIO: o una ruta sobre las respuestas
 * ({@code ocupantes[].ingresos_netos}) o una referencia al anuncio ({@code @anuncio.renta}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Operando(
		String campo,
		String referencia,
		Agregacion agregacion,
		/** Traducir los valores de opción a su `peso` antes de agregar (rangos de ingresos). */
		Boolean usarPesoOpcion,
		Double constante) {

	public Agregacion agregacionODefecto() {
		return agregacion == null ? Agregacion.SUMA : agregacion;
	}

	public boolean usaPesos() {
		return Boolean.TRUE.equals(usarPesoOpcion);
	}
}
