package es.agata.renthelper.puntuacion.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DefinicionRubrica(
		int version,
		Integer puntuacionMaxima,
		/**
		 * Arranca desactivado: las candidaturas que no llegan a mínimos se marcan y se ordenan al
		 * fondo, pero se evalúan y se muestran igual. Activarlo es un flag, no una migración.
		 */
		boolean descarteAutomaticoActivo,
		List<Criterio> criterios,
		List<Minimo> minimos,
		AjusteLlm ajusteLlm) {

	public DefinicionRubrica {
		criterios = criterios == null ? List.of() : List.copyOf(criterios);
		minimos = minimos == null ? List.of() : List.copyOf(minimos);
	}

	public int maximoODefecto() {
		return puntuacionMaxima == null || puntuacionMaxima <= 0 ? 100 : puntuacionMaxima;
	}

	public int sumaPesos() {
		return criterios.stream().mapToInt(Criterio::peso).sum();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Minimo(String criterioId, Operador operador, double valor, String mensaje) {

		public enum Operador {
			MENOR_QUE,
			MAYOR_QUE,
			IGUAL_A;

			public boolean incumple(double valorBruto, double umbral) {
				return switch (this) {
					case MENOR_QUE -> valorBruto < umbral;
					case MAYOR_QUE -> valorBruto > umbral;
					case IGUAL_A -> Double.compare(valorBruto, umbral) == 0;
				};
			}
		}
	}

	/**
	 * La válvula de escape en texto libre: todo lo que no encaja en los seis tipos de criterio.
	 * El ajuste está acotado para que una generación caprichosa no descoloque el ranking.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AjusteLlm(Integer maximo, String instrucciones) {

		public int maximoODefecto(int porDefecto) {
			return maximo == null || maximo < 0 ? porDefecto : maximo;
		}
	}
}
