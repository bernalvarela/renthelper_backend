package es.agata.renthelper.config;

import es.agata.renthelper.api.ExcepcionNegocio;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones a respuestas {@code {codigo, mensaje}} y deja en el log lo necesario
 * para entender qué pasó sin reproducirlo.
 *
 * <p>Cada línea lleva el método y la ruta de la petición. Hay tres niveles, según de quién es el
 * problema:
 * <ul>
 *   <li><b>Negocio</b> ({@link ExcepcionNegocio}): la aplicación dijo que no a propósito. INFO,
 *       una línea, sin traza.</li>
 *   <li><b>Petición mal formada</b> (404, 405, JSON ilegible, validación): el fallo es del
 *       cliente. WARN, una línea con el motivo concreto, sin traza.</li>
 *   <li><b>Inesperado</b>: un fallo nuestro. ERROR con la traza completa y una referencia corta
 *       que también va en la respuesta, para encontrar la traza a partir del mensaje que ve el
 *       usuario.</li>
 * </ul>
 *
 * <p>El detalle técnico va al log, nunca a la respuesta: los endpoints públicos no deben filtrar
 * trazas ni nombres de tablas a quien pegue el enlace.
 */
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	@ExceptionHandler(ExcepcionNegocio.class)
	public ResponseEntity<Map<String, Object>> negocio(ExcepcionNegocio ex, HttpServletRequest peticion) {
		log.info("{} {} -> {} {}: {}", peticion.getMethod(), ruta(peticion),
				ex.getEstado().value(), ex.getCodigo(), ex.getMessage());
		return respuesta(ex.getEstado(), ex.getCodigo(), ex.getMessage());
	}

	/** Un fichero o una ruta que no existe. Frecuente (bots, enlaces viejos) y nada grave. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> noEncontrado(NoResourceFoundException ex, HttpServletRequest peticion) {
		log.warn("{} {} -> 404 no existe", peticion.getMethod(), ruta(peticion));
		return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No existe.");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex,
	                                                       HttpServletRequest peticion) {
		String errores = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.collect(Collectors.joining("; "));
		log.warn("{} {} -> 400 validación: {}", peticion.getMethod(), ruta(peticion), errores);
		return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", errores);
	}

	/** JSON roto o con un tipo que no casa: el motivo útil está en la causa más interna. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex,
	                                                           HttpServletRequest peticion) {
		log.warn("{} {} -> 400 cuerpo ilegible: {}", peticion.getMethod(), ruta(peticion),
				NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
		return respuesta(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La petición no es válida.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> tipoIncorrecto(MethodArgumentTypeMismatchException ex,
	                                                           HttpServletRequest peticion) {
		log.warn("{} {} -> 400 parámetro '{}' con valor '{}' no válido", peticion.getMethod(), ruta(peticion),
				ex.getName(), ex.getValue());
		return respuesta(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La petición no es válida.");
	}

	/**
	 * El cliente cerró la conexión antes de recibir la respuesta (pestaña cerrada, móvil sin
	 * cobertura). No hay a quién responder ni nada que arreglar.
	 */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void conexionCerrada(AsyncRequestNotUsableException ex, HttpServletRequest peticion) {
		log.debug("{} {} -> conexión cerrada por el cliente", peticion.getMethod(), ruta(peticion));
	}

	/**
	 * Autenticación y permisos son cosa de Spring Security (401/403). Si llegan hasta aquí, el
	 * {@code Exception.class} de abajo las convertiría en un 500.
	 */
	@ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
	public void seguridad(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> inesperado(Exception ex, HttpServletRequest peticion) {
		// El resto de errores de Spring MVC ya saben su código HTTP: método no admitido (405),
		// parámetro obligatorio ausente (400), tipo de contenido no soportado (415)... Son de la
		// petición, no nuestros. ErrorResponse es una interfaz y no vale en @ExceptionHandler.
		if (ex instanceof ErrorResponse errorPeticion) {
			HttpStatusCode estado = errorPeticion.getStatusCode();
			log.warn("{} {} -> {} {}", peticion.getMethod(), ruta(peticion), estado.value(), ex.getMessage());
			return respuesta(estado, "PETICION_INVALIDA", "La petición no es válida.");
		}

		String referencia = UUID.randomUUID().toString().substring(0, 8);
		log.error("{} {} -> 500 [ref {}] {}: {}", peticion.getMethod(), ruta(peticion), referencia,
				ex.getClass().getSimpleName(), NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), ex);
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("codigo", "ERROR_INTERNO");
		cuerpo.put("mensaje", "Algo ha fallado. Inténtalo de nuevo. Referencia: " + referencia);
		cuerpo.put("referencia", referencia);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cuerpo);
	}

	private static ResponseEntity<Map<String, Object>> respuesta(HttpStatusCode estado, String codigo, String mensaje) {
		return ResponseEntity.status(estado).body(Map.of("codigo", codigo, "mensaje", mensaje));
	}

	/** Ruta con la query, que suele ser lo que distingue una petición de otra. */
	private static String ruta(HttpServletRequest peticion) {
		String query = peticion.getQueryString();
		return query == null ? peticion.getRequestURI() : peticion.getRequestURI() + "?" + query;
	}
}
