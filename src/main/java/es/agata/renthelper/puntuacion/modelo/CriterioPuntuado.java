package es.agata.renthelper.puntuacion.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Resultado de un criterio. Se persiste en {@code evaluacion.desglose}: sin el desglose no se
 * puede auditar por qué una candidatura quedó donde quedó, y el número suelto no sirve de nada
 * cuando estás revisando 150.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CriterioPuntuado(
		String id,
		String etiqueta,
		int peso,
		/** El valor antes de puntuar: el ratio 3,4 o los días de desfase. Es lo que miran los mínimos. */
		double valorBruto,
		int puntos,
		double puntosPonderados,
		String detalle) {
}
