package es.agata.renthelper.formularios;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.formularios.modelo.Paso;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Valida las respuestas contra el esquema, en servidor.
 *
 * <p>El frontend valida lo mismo para dar feedback inmediato, pero el endpoint público es
 * accesible sin autenticación: la validación del cliente no cuenta como validación.
 */
@Component
public class ValidadorRespuestas {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
	private static final int MAX_ELEMENTOS_GRUPO = 20;

	/** Valida sólo los campos de un paso. Se usa en el autosave. */
	public List<ErrorValidacion> validarPaso(DefinicionFormulario definicion, int indicePaso,
	                                         Map<String, Object> respuestas, ContextoValidacion contexto) {
		Paso paso = definicion.paso(indicePaso).orElse(null);
		if (paso == null) {
			return List.of(ErrorValidacion.de("_paso", "PASO_INEXISTENTE"));
		}
		List<ErrorValidacion> errores = new ArrayList<>();
		validarCampos(paso.campos(), respuestas, respuestas, "", errores, contexto);
		return errores;
	}

	/** Valida el formulario completo. Es la que decide si una candidatura puede pasar a ENVIADA. */
	public List<ErrorValidacion> validarCompleto(DefinicionFormulario definicion,
	                                             Map<String, Object> respuestas,
	                                             ContextoValidacion contexto) {
		List<ErrorValidacion> errores = new ArrayList<>();
		for (Paso paso : definicion.pasos()) {
			validarCampos(paso.campos(), respuestas, respuestas, "", errores, contexto);
		}
		return errores;
	}

	private void validarCampos(List<Campo> campos, Map<String, Object> ambito, Map<String, Object> raiz,
	                           String prefijo, List<ErrorValidacion> errores,
	                           ContextoValidacion contexto) {
		for (Campo campo : campos) {
			// Un campo oculto por condición no se exige ni se valida: el candidato nunca lo vio.
			if (!campo.visible(ambito::get)) {
				continue;
			}
			String ruta = prefijo.isEmpty() ? campo.id() : prefijo + "." + campo.id();
			Object valor = ambito.get(campo.id());

			if (campo.tipo() == es.agata.renthelper.formularios.modelo.TipoCampo.GRUPO_REPETIBLE) {
				validarGrupo(campo, ambito, raiz, ruta, errores, contexto);
				continue;
			}

			if (esVacio(valor)) {
				if (campo.requerido()) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.REQUERIDO));
				}
				continue;
			}
			validarValor(campo, valor, ruta, errores, contexto);
		}
	}

	private void validarGrupo(Campo campo, Map<String, Object> ambito, Map<String, Object> raiz,
	                          String ruta, List<ErrorValidacion> errores, ContextoValidacion contexto) {
		int esperados = elementosEsperados(campo, ambito);
		Object bruto = ambito.get(campo.id());
		List<?> elementos = bruto instanceof List<?> lista ? lista : List.of();

		if (elementos.size() < esperados) {
			errores.add(ErrorValidacion.de(ruta, ErrorValidacion.GRUPO_INCOMPLETO, "esperados", esperados));
		}
		int limite = Math.min(elementos.size(), MAX_ELEMENTOS_GRUPO);
		for (int i = 0; i < limite; i++) {
			if (!(elementos.get(i) instanceof Map<?, ?> mapa)) {
				errores.add(ErrorValidacion.de(ruta + "[" + i + "]", ErrorValidacion.GRUPO_INCOMPLETO));
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> elemento = (Map<String, Object>) mapa;
			validarCampos(campo.campos(), elemento, raiz, ruta + "[" + i + "]", errores, contexto);
		}
	}

	/** Cuántos elementos debe tener el grupo, según el campo numérico que lo gobierna. */
	public int elementosEsperados(Campo campo, Map<String, Object> ambito) {
		if (campo.repeticionesDesde() == null) {
			return 0;
		}
		Object contador = ambito.get(campo.repeticionesDesde());
		if (contador instanceof Number n) {
			return Math.clamp(n.intValue(), 0, MAX_ELEMENTOS_GRUPO);
		}
		try {
			return Math.clamp(Integer.parseInt(String.valueOf(contador)), 0, MAX_ELEMENTOS_GRUPO);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void validarValor(Campo campo, Object valor, String ruta, List<ErrorValidacion> errores,
	                          ContextoValidacion contexto) {
		switch (campo.tipo()) {
			case NUMERO -> {
				Double numero = aNumero(valor);
				if (numero == null) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.NUMERO_INVALIDO));
					return;
				}
				if (campo.min() != null && numero < campo.min()) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.MIN, "min", campo.min()));
				}
				if (campo.max() != null && numero > campo.max()) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.MAX, "max", campo.max()));
				}
			}
			case TEXTO, TEXTAREA -> {
				String texto = String.valueOf(valor);
				int limite = campo.maxLongitud() == null ? 2000 : campo.maxLongitud();
				if (texto.length() > limite) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.LONGITUD, "max", limite));
				}
			}
			case TELEFONO -> {
				if (!NormalizadorTelefono.valido(String.valueOf(valor))) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.FORMATO_TELEFONO));
				}
			}
			case EMAIL -> {
				if (!EMAIL.matcher(String.valueOf(valor)).matches()) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.FORMATO_EMAIL));
				}
			}
			case FECHA -> {
				LocalDate fecha;
				try {
					fecha = LocalDate.parse(String.valueOf(valor));
				} catch (DateTimeParseException e) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.FORMATO_FECHA));
					return;
				}
				// El piso no está libre antes de su fecha: pedir una anterior no es una
				// preferencia peor, es imposible. Mejor impedirlo que penalizarlo después.
				LocalDate minimo = contexto.fecha(campo.minReferencia());
				if (minimo != null && fecha.isBefore(minimo)) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.FECHA_MINIMA,
							"minimo", minimo.toString()));
				}
			}
			case SELECCION_UNICA -> {
				if (campo.opcion(String.valueOf(valor)) == null) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.OPCION_INVALIDA));
				}
			}
			case SELECCION_MULTIPLE -> {
				if (!(valor instanceof List<?> seleccion)) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.OPCION_INVALIDA));
					return;
				}
				for (Object elemento : seleccion) {
					if (campo.opcion(String.valueOf(elemento)) == null) {
						errores.add(ErrorValidacion.de(ruta, ErrorValidacion.OPCION_INVALIDA));
						return;
					}
				}
			}
			case CONSENTIMIENTO -> {
				if (!Boolean.TRUE.equals(aBooleano(valor))) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.CONSENTIMIENTO_REQUERIDO));
				}
			}
			case BOOLEANO -> {
				if (aBooleano(valor) == null) {
					errores.add(ErrorValidacion.de(ruta, ErrorValidacion.OPCION_INVALIDA));
				}
			}
			case GRUPO_REPETIBLE -> {
				// Tratado aparte en validarGrupo.
			}
		}
	}

	private static boolean esVacio(Object valor) {
		if (valor == null) {
			return true;
		}
		if (valor instanceof String s) {
			return s.isBlank();
		}
		if (valor instanceof List<?> l) {
			return l.isEmpty();
		}
		return false;
	}

	public static Double aNumero(Object valor) {
		if (valor instanceof Number n) {
			return n.doubleValue();
		}
		try {
			return Double.parseDouble(String.valueOf(valor).replace(',', '.'));
		} catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}

	public static Boolean aBooleano(Object valor) {
		if (valor instanceof Boolean b) {
			return b;
		}
		String texto = String.valueOf(valor);
		if ("true".equalsIgnoreCase(texto)) {
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(texto)) {
			return Boolean.FALSE;
		}
		return null;
	}
}
