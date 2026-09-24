package es.agata.renthelper.config;

import es.agata.renthelper.api.ExcepcionNegocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	@ExceptionHandler(ExcepcionNegocio.class)
	public ResponseEntity<Map<String, Object>> negocio(ExcepcionNegocio ex) {
		return ResponseEntity.status(ex.getEstado())
				.body(Map.of("codigo", ex.getCodigo(), "mensaje", ex.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> inesperado(Exception ex) {
		// El detalle va al log, nunca a la respuesta: los endpoints públicos no deben filtrar
		// trazas ni nombres de tablas a quien pegue el enlace.
		log.error("Error inesperado", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("codigo", "ERROR_INTERNO", "mensaje", "Algo ha fallado. Inténtalo de nuevo."));
	}
}
