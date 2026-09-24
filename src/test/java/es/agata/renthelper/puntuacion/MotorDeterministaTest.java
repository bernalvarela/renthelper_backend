package es.agata.renthelper.puntuacion;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.formularios.modelo.Opcion;
import es.agata.renthelper.formularios.modelo.Paso;
import es.agata.renthelper.formularios.modelo.TextoLocalizado;
import es.agata.renthelper.formularios.modelo.TipoCampo;
import es.agata.renthelper.puntuacion.modelo.Agregacion;
import es.agata.renthelper.puntuacion.modelo.Criterio;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import es.agata.renthelper.puntuacion.modelo.Operando;
import es.agata.renthelper.puntuacion.modelo.ResultadoDeterminista;
import es.agata.renthelper.puntuacion.modelo.TipoCriterio;
import es.agata.renthelper.puntuacion.modelo.Tramo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El motor determinista existe precisamente para poder testearse: con 150 candidaturas por
 * anuncio, un ranking que cambia entre ejecuciones no sirve para nada.
 */
class MotorDeterministaTest {

	private final MotorDeterminista motor = new MotorDeterminista();
	/** 750 € de renta y 3 habitaciones, como el anuncio de ejemplo del sembrador. */
	private final ContextoAnuncio anuncio = new ContextoAnuncio(750, 3, LocalDate.of(2026, 1, 1));

	@Test
	void suma_los_pesos_de_los_rangos_de_ingresos_de_todos_los_ocupantes() {
		// Dos personas en el tramo 1.500-2.000 € (peso 1750) → 3500 / 750 = 4,67 → tramo alto.
		Map<String, Object> respuestas = Map.of(
				"num_personas", 2,
				"ocupantes", List.of(
						Map.of("ingresos_netos", "1500_2000"),
						Map.of("ingresos_netos", "1500_2000")));

		ResultadoDeterminista resultado = motor.puntuar(rubricaSolvencia(), formulario(), respuestas, anuncio);

		assertThat(resultado.desglose()).hasSize(1);
		assertThat(resultado.desglose().getFirst().valorBruto()).isCloseTo(4.667, within());
		assertThat(resultado.puntuacion()).isEqualTo(100);
		assertThat(resultado.noCumpleMinimos()).isFalse();
	}

	@Test
	void marca_los_minimos_sin_descartar_cuando_la_solvencia_no_llega() {
		// 1250 + 0 = 1250 / 750 = 1,67 → por debajo del mínimo de 2,0.
		Map<String, Object> respuestas = Map.of(
				"num_personas", 2,
				"ocupantes", List.of(
						Map.of("ingresos_netos", "1000_1500"),
						Map.of("ingresos_netos", "NINGUNO")));

		ResultadoDeterminista resultado = motor.puntuar(rubricaSolvencia(), formulario(), respuestas, anuncio);

		assertThat(resultado.noCumpleMinimos()).isTrue();
		assertThat(resultado.motivosMinimos()).containsExactly("Ingresos por debajo de 2 veces la renta");
		// Se marca, pero sigue puntuando: el descarte automático arranca desactivado.
		assertThat(resultado.puntuacion()).isGreaterThan(0);
	}

	@Test
	void no_revienta_cuando_faltan_respuestas() {
		ResultadoDeterminista resultado = motor.puntuar(rubricaSolvencia(), formulario(), Map.of(), anuncio);

		// Ratio 0 → cae en el tramo más bajo (10 puntos), no en un error ni en un nulo.
		assertThat(resultado.puntuacion()).isEqualTo(10);
		assertThat(resultado.desglose()).hasSize(1);
		assertThat(resultado.noCumpleMinimos()).isTrue();
	}

	/**
	 * Personas por habitación, con los tramos reales de la rúbrica.
	 *
	 * <p>Fija la escala que se decidió: una por habitación es lo ideal, 1,33 es el reparto
	 * típico de una pareja más dos individuales, y a partir de 2 son todas parejas —legal, pero
	 * el doble de desgaste—. Los tramos usan `hasta`, así que se evalúan en orden y gana el
	 * primero que encaja; si alguien los reordena, esto se entera.
	 */
	@Test
	void la_densidad_se_mide_en_personas_por_habitacion() {
		Criterio ocupacion = new Criterio("ocupacion", TipoCriterio.RATIO, 10, "Personas por habitación",
				new Operando("num_personas", null, Agregacion.PRIMERO, false, null),
				new Operando(null, ContextoAnuncio.REF_HABITACIONES, null, false, null),
				List.of(new Tramo(null, 1.0, 100), new Tramo(null, 1.34, 80),
						new Tramo(null, 2.0, 50), new Tramo(2.0, null, 15)),
				null, null, Map.of(), null, null, null, null, null, null, null, null, null,
				List.of(), null);

		DefinicionRubrica rubrica = new DefinicionRubrica(1, 100, false, List.of(ocupacion), List.of(), null);

		record Caso(int personas, int esperado, String porque) {
		}
		List<Caso> casos = List.of(
				new Caso(3, 100, "una persona por habitación"),
				new Caso(4, 80, "una pareja y dos individuales"),
				new Caso(6, 50, "tres parejas: cabe, pero el doble de desgaste"),
				new Caso(7, 15, "hacinamiento"));

		for (Caso caso : casos) {
			assertThat(motor.puntuar(rubrica, formulario(), Map.of("num_personas", caso.personas()), anuncio)
					.puntuacion())
					.as("%d personas en 3 habitaciones: %s", caso.personas(), caso.porque())
					.isEqualTo(caso.esperado());
		}
	}

	@Test
	void es_reproducible() {
		Map<String, Object> respuestas = Map.of(
				"num_personas", 3,
				"ocupantes", List.of(
						Map.of("ingresos_netos", "2000_3000"),
						Map.of("ingresos_netos", "MENOS_1000"),
						Map.of("ingresos_netos", "NINGUNO")));

		int primera = motor.puntuar(rubricaSolvencia(), formulario(), respuestas, anuncio).puntuacion();
		int segunda = motor.puntuar(rubricaSolvencia(), formulario(), respuestas, anuncio).puntuacion();

		assertThat(primera).isEqualTo(segunda);
	}

	// --- Andamiaje ---------------------------------------------------------------------

	private static org.assertj.core.data.Offset<Double> within() {
		return org.assertj.core.data.Offset.offset(0.01);
	}

	private DefinicionRubrica rubricaSolvencia() {
		Criterio solvencia = new Criterio("solvencia", TipoCriterio.RATIO, 35, "Solvencia",
				new Operando("ocupantes[].ingresos_netos", null, Agregacion.SUMA, true, null),
				new Operando(null, ContextoAnuncio.REF_RENTA, null, false, null),
				List.of(new Tramo(3.5, null, 100), new Tramo(3.0, null, 85), new Tramo(2.5, null, 65),
						new Tramo(2.0, null, 40), new Tramo(0.0, null, 10)),
				null, null, Map.of(), null, null, null, null, null, null, null, null, null,
				List.of(), null);

		var minimo = new DefinicionRubrica.Minimo("solvencia",
				DefinicionRubrica.Minimo.Operador.MENOR_QUE, 2.0,
				"Ingresos por debajo de 2 veces la renta");

		return new DefinicionRubrica(1, 100, false, List.of(solvencia), List.of(minimo), null);
	}

	private DefinicionFormulario formulario() {
		Campo ingresos = campo("ingresos_netos", TipoCampo.SELECCION_UNICA, List.of(
				new Opcion("NINGUNO", 0d, TextoLocalizado.unico("Sin ingresos")),
				new Opcion("MENOS_1000", 800d, TextoLocalizado.unico("Menos de 1.000 €")),
				new Opcion("1000_1500", 1250d, TextoLocalizado.unico("1.000 – 1.500 €")),
				new Opcion("1500_2000", 1750d, TextoLocalizado.unico("1.500 – 2.000 €")),
				new Opcion("2000_3000", 2500d, TextoLocalizado.unico("2.000 – 3.000 €"))));

		Campo personas = campo("num_personas", TipoCampo.NUMERO, List.of());

		Campo ocupantes = new Campo("ocupantes", TipoCampo.GRUPO_REPETIBLE,
				TextoLocalizado.unico("Ocupantes"), null, true, List.of(),
				null, null, null, null, null,
				"num_personas", TextoLocalizado.unico("Persona"), List.of(ingresos), null);

		return new DefinicionFormulario(1,
				List.of(new Paso("hogar", TextoLocalizado.unico("Hogar"), null,
						List.of(personas, ocupantes))));
	}

	private Campo campo(String id, TipoCampo tipo, List<Opcion> opciones) {
		//              min   max   minRef maxRef maxLong repDesde etiqElem
		return new Campo(id, tipo, TextoLocalizado.unico(id), null, true, opciones,
				null, null, null, null, null, null, null, List.of(), null);
	}
}
