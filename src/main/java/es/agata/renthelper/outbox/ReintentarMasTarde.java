package es.agata.renthelper.outbox;

/**
 * El trabajo no se ha podido completar por algo pasajero: cuota agotada, proveedor saturado.
 *
 * <p>Va aparte de una excepción normal porque la reacción es distinta: no es un fallo que haya
 * que investigar, sino una espera. Sin esta distinción, una cuota diaria agotada dejaría la
 * candidatura con puntuación sólo de reglas <b>para siempre</b>, aunque el proveedor volviera a
 * estar disponible diez minutos después.
 */
public class ReintentarMasTarde extends RuntimeException {

	public ReintentarMasTarde(String mensaje) {
		super(mensaje);
	}
}
