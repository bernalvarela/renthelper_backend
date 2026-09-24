package es.agata.renthelper.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El riesgo de esto no es dejar pasar un teléfono: es cargarse datos legítimos.
 *
 * <p>Si un importe o una fecha acabaran como «[teléfono]», el modelo puntuaría a ciegas y nadie
 * se enteraría, porque el prompt no se mira. De ahí que la mitad de los casos comprueben lo que
 * NO se debe tocar.
 */
class AnonimizadorTest {

	record Caso(String entrada, String esperado, String porque) {
	}

	@Test
	void tapa_telefonos_escritos_de_cualquier_forma() {
		List<Caso> casos = List.of(
				new Caso("Llámame al 600123456", "Llámame al [teléfono]", "del tirón"),
				new Caso("mi móvil es 600 12 34 56", "mi móvil es [teléfono]", "de dos en dos"),
				new Caso("tel. 600-12-34-56", "tel. [teléfono]", "con guiones"),
				new Caso("+34 600 123 456 a cualquier hora", "[teléfono] a cualquier hora",
						"con prefijo internacional"));

		for (Caso caso : casos) {
			assertThat(Anonimizador.limpiar(caso.entrada()))
					.as("%s: %s", caso.porque(), caso.entrada())
					.isEqualTo(caso.esperado());
		}
	}

	@Test
	void tapa_correos() {
		assertThat(Anonimizador.limpiar("escríbeme a juan.perez+piso@gmail.com por favor"))
				.isEqualTo("escríbeme a [correo] por favor");
	}

	/**
	 * Lo que tiene que sobrevivir intacto.
	 *
	 * <p>El criterio es el número de dígitos, no el formato: nueve como mínimo. Por debajo de eso
	 * está casi todo lo que el formulario recoge de verdad.
	 */
	@Test
	void no_toca_los_datos_que_importan() {
		List<Caso> casos = List.of(
				new Caso("Ingresamos 2.100 € entre los dos", "Ingresamos 2.100 € entre los dos",
						"un importe"),
				new Caso("Podríamos entrar el 2026-10-15", "Podríamos entrar el 2026-10-15",
						"una fecha ISO"),
				new Caso("Somos 4 personas en 3 habitaciones", "Somos 4 personas en 3 habitaciones",
						"cifras pequeñas"),
				new Caso("Llevamos 8 años en el barrio", "Llevamos 8 años en el barrio",
						"una antigüedad"));

		for (Caso caso : casos) {
			assertThat(Anonimizador.limpiar(caso.entrada()))
					.as("no debería tocar %s", caso.porque())
					.isEqualTo(caso.esperado());
		}
	}

	@Test
	void aguanta_nulos_y_vacios() {
		assertThat(Anonimizador.limpiar(null)).isNull();
		assertThat(Anonimizador.limpiar("   ")).isEqualTo("   ");
	}
}
