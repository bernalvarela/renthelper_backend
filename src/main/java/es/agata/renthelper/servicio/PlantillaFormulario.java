package es.agata.renthelper.servicio;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.formularios.modelo.Paso;
import es.agata.renthelper.formularios.modelo.TextoLocalizado;
import es.agata.renthelper.formularios.modelo.TipoCampo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esqueleto de formulario para empezar de cero.
 *
 * <p>Se genera ya traducido a los idiomas pedidos: el validador exige que toda etiqueta esté en
 * todos ellos, y un esqueleto que no pasa su propia validación es una mala forma de empezar.
 */
public final class PlantillaFormulario {

	private PlantillaFormulario() {
	}

	public static DefinicionFormulario minima(List<String> idiomas) {
		Campo campo = new Campo("pregunta_1", TipoCampo.TEXTO,
				traducir(idiomas, "Escribe aquí tu primera pregunta"),
				traducir(idiomas, "Texto de ayuda opcional"),
				true, List.of(), null, null, null, null, 200, null, null, List.of(), null);

		Paso paso = new Paso("paso_1", traducir(idiomas, "Primer paso"),
				traducir(idiomas, "Describe brevemente qué se pregunta en este paso"), List.of(campo));

		return new DefinicionFormulario(1, List.of(paso));
	}

	/** El mismo texto en todos los idiomas: es un punto de partida para traducir a mano. */
	private static TextoLocalizado traducir(List<String> idiomas, String texto) {
		Map<String, String> valores = new LinkedHashMap<>();
		idiomas.forEach(idioma -> valores.put(idioma, texto));
		return TextoLocalizado.de(valores);
	}
}
