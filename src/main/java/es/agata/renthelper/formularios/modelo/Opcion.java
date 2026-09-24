package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Opción de un desplegable.
 *
 * <p>{@code peso} es el truco que permite pedir rangos en vez de cifras exactas: la opción
 * "1.500 – 2.000 €" lleva peso 1750, y el criterio de solvencia suma pesos. Así se cumple la
 * minimización de datos del RGPD sin renunciar a la puntuación determinista.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Opcion(String valor, Double peso, TextoLocalizado etiqueta) {

	public double pesoODefecto() {
		return peso == null ? 0d : peso;
	}
}
