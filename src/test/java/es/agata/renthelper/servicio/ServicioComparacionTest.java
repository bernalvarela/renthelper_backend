package es.agata.renthelper.servicio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La correlación es lo que convierte «he probado tres modelos» en «este ordena como yo».
 * Si estuviera mal calculada, la comparación diría lo contrario de lo que pasa.
 */
class ServicioComparacionTest {

	@Test
	void mismo_orden_da_correlacion_uno() {
		Double correlacion = ServicioComparacion.spearman(
				List.of(10d, 20d, 30d, 40d),
				List.of(1d, 2d, 3d, 4d));

		assertThat(correlacion).isEqualTo(1.0);
	}

	@Test
	void orden_inverso_da_correlacion_menos_uno() {
		Double correlacion = ServicioComparacion.spearman(
				List.of(10d, 20d, 30d, 40d),
				List.of(4d, 3d, 2d, 1d));

		assertThat(correlacion).isEqualTo(-1.0);
	}

	@Test
	void compara_el_orden_y_no_las_puntuaciones_absolutas() {
		// Un modelo que puntúa sistemáticamente más bajo pero ordena igual es igual de útil:
		// lo que se hace con la lista es decidir a quién llamar primero.
		Double correlacion = ServicioComparacion.spearman(
				List.of(90d, 70d, 50d),
				List.of(45d, 35d, 25d));

		assertThat(correlacion).isEqualTo(1.0);
	}

	@Test
	void empates_no_rompen_el_calculo() {
		Double correlacion = ServicioComparacion.spearman(
				List.of(50d, 50d, 90d),
				List.of(40d, 45d, 95d));

		assertThat(correlacion).isNotNull().isBetween(0.5, 1.0);
	}

	@Test
	void sin_variacion_no_hay_correlacion_definida() {
		assertThat(ServicioComparacion.spearman(List.of(50d, 50d, 50d), List.of(10d, 20d, 30d)))
				.isNull();
	}
}
