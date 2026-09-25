package es.agata.renthelper.puntuacion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotaCombinadaTest {

	@Test
	void mitadYMitad() {
		// Marta: 100 del modelo, 60 tuyo.
		assertThat(NotaCombinada.calcular(100, 60, 0.5)).isEqualTo(80);
	}

	@Test
	void sinNotaTuyaCuentaLaDelModelo() {
		assertThat(NotaCombinada.calcular(73, null, 0.5)).isEqualTo(73);
	}

	@Test
	void sinNotaDelModeloCuentaLaTuya() {
		assertThat(NotaCombinada.calcular(null, 40, 0.5)).isEqualTo(40);
		assertThat(NotaCombinada.calcular(null, null, 0.5)).isNull();
	}

	@Test
	void elPesoMandaCuantoCuentaLaTuya() {
		assertThat(NotaCombinada.calcular(100, 60, 0.7)).isEqualTo(72);
		assertThat(NotaCombinada.calcular(100, 60, 0.0)).isEqualTo(100);
		assertThat(NotaCombinada.calcular(100, 60, 1.0)).isEqualTo(60);
	}

	@Test
	void redondea() {
		assertThat(NotaCombinada.calcular(71, 60, 0.5)).isEqualTo(66);
	}
}
