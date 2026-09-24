package es.agata.renthelper.formularios.modelo;

/**
 * Catálogo cerrado de tipos de campo. Es pequeño a propósito: con ocho tipos el renderer
 * de React puede derivar los atributos de teclado móvil (inputMode, autoComplete) del tipo,
 * sin ensuciar el JSON del esquema con detalles de presentación.
 */
public enum TipoCampo {

	TEXTO,
	TEXTAREA,
	NUMERO,
	TELEFONO,
	EMAIL,
	FECHA,
	BOOLEANO,
	SELECCION_UNICA,
	SELECCION_MULTIPLE,
	CONSENTIMIENTO,

	/**
	 * Array de sub-formularios: "la edad y el contrato de cada ocupante". Es el único tipo
	 * que permite puntuar esos datos de forma determinista en vez de dejarlos en texto libre,
	 * y el que hace inviables los constructores de formularios genéricos.
	 */
	GRUPO_REPETIBLE;

	public boolean esSeleccion() {
		return this == SELECCION_UNICA || this == SELECCION_MULTIPLE;
	}

	public boolean esTexto() {
		return this == TEXTO || this == TEXTAREA || this == TELEFONO || this == EMAIL;
	}
}
