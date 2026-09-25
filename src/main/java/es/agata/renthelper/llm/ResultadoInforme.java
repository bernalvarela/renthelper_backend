package es.agata.renthelper.llm;

/** Lo que devuelve la llamada que compara finalistas. Con `error`, no hay informe. */
public record ResultadoInforme(
		InformeComparativoLlm informe,
		String proveedor,
		String modelo,
		Integer tokensEntrada,
		Integer tokensSalida,
		int latenciaMs,
		String error) {

	public boolean correcto() {
		return error == null && informe != null;
	}
}
