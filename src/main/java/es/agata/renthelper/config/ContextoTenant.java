package es.agata.renthelper.config;

import java.util.UUID;

/**
 * Tenant activo para la petición en curso.
 *
 * <p>Hoy siempre es el mismo, pero existe para que el día que haya un segundo propietario el
 * trabajo sea la pantalla de alta y no reescribir todas las consultas.
 */
public final class ContextoTenant {

	/** El tenant sembrado por Flyway. Mientras haya uno solo, es también el valor por defecto. */
	public static final UUID POR_DEFECTO = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final ThreadLocal<UUID> ACTUAL = new ThreadLocal<>();

	private ContextoTenant() {
	}

	public static UUID actual() {
		UUID valor = ACTUAL.get();
		return valor == null ? POR_DEFECTO : valor;
	}

	public static void establecer(UUID tenantId) {
		ACTUAL.set(tenantId);
	}

	public static void limpiar() {
		ACTUAL.remove();
	}
}
