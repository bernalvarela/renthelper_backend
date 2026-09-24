package es.agata.renthelper.puntuacion.modelo;

import java.util.List;

/** Cómo se colapsan los valores de un grupo repetible (los cuatro ocupantes) en uno solo. */
public enum Agregacion {

	SUMA,
	MEDIA,
	MAXIMO,
	MINIMO,
	PRIMERO,
	/** Sinónimo de MAXIMO, más legible en el JSON de la rúbrica. */
	MEJOR,
	/** Sinónimo de MINIMO. */
	PEOR;

	public double aplicar(List<Double> valores) {
		if (valores == null || valores.isEmpty()) {
			return 0d;
		}
		return switch (this) {
			case SUMA -> valores.stream().mapToDouble(Double::doubleValue).sum();
			case MEDIA -> valores.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
			case MAXIMO, MEJOR -> valores.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
			case MINIMO, PEOR -> valores.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
			case PRIMERO -> valores.getFirst();
		};
	}
}
