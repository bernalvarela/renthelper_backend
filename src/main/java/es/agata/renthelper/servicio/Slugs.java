package es.agata.renthelper.servicio;

import es.agata.renthelper.api.ExcepcionNegocio;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El trozo del enlace público que identifica al anuncio: {@code /c/<slug>}.
 *
 * <p>Se escribe a mano desde el panel («piso-centro-2026») y se pega en idealista, así que se
 * normaliza en vez de rechazar: sin tildes, en minúsculas y con guiones donde había espacios.
 * Lo que no se puede arreglar —demasiado corto, demasiado largo— sí se rechaza.
 */
final class Slugs {

	static final int MINIMO = 3;
	static final int MAXIMO = 60;

	private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
	private static final Pattern NO_PERMITIDOS = Pattern.compile("[^a-z0-9]+");
	private static final Pattern GUIONES_EXTREMOS = Pattern.compile("^-+|-+$");

	private Slugs() {
	}

	/** «Piso Centro 2026» → «piso-centro-2026». */
	static String normalizar(String texto) {
		String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(texto.strip(), Normalizer.Form.NFD))
				.replaceAll("");
		String conGuiones = NO_PERMITIDOS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll("-");
		return GUIONES_EXTREMOS.matcher(conGuiones).replaceAll("");
	}

	/** Normaliza y comprueba la longitud; lanza un error de negocio legible si no vale. */
	static String validar(String texto) {
		String slug = normalizar(texto == null ? "" : texto);
		if (slug.length() < MINIMO || slug.length() > MAXIMO) {
			throw ExcepcionNegocio.invalido("ENLACE",
					"El enlace tiene que tener entre %d y %d letras, números o guiones.".formatted(MINIMO, MAXIMO));
		}
		return slug;
	}
}
