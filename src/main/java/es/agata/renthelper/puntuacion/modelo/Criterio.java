package es.agata.renthelper.puntuacion.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Un criterio de la rúbrica. Muchos campos son opcionales porque cada {@link TipoCriterio} usa
 * los suyos; a cambio, el JSON que se edita en el panel queda plano y cada criterio es una fila
 * de tabla renderizable en la UI de pesos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Criterio(
		String id,
		TipoCriterio tipo,
		int peso,
		String etiqueta,

		// RATIO
		Operando numerador,
		Operando denominador,

		// RATIO y UMBRAL_NUMERICO
		List<Tramo> tramos,

		// MAPEO_OPCIONES, UMBRAL_NUMERICO, FECHA, BOOLEANO
		String campo,
		Agregacion agregacion,
		Map<String, Integer> mapa,
		Integer puntosSiVacio,

		// FECHA
		String referencia,
		Integer toleranciaDias,
		Integer puntosDentroTolerancia,
		Integer puntosFuera,
		Boolean penalizarAnterior,
		/** Días durante los que la puntuación decae desde `puntosDentroTolerancia` hasta `puntosFuera`. */
		Integer diasDecaimiento,

		// BOOLEANO
		Integer puntosSi,
		Integer puntosNo,

		// PRESENCIA
		List<String> campos,
		Integer longitudMinimaTexto) {

	public Criterio {
		tramos = tramos == null ? List.of() : List.copyOf(tramos);
		campos = campos == null ? List.of() : List.copyOf(campos);
		mapa = mapa == null ? Map.of() : Map.copyOf(mapa);
	}

	public Agregacion agregacionODefecto() {
		return agregacion == null ? Agregacion.MEJOR : agregacion;
	}

	public String etiquetaOId() {
		return etiqueta == null || etiqueta.isBlank() ? id : etiqueta;
	}
}
