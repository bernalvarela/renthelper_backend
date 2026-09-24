package es.agata.renthelper.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Veredicto sobre si el nombre escrito en el formulario parece real o inventado.
 *
 * <p>Se pide en una llamada <b>aislada</b>, que recibe el nombre y nada más. Esa separación es el
 * punto entero del diseño: el modelo que juzga el nombre no ve ingresos ni profesión, así que no
 * puede dejar que el apellido tiña la puntuación; y el modelo que puntúa no ve el nombre, sólo
 * este veredicto. Mezclarlo todo en un prompt reintroduciría el apellido como vector de sesgo.
 *
 * <p>Nunca da ni quita puntos: es una <b>marca</b> para el panel. Un nombre raro no es un mal
 * inquilino, y convertir «me suena inventado» en puntuación es exactamente el camino a penalizar
 * nombres extranjeros.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificacionNombre(String veredicto, String motivo) {

	public static final String PLAUSIBLE = "PLAUSIBLE";
	public static final String DUDOSO = "DUDOSO";
	public static final String FICTICIO = "FICTICIO";
	public static final String NO_VERIFICADO = "NO_VERIFICADO";

	public VerificacionNombre {
		veredicto = normalizar(veredicto);
		motivo = motivo == null || motivo.isBlank() ? null : motivo.trim();
	}

	public static VerificacionNombre noVerificado() {
		return new VerificacionNombre(NO_VERIFICADO, null);
	}

	public static VerificacionNombre ficticio(String motivo) {
		return new VerificacionNombre(FICTICIO, motivo);
	}

	public boolean merecePena() {
		return FICTICIO.equals(veredicto) || DUDOSO.equals(veredicto);
	}

	/** Ante cualquier cosa rara devuelta por el modelo, plausible: la duda no penaliza. */
	private static String normalizar(String bruto) {
		if (bruto == null) {
			return PLAUSIBLE;
		}
		String limpio = bruto.trim().toUpperCase();
		return switch (limpio) {
			case FICTICIO, DUDOSO, NO_VERIFICADO, PLAUSIBLE -> limpio;
			default -> PLAUSIBLE;
		};
	}
}
