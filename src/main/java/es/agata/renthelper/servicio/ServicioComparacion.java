package es.agata.renthelper.servicio;

import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.Evaluacion;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEvaluacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comparación de modelos.
 *
 * <p>Una tabla de puntuaciones por proveedor no dice gran cosa. Dos vistas sí:
 *
 * <ul>
 *   <li><b>Correlación de Spearman</b> entre el ranking de cada modelo y tu puntuación manual.
 *       Un número por modelo: con eso se decide en treinta segundos.</li>
 *   <li><b>Desacuerdos</b>: las candidaturas donde dos modelos difieren más. Ahí es donde se ve
 *       cuál ha entendido la rúbrica y cuál se la ha inventado.</li>
 * </ul>
 *
 * <p>Sin árbitro la comparación no significa nada, y el árbitro es tu nota manual: por eso el
 * seed trae candidaturas sintéticas ya puntuadas a mano.
 */
@Service
public class ServicioComparacion {

	/** Por debajo de esto, la correlación es ruido. */
	private static final int MINIMO_PARA_CORRELACION = 5;
	private static final int UMBRAL_DESACUERDO = 10;

	private final RepositorioEvaluacion repoEvaluaciones;
	private final RepositorioCandidatura repoCandidaturas;

	public ServicioComparacion(RepositorioEvaluacion repoEvaluaciones, RepositorioCandidatura repoCandidaturas) {
		this.repoEvaluaciones = repoEvaluaciones;
		this.repoCandidaturas = repoCandidaturas;
	}

	public record ResumenProveedor(
			String proveedor,
			String modelo,
			int evaluaciones,
			int fallos,
			Double correlacionSpearman,
			Double ajusteMedio,
			Integer latenciaMediaMs) {
	}

	public record Desacuerdo(
			UUID candidaturaId,
			String nombre,
			Map<String, Integer> puntuaciones,
			int diferenciaMaxima) {
	}

	public record Comparacion(
			List<ResumenProveedor> proveedores,
			List<Desacuerdo> desacuerdos,
			int candidaturasConNotaManual,
			String aviso) {
	}

	@Transactional(readOnly = true)
	public Comparacion comparar(UUID anuncioId) {
		List<Candidatura> candidaturas = repoCandidaturas.paraTriaje(anuncioId, false);
		Map<UUID, Candidatura> porId = new LinkedHashMap<>();
		candidaturas.forEach(c -> porId.put(c.getId(), c));

		// Una evaluación por candidatura y proveedor: la más reciente.
		Map<String, Map<UUID, Evaluacion>> porProveedor = new LinkedHashMap<>();
		for (Evaluacion evaluacion : repoEvaluaciones.porAnuncio(anuncioId)) {
			if (!porId.containsKey(evaluacion.getCandidaturaId())) {
				continue;
			}
			porProveedor
					.computeIfAbsent(evaluacion.getProveedor(), clave -> new LinkedHashMap<>())
					.putIfAbsent(evaluacion.getCandidaturaId(), evaluacion);
		}

		long conNotaManual = candidaturas.stream().filter(c -> c.getPuntuacionManual() != null).count();

		List<ResumenProveedor> resumenes = new ArrayList<>();
		porProveedor.forEach((proveedor, evaluaciones) ->
				resumenes.add(resumir(proveedor, evaluaciones, porId)));
		resumenes.sort(Comparator.comparing(
				(ResumenProveedor r) -> r.correlacionSpearman() == null ? -2d : r.correlacionSpearman())
				.reversed());

		String aviso = conNotaManual < MINIMO_PARA_CORRELACION
				? "Hacen falta al menos %d candidaturas con nota manual para que la correlación signifique algo. Ahora hay %d."
						.formatted(MINIMO_PARA_CORRELACION, conNotaManual)
				: null;

		return new Comparacion(resumenes, desacuerdos(porProveedor, porId), (int) conNotaManual, aviso);
	}

	private ResumenProveedor resumir(String proveedor, Map<UUID, Evaluacion> evaluaciones,
	                                 Map<UUID, Candidatura> candidaturas) {
		List<Double> delModelo = new ArrayList<>();
		List<Double> manuales = new ArrayList<>();
		double sumaAjustes = 0;
		long sumaLatencias = 0;
		int conLatencia = 0;
		int fallos = 0;

		for (Evaluacion evaluacion : evaluaciones.values()) {
			if (evaluacion.getError() != null) {
				fallos++;
			}
			sumaAjustes += evaluacion.getAjusteLlm();
			if (evaluacion.getLatenciaMs() != null) {
				sumaLatencias += evaluacion.getLatenciaMs();
				conLatencia++;
			}
			Candidatura candidatura = candidaturas.get(evaluacion.getCandidaturaId());
			if (candidatura != null && candidatura.getPuntuacionManual() != null) {
				delModelo.add((double) evaluacion.getPuntuacionTotal());
				manuales.add((double) candidatura.getPuntuacionManual());
			}
		}

		Double correlacion = delModelo.size() >= MINIMO_PARA_CORRELACION
				? spearman(delModelo, manuales)
				: null;
		String modelo = evaluaciones.values().stream()
				.map(Evaluacion::getModelo).filter(java.util.Objects::nonNull).findFirst().orElse(null);

		return new ResumenProveedor(proveedor, modelo, evaluaciones.size(), fallos, correlacion,
				evaluaciones.isEmpty() ? null : sumaAjustes / evaluaciones.size(),
				conLatencia == 0 ? null : (int) (sumaLatencias / conLatencia));
	}

	private List<Desacuerdo> desacuerdos(Map<String, Map<UUID, Evaluacion>> porProveedor,
	                                     Map<UUID, Candidatura> candidaturas) {
		if (porProveedor.size() < 2) {
			return List.of();
		}
		List<Desacuerdo> resultado = new ArrayList<>();
		for (Map.Entry<UUID, Candidatura> entrada : candidaturas.entrySet()) {
			Map<String, Integer> puntuaciones = new LinkedHashMap<>();
			porProveedor.forEach((proveedor, evaluaciones) -> {
				Evaluacion evaluacion = evaluaciones.get(entrada.getKey());
				if (evaluacion != null && evaluacion.getError() == null) {
					puntuaciones.put(proveedor, evaluacion.getPuntuacionTotal());
				}
			});
			if (puntuaciones.size() < 2) {
				continue;
			}
			int maximo = puntuaciones.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			int minimo = puntuaciones.values().stream().mapToInt(Integer::intValue).min().orElse(0);
			if (maximo - minimo >= UMBRAL_DESACUERDO) {
				resultado.add(new Desacuerdo(entrada.getKey(), entrada.getValue().getNombre(),
						puntuaciones, maximo - minimo));
			}
		}
		resultado.sort(Comparator.comparingInt(Desacuerdo::diferenciaMaxima).reversed());
		return resultado;
	}

	/** Pearson sobre los rangos, con rangos promediados en los empates. */
	static Double spearman(List<Double> a, List<Double> b) {
		if (a.size() != b.size() || a.size() < 2) {
			return null;
		}
		double[] rangosA = rangos(a);
		double[] rangosB = rangos(b);

		double mediaA = media(rangosA);
		double mediaB = media(rangosB);
		double covarianza = 0, varianzaA = 0, varianzaB = 0;
		for (int i = 0; i < rangosA.length; i++) {
			double da = rangosA[i] - mediaA;
			double db = rangosB[i] - mediaB;
			covarianza += da * db;
			varianzaA += da * da;
			varianzaB += db * db;
		}
		if (varianzaA == 0 || varianzaB == 0) {
			// Todos empatados en un lado: la correlación no está definida.
			return null;
		}
		return covarianza / Math.sqrt(varianzaA * varianzaB);
	}

	private static double[] rangos(List<Double> valores) {
		Integer[] indices = new Integer[valores.size()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = i;
		}
		java.util.Arrays.sort(indices, Comparator.comparingDouble(valores::get));

		double[] rangos = new double[valores.size()];
		int i = 0;
		while (i < indices.length) {
			int j = i;
			while (j + 1 < indices.length
					&& valores.get(indices[j + 1]).equals(valores.get(indices[i]))) {
				j++;
			}
			double rangoMedio = (i + j) / 2.0 + 1;
			for (int k = i; k <= j; k++) {
				rangos[indices[k]] = rangoMedio;
			}
			i = j + 1;
		}
		return rangos;
	}

	private static double media(double[] valores) {
		double suma = 0;
		for (double valor : valores) {
			suma += valor;
		}
		return suma / valores.length;
	}
}
