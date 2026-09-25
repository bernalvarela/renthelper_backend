package es.agata.renthelper.api.admin;

import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.puntuacion.modelo.CriterioPuntuado;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DtosAdmin {

	private DtosAdmin() {
	}

	public record ResumenAnuncio(
			UUID id,
			String slug,
			String titulo,
			String direccion,
			BigDecimal rentaMensual,
			Integer habitaciones,
			LocalDate disponibleDesde,
			List<String> idiomas,
			String idiomaPorDefecto,
			UUID formSchemaId,
			UUID rubricaId,
			boolean aceptandoCandidaturas,
			int umbralAlerta,
			Instant ultimoDigestEn,
			long totalCandidaturas,
			long sinTriar,
			String enlacePublico) {
	}

	public record PeticionAnuncio(
			String titulo,
			String direccion,
			BigDecimal rentaMensual,
			Integer habitaciones,
			LocalDate disponibleDesde,
			List<String> idiomas,
			String idiomaPorDefecto,
			UUID formSchemaId,
			UUID rubricaId,
			Boolean aceptandoCandidaturas,
			Integer umbralAlerta,
			/**
			 * El enlace público, {@code /c/<slug>}. SÓLO al crear: vacío = uno corto aleatorio, y
			 * se normaliza («Piso Centro» → «piso-centro»). Al editar se ignora: una vez creado es
			 * fijo, porque ya puede estar en idealista o en manos de algún candidato.
			 */
			String slug) {
	}

	/** Una fila de la tabla de triaje. Lleva ya lo que se necesita para decidir sin abrir la ficha. */
	public record FilaCandidatura(
			UUID id,
			/** Para que la ficha sepa volver a la lista de SU anuncio, no a la de anuncios. */
			UUID anuncioId,
			String nombre,
			/** PLAUSIBLE | DUDOSO | FICTICIO | NO_VERIFICADO. Informativo, no puntúa. */
			String verificacionNombre,
			String motivoNombre,
			/** La llamada al LLM se saltó por nombre inventado; el panel ofrece lanzarla. */
			boolean llmOmitidoPorNombre,
			/** La del modelo: reglas + ajuste del LLM. */
			Integer puntuacion,
			/** La tuya, de 0 a 100. */
			Integer puntuacionManual,
			/** Por la que se ordena: la media ponderada de las dos, o la del modelo si no hay tuya. */
			Integer puntuacionCombinada,
			String estado,
			boolean noCumpleMinimos,
			List<String> motivosMinimos,
			boolean sintetica,
			Instant enviadaEn,
			/** Permite al panel detectar qué ha cambiado sin recargar la página. */
			Instant actualizadaEn,
			Integer segundosCumplimentacion,
			String resumen,
			String valoracion,
			List<String> banderas,
			List<String> preguntasPendientes,
			String confianza,
			String proveedor,
			/** El modelo concreto que respondió. La cadena tiene varios y conviene saber cuál fue. */
			String modelo,
			/** TOTAL | PARCIAL | NINGUNA | DESCONOCIDO. Null si no lo tienes activado. */
			String bonificacionGrado,
			/** Una línea para la ayuda emergente de la tabla. */
			String bonificacionResumen,
			/** Por qué no hay valoración del modelo. Es un fallo o una omisión, no un resumen. */
			String errorIa,
			String telefono,
			String email,
			/**
			 * Hay una evaluación en cola: el panel pone un indicador en la fila. Es cuándo está
			 * programada; si es más tarde que ahora, está aplazada esperando cuota. Null si no hay.
			 */
			Instant evaluacionProgramada,
			/** Qué está haciendo ahora la evaluación en curso («Llamando a GROQ · …»). Null si no hay. */
			String pasoEvaluacion,
			/** Desde cuándo está en ese paso, para enseñar cuánto lleva esperando al modelo. */
			Instant pasoDesde) {
	}

	public record EvaluacionDto(
			UUID id,
			String rol,
			String proveedor,
			String modelo,
			int puntuacionDeterminista,
			int ajusteLlm,
			int puntuacionTotal,
			List<CriterioPuntuado> desglose,
			String resumen,
			/** El juicio, aparte de los hechos: el modelo devuelve los dos. */
			String valoracion,
			List<String> banderas,
			List<String> preguntasPendientes,
			String confianza,
			Integer rubricaVersion,
			String promptVersion,
			Integer tokensEntrada,
			Integer tokensSalida,
			Integer latenciaMs,
			String error,
			Instant creadaEn) {
	}

	/**
	 * Lo que está haciendo el outbox por detrás.
	 *
	 * <p>Evaluar, alertar y enviar enlaces ocurre en segundo plano, minutos después de que la
	 * candidatura entre. Sin esta vista, «todavía no tiene nota» y «lleva cuatro horas aplazada
	 * por cuota» se ven exactamente igual desde el panel.
	 */
	public record Actividad(long pendientes, long fallidas, List<OperacionOutbox> recientes) {
	}

	// --- Informe comparativo de finalistas -----------------------------------------------

	/** Lo que necesita el panel: quién se puede comparar, hasta cuántos, y el último informe. */
	public record EstadoComparativa(int maxFinalistas, List<FinalistaElegible> finalistas,
	                                InformeComparativoDto ultimo) {
	}

	/** Una candidatura citada o seleccionada, candidata a entrar en la comparación. */
	public record FinalistaElegible(UUID id, String nombre, Integer puntuacion, Integer puntuacionManual,
	                                Integer puntuacionCombinada, String estado) {
	}

	public record PeticionComparativa(List<UUID> candidaturaIds) {
	}

	public record InformeComparativoDto(
			UUID id,
			Instant creadoEn,
			String proveedor,
			String modelo,
			String promptVersion,
			Integer latenciaMs,
			/** Alguna ya no es finalista, se borró o cambió después: conviene regenerarlo. */
			boolean desactualizado,
			String panorama,
			List<FinalistaInforme> finalistas,
			String riesgos,
			List<String> preguntas,
			/** Opinión del modelo: no toca las puntuaciones. */
			List<PosicionInforme> ordenSugerido) {
	}

	/** Un finalista del informe, ya con su nombre: al modelo sólo le llegó la letra. */
	public record FinalistaInforme(String etiqueta, UUID candidaturaId, String nombre,
	                               /** Las tres notas: la del modelo, la tuya y la combinada. */
	                               Integer puntuacion, Integer puntuacionManual, Integer puntuacionCombinada,
	                               String estado, String datosClave, String puntoFuerte, String puntoDebil) {
	}

	public record PosicionInforme(String etiqueta, UUID candidaturaId, String nombre, String motivo) {
	}

	public record OperacionOutbox(
			UUID id,
			String tipo,
			UUID referenciaId,
			String estado,
			int intentos,
			/** Esperas por cuota agotada. Se cuentan aparte de los fallos reales. */
			int aplazamientos,
			Instant creadoEn,
			Instant procesadoEn,
			Instant proximaEjecucion,
			String ultimoError,
			/** El modelo concreto que se pidió («Repuntuar con…»). Null = el primero de la cadena. */
			String modelo) {
	}

	public record FichaCandidatura(
			FilaCandidatura fila,
			DefinicionFormulario esquema,
			Map<String, Object> respuestas,
			List<EvaluacionDto> evaluaciones,
			List<ComentarioDto> comentarios,
			/** Informativo: nunca puntúa. Null si no lo tienes activado en Ajustes. */
			BonificacionDto bonificacion) {
	}

	public record PeticionTriaje(String estado, Integer puntuacionManual) {
	}

	public record PeticionEsquema(String nombre, Integer version, DefinicionFormulario definicion) {
	}

	public record PeticionRubrica(String nombre, Integer version, DefinicionRubrica definicion) {
	}

	/** Guardado con validación: si hay problemas no se escribe nada y se devuelven todos. */
	public record ResultadoGuardado(boolean ok, List<String> problemas, UUID id) {
	}

	public record ResumenEsquema(UUID id, String nombre, int version, Instant publicadoEn,
	                             DefinicionFormulario definicion) {
	}

	public record ResumenRubrica(UUID id, String nombre, int version, Instant publicadaEn,
	                             DefinicionRubrica definicion) {
	}

	/**
	 * Una etapa del embudo.
	 *
	 * <p>Lo que importa no es cuántas completaron, sino <b>cuántas se cayeron</b> y dónde. Por eso
	 * van los tres números: las que llegaron a la etapa, las que la superaron y la diferencia.
	 */
	public record PasoEmbudo(int paso, String titulo, long alcanzaron, long completaron, long abandonaron) {

		public static PasoEmbudo de(int paso, String titulo, long alcanzaron, long completaron) {
			return new PasoEmbudo(paso, titulo, alcanzaron, completaron,
					Math.max(0, alcanzaron - completaron));
		}
	}

	public record Sesion(String email, String rol) {
	}

	/**
	 * Un modelo de la cadena, tal y como lo ve el panel.
	 *
	 * <p>La clave de API no viaja nunca: sólo {@code tieneClave} y una pista con sus últimos
	 * caracteres, suficiente para reconocer cuál pusiste sin exponerla en el navegador ni en el
	 * historial del inspector de red.
	 */
	public record ProveedorDto(
			UUID id,
			String nombre,
			String urlBase,
			String modelo,
			boolean tieneClave,
			String pistaClave,
			boolean activo,
			int orden,
			boolean aptoDatosReales,
			boolean sombra,
			int limiteMensualLlamadas) {
	}

	/** Al guardar, `apiKey` vacía significa «no la toques», no «bórrala». */
	public record PeticionProveedor(
			String nombre,
			String urlBase,
			String modelo,
			String apiKey,
			Boolean activo,
			Boolean aptoDatosReales,
			Boolean sombra,
			Integer limiteMensualLlamadas) {
	}

	public record AjustesDto(
			String smtpHost,
			int smtpPuerto,
			String smtpUsuario,
			boolean smtpStarttls,
			String correoRemitente,
			boolean tienePasswordSmtp,
			String telegramChatId,
			boolean tieneTokenTelegram,
			/** Si falta, lo que se cifre hoy no se podrá descifrar tras reiniciar. */
			boolean claveMaestraConfigurada,
			boolean bonificacionActiva,
			int bonificacionEdadMin,
			int bonificacionEdadMax,
			String bonificacionNota) {
	}

	/** Igual que arriba: contraseña y token vacíos significan «déjalos como están». */
	public record PeticionAjustes(
			String smtpHost,
			Integer smtpPuerto,
			String smtpUsuario,
			String smtpPassword,
			Boolean smtpStarttls,
			String correoRemitente,
			String telegramToken,
			String telegramChatId,
			Boolean bonificacionActiva,
			Integer bonificacionEdadMin,
			Integer bonificacionEdadMax,
			String bonificacionNota) {
	}

	/** Un apunte tuyo sobre la candidatura. Nunca se envía al modelo. */
	public record ComentarioDto(UUID id, String texto, Instant creadoEn) {
	}

	public record PeticionComentario(String texto) {
	}

	/** Sólo la nota manual, sin tocar el estado de triaje. */
	public record PeticionNota(Integer puntuacionManual) {
	}

	/** Si el contrato entraría en la bonificación por edad, y con qué condiciones. */
	public record BonificacionDto(String grado, boolean aplicable, String resumen, String nota) {
	}
}
