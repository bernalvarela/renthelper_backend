package es.agata.renthelper.servicio;

import java.security.SecureRandom;

/**
 * Identificadores cortos en base62.
 *
 * <p>Un UUID completo en una URL pegada en un mensaje de idealista queda largo, feo y se rompe
 * al copiarlo. Diez caracteres base62 dan ~59 bits de entropía: imposible de adivinar por fuerza
 * bruta con el límite por IP, y cabe en una línea.
 *
 * <p>{@link SecureRandom} y no {@code Random}: el token de candidatura es la única credencial que
 * protege los datos que ha escrito una persona.
 */
public final class GeneradorTokens {

	private static final char[] ALFABETO =
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
	private static final SecureRandom ALEATORIO = new SecureRandom();

	public static final int LONGITUD_TOKEN = 12;
	public static final int LONGITUD_SLUG = 8;

	private GeneradorTokens() {
	}

	public static String token() {
		return generar(LONGITUD_TOKEN);
	}

	public static String slug() {
		return generar(LONGITUD_SLUG);
	}

	private static String generar(int longitud) {
		StringBuilder sb = new StringBuilder(longitud);
		for (int i = 0; i < longitud; i++) {
			sb.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
		}
		return sb.toString();
	}
}
