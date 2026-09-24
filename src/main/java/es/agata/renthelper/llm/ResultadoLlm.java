package es.agata.renthelper.llm;

/**
 * Salida del evaluador con los metadatos que hacen falta para auditar y comparar modelos.
 * Sin proveedor, modelo y versiones, dos puntuaciones no son comparables.
 */
public record ResultadoLlm(
		AjusteEvaluacion ajuste,
		String proveedor,
		String modelo,
		Integer tokensEntrada,
		Integer tokensSalida,
		int latenciaMs,
		String textoCrudo,
		String error) {

	public boolean correcto() {
		return error == null && ajuste != null;
	}

	public static ResultadoLlm fallo(String proveedor, String modelo, int latenciaMs, String error) {
		return new ResultadoLlm(null, proveedor, modelo, null, null, latenciaMs, null, error);
	}
}
