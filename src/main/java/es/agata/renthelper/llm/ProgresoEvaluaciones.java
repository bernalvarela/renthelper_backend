package es.agata.renthelper.llm;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué está haciendo ahora mismo cada evaluación en curso, para enseñarlo en su fila del panel.
 *
 * <p>Una evaluación son varios pasos que tardan segundos cada uno —las reglas, la llamada al
 * modelo, a veces un segundo intento, a veces el siguiente de la cadena porque el primero
 * falló— y desde el panel sólo se veía un «repuntuando» genérico. Aquí cada paso deja una línea
 * («Llamando a GROQ · openai/gpt-oss-120b») y la fila la enseña con los segundos que lleva.
 *
 * <p>En memoria y no en la base de datos a propósito: la evaluación corre dentro de la
 * transacción del outbox, y lo que escribiera en una tabla no sería visible hasta el commit, es
 * decir, cuando ya ha terminado. Es un único contenedor, así que no hay otro nodo que lo pierda;
 * si se reinicia, lo que estuviera en curso se reintenta y vuelve a anotar sus pasos.
 *
 * <p>La candidatura en curso va además en un {@link ThreadLocal}: el evaluador sólo recibe la
 * solicitud, no la candidatura, y así puede anotar sus pasos (el reintento de reparación) sin
 * cambiar su interfaz. El outbox procesa en un solo hilo y de una en una.
 */
@Component
public class ProgresoEvaluaciones {

	/** El paso en curso y desde cuándo, para que la fila diga cuánto lleva esperando. */
	public record Paso(String texto, Instant desde) {
	}

	private final Map<UUID, Paso> enCurso = new ConcurrentHashMap<>();
	private final ThreadLocal<UUID> actual = new ThreadLocal<>();

	public void empezar(UUID candidaturaId) {
		actual.set(candidaturaId);
		paso("Empezando la evaluación");
	}

	/** Anota el paso de la evaluación que corre en este hilo. Fuera de una evaluación no hace nada. */
	public void paso(String texto) {
		UUID candidaturaId = actual.get();
		if (candidaturaId != null) {
			enCurso.put(candidaturaId, new Paso(texto, Instant.now()));
		}
	}

	public void terminar(UUID candidaturaId) {
		enCurso.remove(candidaturaId);
		actual.remove();
	}

	/** Los pasos en curso de esas candidaturas; las que no se están evaluando no aparecen. */
	public Map<UUID, Paso> de(Collection<UUID> candidaturas) {
		Map<UUID, Paso> pasos = new HashMap<>();
		for (UUID id : candidaturas) {
			Paso paso = enCurso.get(id);
			if (paso != null) {
				pasos.put(id, paso);
			}
		}
		return pasos;
	}
}
