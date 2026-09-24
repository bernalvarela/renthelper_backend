package es.agata.renthelper.puntuacion.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tramo de puntuación. Admite {@code desde}, {@code hasta} o ambos, y gana el primero que
 * encaja en el orden en que están declarados — así la misma estructura sirve para criterios
 * donde más es mejor (solvencia) y para los inversos (ocupación).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tramo(Double desde, Double hasta, int puntos) {

	public boolean contiene(double valor) {
		return (desde == null || valor >= desde) && (hasta == null || valor <= hasta);
	}
}
