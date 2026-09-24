package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Etiqueta multiidioma. Se serializa como un mapa plano ({@code {"es": "...", "gl": "..."}})
 * para que el JSON del esquema siga siendo legible en el textarea de administración.
 */
public record TextoLocalizado(Map<String, String> valores) {

	public static final String IDIOMA_FALLBACK = "es";

	@JsonCreator
	public static TextoLocalizado de(Map<String, String> valores) {
		return new TextoLocalizado(valores == null ? Map.of() : new LinkedHashMap<>(valores));
	}

	public static TextoLocalizado unico(String texto) {
		return new TextoLocalizado(Map.of(IDIOMA_FALLBACK, texto));
	}

	@JsonValue
	public Map<String, String> valores() {
		return valores;
	}

	/** Devuelve el idioma pedido; si falta, castellano; si tampoco, el primero que haya. */
	public String texto(String idioma) {
		String directo = valores.get(idioma);
		if (directo != null) {
			return directo;
		}
		String fallback = valores.get(IDIOMA_FALLBACK);
		if (fallback != null) {
			return fallback;
		}
		return valores.values().stream().findFirst().orElse("");
	}

	public Set<String> idiomas() {
		return valores.keySet();
	}

	public boolean vacio() {
		return valores.isEmpty();
	}
}
