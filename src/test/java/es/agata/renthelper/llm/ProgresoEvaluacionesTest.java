package es.agata.renthelper.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProgresoEvaluacionesTest {

	private final ProgresoEvaluaciones progreso = new ProgresoEvaluaciones();
	private final UUID candidatura = UUID.randomUUID();

	@Test
	void cadaPasoSustituyeAlAnterior() {
		progreso.empezar(candidatura);
		progreso.paso("Llamando a GROQ · openai/gpt-oss-120b");

		assertThat(progreso.de(List.of(candidatura)).get(candidatura).texto())
				.isEqualTo("Llamando a GROQ · openai/gpt-oss-120b");
	}

	@Test
	void alTerminarDesaparece() {
		progreso.empezar(candidatura);
		progreso.paso("Leyendo la respuesta");
		progreso.terminar(candidatura);

		assertThat(progreso.de(List.of(candidatura))).isEmpty();
	}

	@Test
	void fueraDeUnaEvaluacionNoAnotaNada() {
		// La prueba de conexión del panel también pasa por el evaluador, sin candidatura detrás.
		progreso.paso("Leyendo la respuesta");

		assertThat(progreso.de(List.of(candidatura))).isEmpty();
	}

	@Test
	void sóloDevuelveLasQueSeEstánEvaluando() {
		UUID otra = UUID.randomUUID();
		progreso.empezar(candidatura);

		assertThat(progreso.de(List.of(candidatura, otra))).containsOnlyKeys(candidatura);
	}
}
