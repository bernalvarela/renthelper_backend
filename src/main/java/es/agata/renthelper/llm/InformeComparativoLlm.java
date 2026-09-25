package es.agata.renthelper.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lo que se le pide al modelo al comparar a los finalistas, en una sola llamada.
 *
 * <p>Las candidaturas van por letra (A, B, C...) y sin nombre: el modelo no sabe quién es
 * quién, y el servicio traduce las letras de vuelta al guardar. Se guarda tal cual en la base de
 * datos (jsonb), así que también es el formato del informe en el panel.
 *
 * <p>No lleva puntuaciones: la nota de cada candidatura sigue siendo la individual, que es
 * reproducible. Esto es para decidir entre los últimos, y el orden que sugiere es una opinión.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InformeComparativoLlm(
		/** Dos o tres frases sobre el grupo: en qué se parecen y qué los separa. */
		String panorama,
		/** Una entrada por finalista, en el orden de las letras. */
		List<Finalista> finalistas,
		/** Los riesgos comparados: quién arriesga más y en qué. */
		String riesgos,
		/** Preguntas concretas para la visita o la llamada que desempatarían. */
		List<String> preguntas,
		/** Orden sugerido, del que más convence al que menos. Opinión del modelo. */
		List<Posicion> ordenSugerido) {

	public InformeComparativoLlm {
		finalistas = finalistas == null ? List.of() : List.copyOf(finalistas);
		preguntas = preguntas == null ? List.of() : List.copyOf(preguntas);
		ordenSugerido = ordenSugerido == null ? List.of() : List.copyOf(ordenSugerido);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Finalista(
			/** La letra con la que se le presentó: «A». */
			String etiqueta,
			/** Una línea con sus datos clave: ingresos frente a renta, fechas, personas. */
			String datosClave,
			/** Lo que tiene mejor que los demás. */
			String puntoFuerte,
			/** Lo que tiene peor que los demás. */
			String puntoDebil) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Posicion(String etiqueta, String motivo) {
	}
}
