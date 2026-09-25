package es.agata.renthelper.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EtiquetasFinalistasTest {

	private static Map<String, String> nombres(int cuantos) {
		Map<String, String> nombres = new LinkedHashMap<>();
		for (int i = 0; i < cuantos; i++) {
			nombres.put(EtiquetasFinalistas.etiqueta(i), "Persona " + (i + 1));
		}
		return nombres;
	}

	@Test
	void cambiaCadaEtiquetaPorSuNombre() {
		assertThat(EtiquetasFinalistas.sustituir("Lo que separa a C2 del resto es su margen, frente a C1 y C4.", nombres(4)))
				.isEqualTo("Lo que separa a Persona 2 del resto es su margen, frente a Persona 1 y Persona 4.");
	}

	@Test
	void lasPreguntasEmpiezanPorElNombre() {
		assertThat(EtiquetasFinalistas.sustituir("C3: ¿Cuánto lleva en su empresa?", nombres(3)))
				.isEqualTo("Persona 3: ¿Cuánto lleva en su empresa?");
	}

	@Test
	void noConfundeC1ConC10() {
		assertThat(EtiquetasFinalistas.sustituir("C10 gana a C1.", nombres(10)))
				.isEqualTo("Persona 10 gana a Persona 1.");
	}

	@Test
	void noTocaLasPalabrasNiLasEtiquetasQueNoExisten() {
		// «A partir de» es texto normal; C9 no está en este informe.
		assertThat(EtiquetasFinalistas.sustituir("A partir de 01/10/2026, C9 o CC1.", nombres(3)))
				.isEqualTo("A partir de 01/10/2026, C9 o CC1.");
	}

	@Test
	void textoVacioONulo() {
		assertThat(EtiquetasFinalistas.sustituir(null, nombres(2))).isNull();
		assertThat(EtiquetasFinalistas.sustituir("", nombres(2))).isEmpty();
	}
}
