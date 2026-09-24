package es.agata.renthelper.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicaNombreTest {

	@ParameterizedTest
	@ValueSource(strings = {"asdfgh", "xxxx", "test", "prueba", "aaa", "Juan123", "A", "n/a"})
	void descarta_la_basura_evidente_sin_llamar_al_modelo(String bruto) {
		VerificacionNombre resultado = HeuristicaNombre.revisar(bruto);

		assertThat(resultado).isNotNull();
		assertThat(resultado.veredicto()).isEqualTo(VerificacionNombre.FICTICIO);
	}

	/**
	 * La parte que más importa: la heurística no puede opinar sobre si un nombre «suena» real.
	 * Cualquier regla escrita desde aquí sobre cuántas palabras o qué letras tiene un nombre
	 * marcaría como raro a medio mundo, y eso en vivienda es discriminación por origen.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
			"Juan Pérez",
			"Ngozi Adichie",
			"Mohammed Al-Farsi",
			"Xoán Núñez Barreiro",
			"Anna-Lena Müller",
			"Nguyễn Thị Hương",
			"O'Brien",
			"Jack Sparrow"})
	void deja_pasar_cualquier_nombre_con_forma_de_nombre(String bruto) {
		// Incluido "Jack Sparrow": que sea de ficción no se decide con una expresión regular,
		// eso es trabajo del modelo.
		assertThat(HeuristicaNombre.revisar(bruto)).isNull();
	}

	@Test
	void el_nombre_vacio_es_ficticio() {
		assertThat(HeuristicaNombre.revisar("   ")).isNotNull();
		assertThat(HeuristicaNombre.revisar(null)).isNotNull();
	}

	@Test
	void el_veredicto_del_modelo_se_normaliza_a_plausible_ante_cualquier_cosa_rara() {
		assertThat(new VerificacionNombre("quizá", null).veredicto())
				.isEqualTo(VerificacionNombre.PLAUSIBLE);
		assertThat(new VerificacionNombre("ficticio", "Personaje de Piratas del Caribe").veredicto())
				.isEqualTo(VerificacionNombre.FICTICIO);
	}
}
