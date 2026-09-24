package es.agata.renthelper.api.publico;

import es.agata.renthelper.formularios.ErrorValidacion;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DTOs del formulario del candidato. Todo lo que sale por aquí es público por diseño. */
public final class DtosPublico {

	private DtosPublico() {
	}

	public record AnuncioPublico(
			String slug,
			String titulo,
			String direccion,
			BigDecimal rentaMensual,
			LocalDate disponibleDesde,
			List<String> idiomas,
			String idiomaPorDefecto,
			boolean aceptandoCandidaturas) {
	}

	/** El correo es opcional: sólo sirve para mandarle su enlace de continuación. */
	public record AltaPeticion(String nombre, String email, String idioma, boolean consentimiento) {
	}

	public record AltaRespuesta(String token, String urlContinuar, boolean correoEnviado) {
	}

	/**
	 * Estado completo del formulario. Incluye las respuestas guardadas para que reanudar sea
	 * abrir la URL: el enlace se abre dentro del navegador embebido de idealista, donde no se
	 * puede contar con que la cookie sobreviva.
	 */
	public record EstadoFormulario(
			AnuncioPublico anuncio,
			DefinicionFormulario esquema,
			Map<String, Object> respuestas,
			int pasoActual,
			String idioma,
			String estado,
			String nombre,
			/** Para poder decirle «te lo hemos enviado también por correo». */
			boolean tieneCorreo) {
	}

	public record GuardarPasoPeticion(Map<String, Object> respuestas, String idioma) {
	}

	public record ResultadoValidacion(boolean ok, List<ErrorValidacion> errores) {

		public static ResultadoValidacion de(List<ErrorValidacion> errores) {
			return new ResultadoValidacion(errores.isEmpty(), errores);
		}
	}
}
