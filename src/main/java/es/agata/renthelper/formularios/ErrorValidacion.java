package es.agata.renthelper.formularios;

import java.util.Map;

/**
 * Error de validación del servidor.
 *
 * <p>Devuelve un <b>código</b>, no un texto: el formulario es bilingüe y la traducción vive en
 * el frontend. Así no hay que duplicar los mensajes en castellano y gallego en el backend.
 */
public record ErrorValidacion(String campo, String codigo, Map<String, Object> params) {

	public static ErrorValidacion de(String campo, String codigo) {
		return new ErrorValidacion(campo, codigo, Map.of());
	}

	public static ErrorValidacion de(String campo, String codigo, String clave, Object valor) {
		return new ErrorValidacion(campo, codigo, Map.of(clave, valor));
	}

	public static final String REQUERIDO = "REQUERIDO";
	public static final String NUMERO_INVALIDO = "NUMERO_INVALIDO";
	public static final String MIN = "MIN";
	public static final String MAX = "MAX";
	public static final String LONGITUD = "LONGITUD";
	public static final String OPCION_INVALIDA = "OPCION_INVALIDA";
	public static final String FORMATO_TELEFONO = "FORMATO_TELEFONO";
	public static final String FORMATO_EMAIL = "FORMATO_EMAIL";
	public static final String FORMATO_FECHA = "FORMATO_FECHA";
	public static final String FECHA_MINIMA = "FECHA_MINIMA";
	public static final String GRUPO_INCOMPLETO = "GRUPO_INCOMPLETO";
	public static final String CONSENTIMIENTO_REQUERIDO = "CONSENTIMIENTO_REQUERIDO";
}
