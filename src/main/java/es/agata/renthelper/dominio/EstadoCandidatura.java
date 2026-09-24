package es.agata.renthelper.dominio;

public enum EstadoCandidatura {

	/** Ha puesto el nombre pero no ha terminado el formulario. */
	BORRADOR,
	/** Formulario enviado y validado en servidor. Pendiente de evaluar. */
	ENVIADA,
	/** Ya tiene evaluación principal. */
	EVALUADA,
	/** Triaje manual: no interesa. */
	DESCARTADA,
	/** Triaje manual: a la lista de visitas. */
	CITADA,
	/** Triaje manual: seleccionada. Exenta de la purga por retención. */
	SELECCIONADA;

	public boolean esTriada() {
		return this == DESCARTADA || this == CITADA || this == SELECCIONADA;
	}
}
