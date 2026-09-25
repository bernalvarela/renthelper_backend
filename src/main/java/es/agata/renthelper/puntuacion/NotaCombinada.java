package es.agata.renthelper.puntuacion;

/**
 * La nota por la que se ordena: la del modelo mezclada con la tuya.
 *
 * <p>La del modelo (reglas + ajuste del LLM) se sigue guardando y enseñando tal cual: es la que
 * mide «Comparar modelos» contra tu nota, y si tu nota entrase en ella se estaría comparando al
 * modelo consigo mismo. Esto es sólo para ordenar y decidir.
 *
 * <p>Sin nota tuya, cuenta la del modelo: es dar por hecho que estás de acuerdo hasta que digas
 * otra cosa, y así las que aún no has valorado no se hunden ni se saltan el orden.
 */
public final class NotaCombinada {

	private NotaCombinada() {
	}

	/**
	 * @param pesoManual cuánto pesa tu nota, de 0 a 1 (0,5 = la media).
	 * @return null sólo si no hay ni una ni otra.
	 */
	public static Integer calcular(Integer modelo, Integer manual, double pesoManual) {
		if (manual == null) {
			return modelo;
		}
		if (modelo == null) {
			return manual;
		}
		return (int) Math.round((1 - pesoManual) * modelo + pesoManual * manual);
	}
}
