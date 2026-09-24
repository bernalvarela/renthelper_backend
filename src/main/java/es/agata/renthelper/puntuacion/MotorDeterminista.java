package es.agata.renthelper.puntuacion;

import es.agata.renthelper.formularios.ResolutorValores;
import es.agata.renthelper.formularios.ValidadorRespuestas;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.puntuacion.modelo.Criterio;
import es.agata.renthelper.puntuacion.modelo.CriterioPuntuado;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import es.agata.renthelper.puntuacion.modelo.Operando;
import es.agata.renthelper.puntuacion.modelo.ResultadoDeterminista;
import es.agata.renthelper.puntuacion.modelo.Tramo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Puntuación determinista.
 *
 * <p>Es la parte que ordena el ranking, y está deliberadamente separada del LLM: es
 * reproducible, se testea con JUnit, no cuesta dinero y se puede explicar a un candidato que
 * pregunte. El modelo sólo añade después un ajuste acotado sobre lo cualitativo.
 *
 * <p>Con 150 candidaturas por anuncio, un ranking que cambia entre ejecuciones es inservible.
 */
@Component
public class MotorDeterminista {

	/** Margen por defecto de decaimiento, si la rúbrica no lo fija. */
	private static final int DIAS_DECAIMIENTO_POR_DEFECTO = 90;

	public ResultadoDeterminista puntuar(DefinicionRubrica rubrica, DefinicionFormulario formulario,
	                                     Map<String, Object> respuestas, ContextoAnuncio anuncio) {
		if (rubrica == null || rubrica.criterios().isEmpty()) {
			return ResultadoDeterminista.vacio();
		}
		int sumaPesos = rubrica.sumaPesos();
		if (sumaPesos <= 0) {
			return ResultadoDeterminista.vacio();
		}

		List<CriterioPuntuado> desglose = new ArrayList<>();
		Map<String, Double> brutosPorCriterio = new LinkedHashMap<>();
		double acumulado = 0d;

		for (Criterio criterio : rubrica.criterios()) {
			CriterioPuntuado puntuado = evaluar(criterio, formulario, respuestas, anuncio);
			desglose.add(puntuado);
			brutosPorCriterio.put(criterio.id(), puntuado.valorBruto());
			acumulado += puntuado.puntosPonderados();
		}

		// Cada criterio puntúa de 0 a 100 y aporta según su peso: la media ponderada ya está en
		// esa escala, así que hay que dividir entre 100 antes de llevarla al máximo de la rúbrica.
		double mediaPonderada = acumulado / sumaPesos;
		int total = (int) Math.round(mediaPonderada / 100d * rubrica.maximoODefecto());
		total = Math.clamp(total, 0, rubrica.maximoODefecto());

		List<String> motivos = comprobarMinimos(rubrica, brutosPorCriterio);
		return new ResultadoDeterminista(total, desglose, !motivos.isEmpty(), motivos);
	}

	private List<String> comprobarMinimos(DefinicionRubrica rubrica, Map<String, Double> brutos) {
		List<String> motivos = new ArrayList<>();
		for (DefinicionRubrica.Minimo minimo : rubrica.minimos()) {
			Double bruto = brutos.get(minimo.criterioId());
			if (bruto == null || minimo.operador() == null) {
				continue;
			}
			if (minimo.operador().incumple(bruto, minimo.valor())) {
				motivos.add(minimo.mensaje() == null ? minimo.criterioId() : minimo.mensaje());
			}
		}
		return motivos;
	}

	private CriterioPuntuado evaluar(Criterio criterio, DefinicionFormulario formulario,
	                                 Map<String, Object> respuestas, ContextoAnuncio anuncio) {
		return switch (criterio.tipo()) {
			case RATIO -> ratio(criterio, formulario, respuestas, anuncio);
			case MAPEO_OPCIONES -> mapeoOpciones(criterio, respuestas);
			case UMBRAL_NUMERICO -> umbralNumerico(criterio, respuestas);
			case FECHA -> fecha(criterio, respuestas, anuncio);
			case BOOLEANO -> booleano(criterio, respuestas);
			case PRESENCIA -> presencia(criterio, respuestas);
		};
	}

	// --- RATIO -------------------------------------------------------------------------

	private CriterioPuntuado ratio(Criterio criterio, DefinicionFormulario formulario,
	                               Map<String, Object> respuestas, ContextoAnuncio anuncio) {
		double arriba = resolverOperando(criterio.numerador(), formulario, respuestas, anuncio);
		double abajo = resolverOperando(criterio.denominador(), formulario, respuestas, anuncio);
		if (abajo == 0d) {
			return construir(criterio, 0d, 0, "Sin denominador: no se puede calcular el ratio");
		}
		double valor = arriba / abajo;
		int puntos = puntosPorTramos(criterio.tramos(), valor);
		String detalle = "%s / %s = %s".formatted(formatea(arriba), formatea(abajo), formatea(valor));
		return construir(criterio, valor, puntos, detalle);
	}

	private double resolverOperando(Operando operando, DefinicionFormulario formulario,
	                                Map<String, Object> respuestas, ContextoAnuncio anuncio) {
		if (operando == null) {
			return 0d;
		}
		if (operando.constante() != null) {
			return operando.constante();
		}
		if (operando.referencia() != null) {
			Double referencia = anuncio.numero(operando.referencia());
			return referencia == null ? 0d : referencia;
		}
		if (operando.campo() == null) {
			return 0d;
		}
		List<Double> valores = operando.usaPesos()
				? ResolutorValores.pesos(formulario, respuestas, operando.campo())
				: ResolutorValores.numeros(respuestas, operando.campo());
		return operando.agregacionODefecto().aplicar(valores);
	}

	// --- MAPEO_OPCIONES ----------------------------------------------------------------

	private CriterioPuntuado mapeoOpciones(Criterio criterio, Map<String, Object> respuestas) {
		List<Object> valores = ResolutorValores.valores(respuestas, criterio.campo());
		List<Double> puntuados = new ArrayList<>();
		for (Object valor : valores) {
			Integer puntos = criterio.mapa().get(String.valueOf(valor));
			if (puntos != null) {
				puntuados.add(puntos.doubleValue());
			}
		}
		if (puntuados.isEmpty()) {
			// Nadie con contrato mapeable (todo estudiantes, autónomos...): no es un cero, es
			// una ausencia de información. `puntosSiVacio` evita castigarla como si fuese lo peor.
			int porDefecto = criterio.puntosSiVacio() == null ? 0 : criterio.puntosSiVacio();
			return construir(criterio, porDefecto, porDefecto, "Sin valores puntuables");
		}
		double puntos = criterio.agregacionODefecto().aplicar(puntuados);
		int redondeado = Math.clamp((int) Math.round(puntos), 0, 100);
		return construir(criterio, redondeado, redondeado,
				"%d valores, agregación %s".formatted(puntuados.size(), criterio.agregacionODefecto()));
	}

	// --- UMBRAL_NUMERICO ---------------------------------------------------------------

	private CriterioPuntuado umbralNumerico(Criterio criterio, Map<String, Object> respuestas) {
		List<Double> numeros = ResolutorValores.numeros(respuestas, criterio.campo());
		if (numeros.isEmpty()) {
			int porDefecto = criterio.puntosSiVacio() == null ? 0 : criterio.puntosSiVacio();
			return construir(criterio, 0d, porDefecto, "Sin dato");
		}
		double valor = criterio.agregacionODefecto().aplicar(numeros);
		return construir(criterio, valor, puntosPorTramos(criterio.tramos(), valor), formatea(valor));
	}

	// --- FECHA -------------------------------------------------------------------------

	private CriterioPuntuado fecha(Criterio criterio, Map<String, Object> respuestas, ContextoAnuncio anuncio) {
		LocalDate referencia = anuncio.fecha(criterio.referencia());
		LocalDate respondida = aFecha(ResolutorValores.valor(respuestas, criterio.campo()));
		if (referencia == null || respondida == null) {
			int porDefecto = criterio.puntosSiVacio() == null ? 50 : criterio.puntosSiVacio();
			return construir(criterio, 0d, porDefecto, "Sin fecha comparable");
		}
		long desfase = ChronoUnit.DAYS.between(referencia, respondida);
		int tolerancia = criterio.toleranciaDias() == null ? 30 : criterio.toleranciaDias();
		int dentro = criterio.puntosDentroTolerancia() == null ? 100 : criterio.puntosDentroTolerancia();
		int fuera = criterio.puntosFuera() == null ? 30 : criterio.puntosFuera();

		int decaimiento = criterio.diasDecaimiento() == null
				? DIAS_DECAIMIENTO_POR_DEFECTO
				: criterio.diasDecaimiento();

		int puntos;
		if (desfase < 0 && Boolean.TRUE.equals(criterio.penalizarAnterior())) {
			// Red de seguridad. El formulario ya impide elegir una fecha anterior a la de
			// disponibilidad (`minReferencia`), así que esto sólo salta con datos viejos o si
			// se cambia la fecha del anuncio después de recibir candidaturas.
			puntos = fuera;
		} else if (desfase <= tolerancia) {
			// Entrar el primer día o poco después es lo mejor posible: la tolerancia es una
			// meseta de puntuación máxima, no un margen de gracia.
			puntos = dentro;
		} else {
			// A partir de ahí baja de forma continua: cuanto más tarde, menos puntos, hasta
			// el suelo. Sin escalones, para que dos fechas parecidas no puntúen muy distinto.
			long exceso = desfase - tolerancia;
			double factor = Math.max(0d, 1d - (double) exceso / decaimiento);
			puntos = (int) Math.round(fuera + (dentro - fuera) * factor);
		}
		// Redactado y no "%+d días": el signo a secas lo malinterpreta tanto quien lee el panel
		// como el modelo, que llegó a describir un retraso como una entrada anticipada.
		String detalle = desfase == 0
				? "entra el mismo día en que queda libre"
				: desfase > 0
						? "entra %d días después de quedar libre".formatted(desfase)
						: "pide entrar %d días ANTES de quedar libre".formatted(-desfase);
		return construir(criterio, desfase, Math.clamp(puntos, 0, 100), detalle);
	}

	// --- BOOLEANO ----------------------------------------------------------------------

	private CriterioPuntuado booleano(Criterio criterio, Map<String, Object> respuestas) {
		Boolean valor = ValidadorRespuestas.aBooleano(ResolutorValores.valor(respuestas, criterio.campo()));
		if (valor == null) {
			int porDefecto = criterio.puntosSiVacio() == null ? 50 : criterio.puntosSiVacio();
			return construir(criterio, 0d, porDefecto, "Sin respuesta");
		}
		int puntos = valor
				? (criterio.puntosSi() == null ? 0 : criterio.puntosSi())
				: (criterio.puntosNo() == null ? 100 : criterio.puntosNo());
		return construir(criterio, valor ? 1d : 0d, Math.clamp(puntos, 0, 100), valor ? "Sí" : "No");
	}

	// --- PRESENCIA ---------------------------------------------------------------------

	private CriterioPuntuado presencia(Criterio criterio, Map<String, Object> respuestas) {
		if (criterio.campos().isEmpty()) {
			return construir(criterio, 0d, 0, "Sin campos declarados");
		}
		int minimo = criterio.longitudMinimaTexto() == null ? 40 : criterio.longitudMinimaTexto();
		double acumulado = 0d;
		int rellenos = 0;
		for (String campo : criterio.campos()) {
			Object valor = ResolutorValores.valor(respuestas, campo);
			if (valor == null || String.valueOf(valor).isBlank()) {
				continue;
			}
			rellenos++;
			// Media respuesta cuenta la mitad: "ok" no es contestar, pero tampoco es no contestar.
			acumulado += String.valueOf(valor).trim().length() >= minimo ? 1d : 0.5d;
		}
		double fraccion = acumulado / criterio.campos().size();
		int puntos = (int) Math.round(fraccion * 100);
		return construir(criterio, fraccion, Math.clamp(puntos, 0, 100),
				"%d de %d campos".formatted(rellenos, criterio.campos().size()));
	}

	// --- Utilidades --------------------------------------------------------------------

	private int puntosPorTramos(List<Tramo> tramos, double valor) {
		for (Tramo tramo : tramos) {
			if (tramo.contiene(valor)) {
				return Math.clamp(tramo.puntos(), 0, 100);
			}
		}
		return 0;
	}

	private CriterioPuntuado construir(Criterio criterio, double valorBruto, int puntos, String detalle) {
		return new CriterioPuntuado(
				criterio.id(),
				criterio.etiquetaOId(),
				criterio.peso(),
				valorBruto,
				puntos,
				puntos * (double) criterio.peso(),
				detalle);
	}

	private static LocalDate aFecha(Object valor) {
		if (valor == null) {
			return null;
		}
		try {
			return LocalDate.parse(String.valueOf(valor));
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String formatea(double valor) {
		return String.format(Locale.forLanguageTag("es-ES"), "%.2f", valor);
	}
}
