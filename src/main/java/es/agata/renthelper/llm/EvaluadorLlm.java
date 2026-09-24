package es.agata.renthelper.llm;

import es.agata.renthelper.dominio.ProveedorLlm;

/**
 * Puerto propio sobre el proveedor de LLM.
 *
 * <p>Existe aunque por debajo se use Spring AI: el día que su API cambie —y cambiará— hay que
 * tocar una clase, no el dominio. También es lo que permite comparar proveedores sin que el
 * orquestador sepa de ninguno en concreto.
 */
public interface EvaluadorLlm {

	ResultadoLlm evaluar(SolicitudEvaluacion solicitud, ProveedorLlm proveedor);

	/** Sin url, sin modelo o sin clave no se intenta la llamada. */
	boolean disponible(ProveedorLlm proveedor);

	/** Llamada mínima contra el proveedor, para el botón de comprobar del panel. */
	ResultadoPrueba probar(ProveedorLlm proveedor);
}
