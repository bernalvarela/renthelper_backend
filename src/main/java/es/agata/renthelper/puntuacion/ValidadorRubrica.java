package es.agata.renthelper.puntuacion;

import es.agata.renthelper.formularios.ResolutorValores;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.puntuacion.modelo.Criterio;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import es.agata.renthelper.puntuacion.modelo.Operando;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Valida la rúbrica contra el esquema de formulario del anuncio al guardarla.
 *
 * <p>Lo importante es que comprueba que cada {@code campo} referenciado existe de verdad: una
 * rúbrica que apunta a un campo renombrado no falla, simplemente puntúa cero en silencio, y eso
 * se descubre tarde y mal.
 *
 * <p>Como el formulario no recoge origen, nacionalidad, situación familiar ni discapacidad, esta
 * comprobación también impide de hecho construir criterios sobre atributos protegidos.
 */
@Component
public class ValidadorRubrica {

	public List<String> validar(DefinicionRubrica rubrica, DefinicionFormulario formulario) {
		List<String> problemas = new ArrayList<>();
		if (rubrica == null) {
			return List.of("La rúbrica está vacía.");
		}
		if (rubrica.criterios().isEmpty()) {
			problemas.add("La rúbrica no tiene criterios.");
		}
		if (rubrica.sumaPesos() <= 0) {
			problemas.add("La suma de pesos es cero: ninguna candidatura puntuaría.");
		}

		Set<String> ids = new HashSet<>();
		for (Criterio criterio : rubrica.criterios()) {
			if (criterio.id() == null || criterio.id().isBlank()) {
				problemas.add("Hay un criterio sin id.");
				continue;
			}
			if (!ids.add(criterio.id())) {
				problemas.add("Id de criterio duplicado: " + criterio.id());
			}
			if (criterio.tipo() == null) {
				problemas.add("El criterio " + criterio.id() + " no tiene tipo.");
				continue;
			}
			if (criterio.peso() <= 0) {
				problemas.add("El criterio " + criterio.id() + " tiene peso " + criterio.peso() + ".");
			}
			validarSegunTipo(criterio, formulario, problemas);
		}

		for (DefinicionRubrica.Minimo minimo : rubrica.minimos()) {
			if (!ids.contains(minimo.criterioId())) {
				problemas.add("El mínimo apunta al criterio " + minimo.criterioId() + ", que no existe.");
			}
			if (minimo.operador() == null) {
				problemas.add("El mínimo sobre " + minimo.criterioId() + " no tiene operador.");
			}
		}

		if (rubrica.ajusteLlm() != null && rubrica.ajusteLlm().maximoODefecto(15) > 40) {
			problemas.add("El ajuste del LLM supera los 40 puntos. Con ese margen el ranking deja de"
					+ " ser reproducible y lo decide el modelo, no las reglas.");
		}
		return problemas;
	}

	private void validarSegunTipo(Criterio criterio, DefinicionFormulario formulario, List<String> problemas) {
		switch (criterio.tipo()) {
			case RATIO -> {
				comprobarOperando(criterio, criterio.numerador(), "numerador", formulario, problemas);
				comprobarOperando(criterio, criterio.denominador(), "denominador", formulario, problemas);
				if (criterio.tramos().isEmpty()) {
					problemas.add("El criterio " + criterio.id() + " no define tramos.");
				}
			}
			case UMBRAL_NUMERICO -> {
				comprobarCampo(criterio, criterio.campo(), formulario, problemas);
				if (criterio.tramos().isEmpty()) {
					problemas.add("El criterio " + criterio.id() + " no define tramos.");
				}
			}
			case MAPEO_OPCIONES -> {
				comprobarCampo(criterio, criterio.campo(), formulario, problemas);
				if (criterio.mapa().isEmpty()) {
					problemas.add("El criterio " + criterio.id() + " no define mapa de opciones.");
				} else {
					comprobarOpcionesDelMapa(criterio, formulario, problemas);
				}
			}
			case FECHA -> {
				comprobarCampo(criterio, criterio.campo(), formulario, problemas);
				if (criterio.referencia() == null) {
					problemas.add("El criterio " + criterio.id() + " no indica fecha de referencia.");
				}
			}
			case BOOLEANO -> comprobarCampo(criterio, criterio.campo(), formulario, problemas);
			case PRESENCIA -> {
				if (criterio.campos().isEmpty()) {
					problemas.add("El criterio " + criterio.id() + " no lista campos.");
				}
				criterio.campos().forEach(c -> comprobarCampo(criterio, c, formulario, problemas));
			}
		}
	}

	private void comprobarOperando(Criterio criterio, Operando operando, String lado,
	                               DefinicionFormulario formulario, List<String> problemas) {
		if (operando == null) {
			problemas.add("El criterio " + criterio.id() + " no define " + lado + ".");
			return;
		}
		if (operando.constante() != null) {
			return;
		}
		if (operando.referencia() != null) {
			if (ContextoAnuncio.REF_DISPONIBLE_DESDE.equals(operando.referencia())) {
				problemas.add("El " + lado + " de " + criterio.id() + " usa una fecha en un ratio.");
			} else if (operando.referencia().startsWith("@")
					&& new ContextoAnuncio(0, 0, null).numero(operando.referencia()) == null) {
				problemas.add("Referencia desconocida en " + criterio.id() + ": " + operando.referencia());
			}
			return;
		}
		comprobarCampo(criterio, operando.campo(), formulario, problemas);
	}

	private void comprobarCampo(Criterio criterio, String ruta, DefinicionFormulario formulario,
	                            List<String> problemas) {
		if (ruta == null || ruta.isBlank()) {
			problemas.add("El criterio " + criterio.id() + " no indica campo.");
			return;
		}
		if (ResolutorValores.campoDeRuta(formulario, ruta) == null) {
			problemas.add("El criterio " + criterio.id() + " apunta al campo " + ruta
					+ ", que no existe en el formulario. Puntuaría cero en silencio.");
		}
	}

	private void comprobarOpcionesDelMapa(Criterio criterio, DefinicionFormulario formulario,
	                                      List<String> problemas) {
		var campo = ResolutorValores.campoDeRuta(formulario, criterio.campo());
		if (campo == null || campo.opciones().isEmpty()) {
			return;
		}
		Set<String> validas = new HashSet<>();
		campo.opciones().forEach(o -> validas.add(o.valor()));
		criterio.mapa().keySet().stream()
				.filter(clave -> !validas.contains(clave))
				.forEach(clave -> problemas.add("El criterio " + criterio.id() + " mapea la opción "
						+ clave + ", que ya no existe en el campo " + criterio.campo() + "."));
	}
}
