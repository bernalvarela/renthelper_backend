package es.agata.renthelper.formularios;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La normalización es la clave de deduplicación: con 150 contactos habrá gente que abra el
 * enlace tres veces y escriba el número de tres formas distintas.
 */
class NormalizadorTelefonoTest {

	@ParameterizedTest
	@ValueSource(strings = {"600123456", "600 123 456", "600-123-456", "+34600123456",
			"0034600123456", "34 600 123 456", "(600) 123456"})
	void distintas_formas_del_mismo_numero_normalizan_igual(String bruto) {
		assertThat(NormalizadorTelefono.normalizar(bruto)).isEqualTo("+34600123456");
	}

	@ParameterizedTest
	@ValueSource(strings = {"12345", "no es un teléfono", "+1", ""})
	void rechaza_lo_que_no_es_un_telefono(String bruto) {
		assertThat(NormalizadorTelefono.normalizar(bruto)).isNull();
	}

	@Test
	void nulo_no_revienta() {
		assertThat(NormalizadorTelefono.normalizar(null)).isNull();
	}

	@Test
	void acepta_numeros_internacionales_en_e164() {
		assertThat(NormalizadorTelefono.normalizar("+351912345678")).isEqualTo("+351912345678");
	}
}
