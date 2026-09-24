package es.agata.renthelper.formularios.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DefinicionFormulario(int version, List<Paso> pasos) {

	public DefinicionFormulario {
		pasos = pasos == null ? List.of() : List.copyOf(pasos);
	}

	public Optional<Paso> paso(int indice) {
		return indice >= 0 && indice < pasos.size() ? Optional.of(pasos.get(indice)) : Optional.empty();
	}

	public int numeroPasos() {
		return pasos.size();
	}

	/** Todos los campos de todos los pasos, incluidos los subcampos de los grupos repetibles. */
	public Stream<Campo> todosLosCampos() {
		List<Campo> acumulado = new ArrayList<>();
		pasos.forEach(p -> recolectar(p.campos(), acumulado));
		return acumulado.stream();
	}

	/** Campos de primer nivel (sin descender a los grupos repetibles). */
	public Stream<Campo> camposRaiz() {
		return pasos.stream().flatMap(p -> p.campos().stream());
	}

	public Optional<Campo> campoPorId(String id) {
		return todosLosCampos().filter(c -> c.id().equals(id)).findFirst();
	}

	private static void recolectar(List<Campo> campos, List<Campo> acumulado) {
		for (Campo campo : campos) {
			acumulado.add(campo);
			if (!campo.campos().isEmpty()) {
				recolectar(campo.campos(), acumulado);
			}
		}
	}
}
