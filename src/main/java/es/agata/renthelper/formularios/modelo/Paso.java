package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Paso(String id, TextoLocalizado titulo, TextoLocalizado descripcion, List<Campo> campos) {

	public Paso {
		campos = campos == null ? List.of() : List.copyOf(campos);
	}
}
