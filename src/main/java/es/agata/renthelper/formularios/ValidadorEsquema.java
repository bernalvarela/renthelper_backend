package es.agata.renthelper.formularios;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.formularios.modelo.Paso;
import es.agata.renthelper.formularios.modelo.TextoLocalizado;
import es.agata.renthelper.formularios.modelo.TipoCampo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Valida el JSON del esquema al guardarlo desde el panel.
 *
 * <p>La comprobación que más se agradece es la de traducciones: sin ella acabas con
 * formularios medio traducidos que sólo se descubren cuando un candidato elige gallego.
 *
 * <p>Los mensajes van sólo en castellano: es tu panel, no el formulario del candidato.
 */
@Component
public class ValidadorEsquema {

	public List<String> validar(DefinicionFormulario definicion, List<String> idiomasExigidos) {
		List<String> problemas = new ArrayList<>();
		if (definicion == null) {
			return List.of("El esquema está vacío.");
		}
		if (definicion.version() <= 0) {
			problemas.add("La versión debe ser un entero positivo.");
		}
		if (definicion.pasos().isEmpty()) {
			problemas.add("El esquema no tiene ningún paso.");
		}
		if (definicion.pasos().size() > 6) {
			problemas.add("El esquema tiene %d pasos. Por encima de 4-5 el abandono en móvil se dispara."
					.formatted(definicion.pasos().size()));
		}

		Set<String> idsPaso = new HashSet<>();
		Set<String> idsCampo = new HashSet<>();

		for (Paso paso : definicion.pasos()) {
			if (paso.id() == null || paso.id().isBlank()) {
				problemas.add("Hay un paso sin id.");
			} else if (!idsPaso.add(paso.id())) {
				problemas.add("Id de paso duplicado: " + paso.id());
			}
			comprobarTraducciones("El título del paso " + paso.id(), paso.titulo(), idiomasExigidos, problemas, true);
			comprobarTraducciones("La descripción del paso " + paso.id(), paso.descripcion(), idiomasExigidos, problemas, false);

			Set<String> idsDelPaso = new HashSet<>();
			paso.campos().forEach(c -> idsDelPaso.add(c.id()));

			for (Campo campo : paso.campos()) {
				validarCampo(campo, idsDelPaso, idsCampo, idiomasExigidos, problemas, false);
			}
		}
		return problemas;
	}

	private void validarCampo(Campo campo, Set<String> idsHermanos, Set<String> idsGlobales,
	                          List<String> idiomas, List<String> problemas, boolean dentroDeGrupo) {
		String id = campo.id();
		if (id == null || id.isBlank()) {
			problemas.add("Hay un campo sin id.");
			return;
		}
		if (!idsGlobales.add(id) && !dentroDeGrupo) {
			problemas.add("Id de campo duplicado: " + id);
		}
		if (campo.tipo() == null) {
			problemas.add("El campo " + id + " no tiene tipo.");
			return;
		}
		comprobarTraducciones("La etiqueta del campo " + id, campo.label(), idiomas, problemas, true);
		comprobarTraducciones("La ayuda del campo " + id, campo.ayuda(), idiomas, problemas, false);

		if (campo.tipo().esSeleccion() && campo.opciones().isEmpty()) {
			problemas.add("El campo " + id + " es de selección pero no tiene opciones.");
		}
		Set<String> valores = new HashSet<>();
		campo.opciones().forEach(o -> {
			if (o.valor() == null || o.valor().isBlank()) {
				problemas.add("El campo " + id + " tiene una opción sin valor.");
			} else if (!valores.add(o.valor())) {
				problemas.add("El campo " + id + " repite el valor de opción " + o.valor() + ".");
			}
			comprobarTraducciones("La opción " + o.valor() + " del campo " + id, o.etiqueta(), idiomas, problemas, true);
		});

		for (String referencia : new String[] {campo.minReferencia(), campo.maxReferencia()}) {
			if (referencia != null && !ContextoValidacion.referenciaConocida(referencia)) {
				problemas.add("El campo " + id + " usa la referencia " + referencia
						+ ", que no existe. La única disponible es "
						+ ContextoValidacion.REF_DISPONIBLE_DESDE + ".");
			}
		}
		if (campo.minReferencia() != null && campo.tipo() != TipoCampo.FECHA) {
			problemas.add("El campo " + id + " usa `minReferencia` pero no es de tipo FECHA.");
		}

		if (campo.visibleSi() != null) {
			String referencia = campo.visibleSi().campo();
			if (!idsHermanos.contains(referencia)) {
				problemas.add("El campo " + id + " depende de " + referencia
						+ ", que no está en el mismo paso ni en el mismo grupo.");
			}
		}

		if (campo.tipo() == TipoCampo.GRUPO_REPETIBLE) {
			if (dentroDeGrupo) {
				problemas.add("El campo " + id + " anida un grupo repetible dentro de otro. No está soportado.");
				return;
			}
			if (campo.campos().isEmpty()) {
				problemas.add("El grupo repetible " + id + " no tiene subcampos.");
			}
			if (campo.repeticionesDesde() == null || !idsHermanos.contains(campo.repeticionesDesde())) {
				problemas.add("El grupo repetible " + id + " necesita un `repeticionesDesde` que apunte a un"
						+ " campo numérico del mismo paso.");
			}
			Set<String> idsDelGrupo = new HashSet<>();
			campo.campos().forEach(c -> idsDelGrupo.add(c.id()));
			for (Campo subcampo : campo.campos()) {
				validarCampo(subcampo, idsDelGrupo, new HashSet<>(), idiomas, problemas, true);
			}
		} else if (!campo.campos().isEmpty()) {
			problemas.add("El campo " + id + " define subcampos pero no es un grupo repetible.");
		}
	}

	private void comprobarTraducciones(String que, TextoLocalizado texto, List<String> idiomas,
	                                   List<String> problemas, boolean obligatorio) {
		if (texto == null || texto.vacio()) {
			if (obligatorio) {
				problemas.add(que + " está vacía.");
			}
			return;
		}
		List<String> faltan = idiomas.stream().filter(i -> !texto.idiomas().contains(i)).toList();
		if (!faltan.isEmpty()) {
			problemas.add(que + " no está traducida a: " + String.join(", ", faltan) + ".");
		}
	}
}
