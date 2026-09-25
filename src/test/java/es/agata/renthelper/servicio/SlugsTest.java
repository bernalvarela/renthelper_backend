package es.agata.renthelper.servicio;

import es.agata.renthelper.api.ExcepcionNegocio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugsTest {

	@Test
	void quitaTildesMayusculasYEspacios() {
		assertThat(Slugs.normalizar("  Piso Céntrico Ñandú 2026 ")).isEqualTo("piso-centrico-nandu-2026");
	}

	@Test
	void juntaLosSeparadoresYLimpiaLosExtremos() {
		assertThat(Slugs.normalizar("--piso // centro__2026!!")).isEqualTo("piso-centro-2026");
	}

	@Test
	void rechazaLoDemasiadoCortoOLoQueSeQuedaEnNada() {
		assertThatThrownBy(() -> Slugs.validar("ab")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> Slugs.validar("¡¿?!")).isInstanceOf(ExcepcionNegocio.class);
		assertThatThrownBy(() -> Slugs.validar("x".repeat(61))).isInstanceOf(ExcepcionNegocio.class);
	}

	@Test
	void aceptaUnoNormal() {
		assertThat(Slugs.validar("Piso Centro 2026")).isEqualTo("piso-centro-2026");
	}
}
