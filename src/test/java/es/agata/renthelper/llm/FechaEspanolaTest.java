package es.agata.renthelper.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FechaEspanolaTest {

	@Test
	void convierteLaFechaIsoDelFormulario() {
		assertThat(ConstructorPrompt.fechaEspanola("2026-10-01")).isEqualTo("01/10/2026");
	}

	@Test
	void ignoraLaHoraSiLaFechaLaTrae() {
		assertThat(ConstructorPrompt.fechaEspanola("2026-10-01T09:30:00")).isEqualTo("01/10/2026");
	}

	@Test
	void loQueNoEsUnaFechaPasaTalCual() {
		assertThat(ConstructorPrompt.fechaEspanola("cuanto antes")).isEqualTo("cuanto antes");
		assertThat(ConstructorPrompt.fechaEspanola("01/10/2026")).isEqualTo("01/10/2026");
	}
}
