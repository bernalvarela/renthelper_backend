package es.agata.renthelper.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lo que se le pide al modelo al comparar a los finalistas, en una sola llamada.
 *
 * <p>Las candidaturas van por etiqueta (C1, C2...) y sin
 * nombre: el modelo no sabe quién es quién. Se guarda tal cual, con las etiquetas, en la base de
 * datos (jsonb), y {@link EtiquetasFinalistas} las cambia por los nombres al enseñarlo.
 *
 * <p>No lleva puntuaciones: la nota de cada candidatura sigue siendo la individual, que es
 * reproducible. Esto es para decidir entre los últimos, y el orden que sugiere es una opinión.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InformeComparativoLlm(
		/** Dos o tres frases sobre el grupo: en qué se parecen y qué los separa. */
		String panorama,
		/** Una entrada por finalista, en el orden de las etiquetas. */
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
			/** La etiqueta con la que se le presentó: «C1». */
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
