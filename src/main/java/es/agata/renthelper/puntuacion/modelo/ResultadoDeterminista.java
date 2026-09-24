package es.agata.renthelper.puntuacion.modelo;

import java.util.List;

public record ResultadoDeterminista(
		int puntuacion,
		List<CriterioPuntuado> desglose,
		boolean noCumpleMinimos,
		List<String> motivosMinimos) {

	public static ResultadoDeterminista vacio() {
		return new ResultadoDeterminista(0, List.of(), false, List.of());
	}
}
