package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Un campo del formulario. Los atributos de presentación móvil (tipo de teclado, autocompletado)
 * NO viven aquí: los deriva el renderer a partir de {@link TipoCampo}, para que el JSON que se
 * edita a mano en el panel siga siendo corto y legible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Campo(
		String id,
		TipoCampo tipo,
		TextoLocalizado label,
		TextoLocalizado ayuda,
		boolean requerido,
		List<Opcion> opciones,
		Double min,
		Double max,
		/**
		 * Mínimo tomado de una fecha del anuncio, p. ej. {@code @anuncio.disponibleDesde}.
		 *
		 * <p>No puede ser un literal en el esquema: la fecha cambia con cada anuncio y el
		 * formulario se reutiliza entre ellos.
		 */
		String minReferencia,
		String maxReferencia,
		Integer maxLongitud,
		/** Id del campo numérico que determina cuántos elementos tiene el grupo repetible. */
		String repeticionesDesde,
		/** Etiqueta de cada elemento del grupo ("Persona 1", "Persoa 1"). */
		TextoLocalizado etiquetaElemento,
		/** Subcampos, sólo para GRUPO_REPETIBLE. */
		List<Campo> campos,
		CondicionVisibilidad visibleSi) {

	public Campo {
		opciones = opciones == null ? List.of() : List.copyOf(opciones);
		campos = campos == null ? List.of() : List.copyOf(campos);
	}

	public Opcion opcion(String valor) {
		return opciones.stream().filter(o -> o.valor().equals(valor)).findFirst().orElse(null);
	}

	public boolean visible(java.util.function.Function<String, Object> lector) {
		return visibleSi == null || visibleSi.seCumple(lector.apply(visibleSi.campo()));
	}
}
