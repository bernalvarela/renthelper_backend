package es.agata.renthelper.llm;

import es.agata.renthelper.formularios.modelo.Campo;
import es.agata.renthelper.formularios.modelo.Opcion;
import es.agata.renthelper.formularios.modelo.Paso;
import es.agata.renthelper.formularios.modelo.TipoCampo;
import es.agata.renthelper.puntuacion.modelo.CriterioPuntuado;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construye los dos prompts.
 *
 * <p>Dos responsabilidades que no se pueden separar de aquí:
 *
 * <ol>
 *   <li><b>Seudonimización.</b> Es el único punto por el que salen datos hacia un tercero, y no
 *       deja pasar nombre, teléfono ni correo. El modelo no los necesita para puntuar y no
 *       mejoran el resultado, así que no se envían.</li>
 *   <li><b>Antidiscriminación.</b> El formulario no recoge origen, nacionalidad, situación
 *       familiar ni discapacidad, pero pueden aparecer en el texto libre. La instrucción de
 *       ignorarlos es explícita (Ley 12/2023, LO 3/2007).</li>
 * </ol>
 */
@Component
public class ConstructorPrompt {

	/** Tipos que nunca se envían: identifican a la persona y no aportan a la puntuación. */
	private static final Set<TipoCampo> TIPOS_EXCLUIDOS =
			Set.of(TipoCampo.TELEFONO, TipoCampo.EMAIL, TipoCampo.CONSENTIMIENTO);

	private static final String IDIOMA_PROMPT = "es";

	/** Cuántos criterios del desglose se le pasan al modelo además de los que llevan cifras. */
	private static final int CRITERIOS_EN_PROMPT = 4;

	public String sistema(SolicitudEvaluacion solicitud, int ajusteMaximo) {
		String instrucciones = solicitud.rubrica() != null && solicitud.rubrica().ajusteLlm() != null
				? String.valueOf(solicitud.rubrica().ajusteLlm().instrucciones())
				: "";

		return """
				Ayudas a un propietario a hacer un primer filtrado de candidatos a alquilar una vivienda.
				Recibe más de cien solicitudes por anuncio y necesita saber a quién llamar primero.

				La puntuación objetiva (solvencia, contrato, fechas, ocupación) YA está calculada por un
				motor de reglas y te la paso hecha. Tu trabajo NO es repetirla ni recalcularla: es
				aportar el juicio cualitativo que las reglas no pueden dar, y expresarlo como un ajuste
				entre %d y +%d puntos sobre esa puntuación.

				Qué valorar:
				%s

				Reglas que no puedes saltarte:
				- NO valores ni menciones origen, nacionalidad, etnia, religión, orientación sexual,
				  identidad de género, discapacidad, estado civil ni si tienen hijos. Si algo de eso
				  aparece en el texto libre, ignóralo por completo: no puede influir en el ajuste ni
				  aparecer en la valoración. Discriminar por esos motivos en el acceso a la vivienda es
				  ilegal en España.
				- Los datos son autodeclarados y no están verificados. Si algo es dudoso, bájalo a
				  `preguntasPendientes` en vez de castigarlo en el ajuste.
				- Sobre la ocupación tienes el número de personas, las habitaciones del piso, si
				  compartirían alguna, cuántas firmarían el contrato y si ya conviven. Valora lo que
				  eso implica PARA EL ALQUILER: reparto del riesgo entre firmantes, rotación probable
				  del grupo, desgaste de la vivienda. No deduzcas ni comentes qué relación tienen
				  entre ellas. Que no firmen todas es normal —habrá menores o personas sin ingresos
				  propios— y no es un punto en contra.
				- Comprobación concreta: si hay más personas que habitaciones y responden que NO
				  compartirían ninguna, alguien dormiría fuera de un dormitorio. Ponlo en
				  `banderasRojas` con esas palabras, porque el motor de reglas no puede detectarlo.
				- Si la información es escasa, usa un ajuste próximo a cero y confianza BAJA. Inventar
				  matices con pocos datos es peor que no ajustar.
				- Cuando menciones un dato, DI SU VALOR. «Los ingresos se quedan justos» obliga a abrir
				  la ficha para saber cuánto son; «2.100 € entre los cuatro para una renta de 750 €» se
				  entiende sin salir del mensaje. Vale para ingresos, fechas, número de personas,
				  habitaciones y antigüedad: el número concreto, no el adjetivo. Los tienes todos en el
				  desglose de las reglas y en las respuestas; no inventes ninguno ni lo redondees a ojo.
				- Si en el bloque de vivienda aparece «Bonificación fiscal por edad», MENCIÓNALA en la
				  valoración, diga lo que diga, con las palabras que te llegan. Es un dato económico
				  del contrato que el propietario quiere ver sin abrir la ficha.
				  Ahora bien: NO puede mover el ajuste ni un punto, ni a favor ni en contra. Te llega
				  calculada precisamente para que no tengas que mirar edades; ordenar candidatos por
				  edad en el acceso a la vivienda es ilegal aunque la bonificación sea legal. Una cosa
				  es informar de que el contrato desgravaría y otra colocar a alguien por debajo por
				  haber nacido antes.
				- Responde SIEMPRE en castellano, aunque el candidato haya contestado en otro idioma.

				Sobre los campos de texto:
				- `resumen`: los hechos, sin juicio. Dos frases con quiénes son, de qué viven, qué
				  ingresan y cuándo entrarían. Es para hacerse una idea en cinco segundos desde el
				  móvil sin abrir la ficha. Aquí SÍ toca repetir datos: para eso está.
				- `valoracion`: el juicio, y NO un resumen —ese ya lo has escrito arriba—. En dos o
				  tres frases, di lo que los datos no dicen por sí solos: si las respuestas encajan
				  entre sí o se contradicen, qué riesgo concreto ves, qué distingue a este candidato
				  de otro con la MISMA puntuación, y por qué merece el ajuste que le das.

				  La solvencia se menciona SIEMPRE, vaya bien o mal: cuánto ingresan entre todos
				  frente a la renta, y si eso encaja con sus contratos. Es lo primero que se mira al
				  decidir, y una valoración que lo calla obliga a bajar al desglose para averiguar si
				  el problema estaba ahí. «Ingresos holgados, 2.800 € para una renta de 750 €» es tan
				  útil como decir que no llegan.

				  Mal, porque repite el resumen:
				  «Dos adultos con contrato indefinido e ingresos suficientes, plan de estancia de más
				  de 3 años y sin mascotas.»

				  Bien, porque añade juicio:
				  «Solvencia holgada: 2.800 € entre los dos para una renta de 750 €, con contratos
				  indefinidos que encajan con la antigüedad declarada, así que el riesgo de rotación
				  es bajo. Lo que desentona es que pidan entrar mucho antes de que el piso quede libre;
				  conviene aclarar si es un error al rellenarlo o si necesitan algo que no podemos darles.»

				  Si de verdad no hay nada que añadir, dilo en una línea y baja la confianza. Eso es
				  información; repetir el formulario no lo es.
				- `preguntasPendientes`: ahí va todo lo que falte por saber, en forma de pregunta
				  concreta que se pueda hacer por teléfono o en la visita. Es la lista con la que el
				  propietario llama, así que una respuesta escueta del candidato debe convertirse en
				  preguntas útiles, no en una queja.
				- `verificacionNombre`: veredicto sobre el nombre del bloque «Identidad», con estas
				  reglas por orden de importancia:
				  · Que un nombre sea infrecuente, extranjero, largo, corto o que no te resulte
				    familiar NO es motivo para marcarlo. La inmensa mayoría de los nombres del mundo
				    no son españoles y todos son igual de válidos.
				  · FICTICIO sólo si reconoces un personaje de ficción muy conocido, una figura
				    histórica célebre o un texto de relleno evidente.
				  · DUDOSO sólo si hay un indicio claro y lo puedes explicar en una frase.
				  · Ante cualquier duda, PLAUSIBLE. Un falso positivo aquí perjudica a una persona
				    real.
				  Y lo más importante: ese veredicto NO puede influir en `ajuste`, ni aparecer en
				  `resumen` ni en `valoracion`. El nombre se te da para juzgar si la solicitud es de
				  verdad, para nada más.
				""".formatted(-ajusteMaximo, ajusteMaximo,
				instrucciones.isBlank() ? "Coherencia interna y calidad de las respuestas." : instrucciones);
	}

	public String usuario(SolicitudEvaluacion solicitud) {
		StringBuilder sb = new StringBuilder();

		// El nombre va primero y en su propio bloque, para que la instrucción de usarlo sólo para
		// el veredicto tenga a qué referirse sin ambigüedad.
		if (solicitud.nombre() != null && !solicitud.nombre().isBlank()) {
			sb.append("## Identidad (sólo para `verificacionNombre`; no puntúa)\n")
					.append("- Nombre escrito en el formulario: ").append(solicitud.nombre())
					.append("\n\n");
		}

		sb.append("## Vivienda\n")
				.append("- Renta mensual: ").append(solicitud.renta()).append(" €\n");
		if (solicitud.habitaciones() != null) {
			sb.append("- Habitaciones: ").append(solicitud.habitaciones()).append('\n');
		}
		if (solicitud.disponibleDesde() != null) {
			sb.append("- Disponible desde: ").append(solicitud.disponibleDesde()).append('\n');
		}

		// Ya calculado por el motor. Al modelo se le pide que lo diga, no que lo deduzca.
		if (solicitud.bonificacion() != null && !solicitud.bonificacion().isBlank()) {
			sb.append("- Bonificación fiscal por edad: ").append(solicitud.bonificacion()).append('\n');
		}

		sb.append("\n## Respuestas del candidato\n");
		sb.append(respuestasSeudonimizadas(solicitud));

		sb.append("\n## Puntuación objetiva ya calculada (no la recalcules)\n");
		sb.append("- Total determinista: ").append(solicitud.determinista().puntuacion()).append("/100\n");
		for (CriterioPuntuado criterio : paraElPrompt(solicitud.determinista().desglose())) {
			sb.append("- ").append(criterio.etiqueta())
					.append(": ").append(criterio.puntos()).append("/100")
					.append(" (peso ").append(criterio.peso()).append(")");
			if (criterio.detalle() != null && !criterio.detalle().isBlank()) {
				sb.append(" — ").append(criterio.detalle());
			}
			sb.append('\n');
		}
		if (solicitud.determinista().noCumpleMinimos()) {
			sb.append("- Avisos de mínimos: ")
					.append(String.join("; ", solicitud.determinista().motivosMinimos()))
					.append('\n');
		}
		return sb.toString();
	}


	/**
	 * Qué criterios del desglose se le pasan al modelo.
	 *
	 * <p>Dos cosas, y en este orden:
	 *
	 * <ol>
	 *   <li><b>Los que llevan detalle.</b> Ahí están los números concretos —ingresos entre renta,
	 *       días de desfase— y ahora se le pide al modelo que los cite en el texto. Van siempre,
	 *       aunque el candidato no pierda un solo punto ahí: pedirle una cifra que no le hemos
	 *       dado es invitarle a inventársela.</li>
	 *   <li><b>Dónde flojea.</b> El resto se ordena por puntos perdidos ponderados —peso por lo
	 *       que falta para 100—, que es el daño real a la nota: un criterio de peso 30 al 50%
	 *       pesa más que uno de peso 3 a cero.</li>
	 * </ol>
	 *
	 * <p>Los once completos eran casi todo relleno: al decirle que no recalcule la nota, un
	 * criterio clavado y sin detalle no le aporta nada que no esté ya en el total.
	 */
	private List<CriterioPuntuado> paraElPrompt(List<CriterioPuntuado> desglose) {
		List<CriterioPuntuado> conDatos = desglose.stream()
				.filter(c -> c.detalle() != null && !c.detalle().isBlank())
				.toList();

		List<CriterioPuntuado> flojos = desglose.stream()
				.filter(c -> c.puntos() < 100 && !conDatos.contains(c))
				.sorted(java.util.Comparator.comparingInt(
						(CriterioPuntuado c) -> c.peso() * (100 - c.puntos())).reversed())
				.limit(CRITERIOS_EN_PROMPT)
				.toList();

		return java.util.stream.Stream.concat(conDatos.stream(), flojos.stream()).toList();
	}

	/** Renderiza las respuestas con las etiquetas del esquema, omitiendo los datos de contacto. */
	private String respuestasSeudonimizadas(SolicitudEvaluacion solicitud) {
		StringBuilder sb = new StringBuilder();
		Map<String, Object> respuestas = solicitud.respuestas();

		for (Paso paso : solicitud.formulario().pasos()) {
			StringBuilder bloque = new StringBuilder();
			for (Campo campo : paso.campos()) {
				String linea = renderizarCampo(campo, respuestas);
				if (linea != null) {
					bloque.append(linea);
				}
			}
			if (!bloque.isEmpty()) {
				sb.append("\n### ").append(paso.titulo().texto(IDIOMA_PROMPT)).append('\n').append(bloque);
			}
		}
		return sb.toString();
	}

	private String renderizarCampo(Campo campo, Map<String, Object> ambito) {
		if (TIPOS_EXCLUIDOS.contains(campo.tipo())) {
			return null;
		}
		Object valor = ambito.get(campo.id());

		if (campo.tipo() == TipoCampo.GRUPO_REPETIBLE) {
			if (!(valor instanceof List<?> elementos) || elementos.isEmpty()) {
				return null;
			}
			StringBuilder sb = new StringBuilder("- ")
					.append(campo.label().texto(IDIOMA_PROMPT)).append(":\n");
			int indice = 1;
			for (Object elemento : elementos) {
				if (!(elemento instanceof Map<?, ?> mapa)) {
					continue;
				}
				@SuppressWarnings("unchecked")
				Map<String, Object> sub = (Map<String, Object>) mapa;
				sb.append("  - Persona ").append(indice++).append(": ");
				List<String> partes = campo.campos().stream()
						.filter(c -> !TIPOS_EXCLUIDOS.contains(c.tipo()))
						.map(c -> {
							String texto = valorLegible(c, sub.get(c.id()));
							return texto == null ? null : c.label().texto(IDIOMA_PROMPT) + " " + texto;
						})
						.filter(java.util.Objects::nonNull)
						.toList();
				sb.append(String.join(", ", partes)).append('\n');
			}
			return sb.toString();
		}

		String legible = valorLegible(campo, valor);
		return legible == null ? null
				: "- " + campo.label().texto(IDIOMA_PROMPT) + ": " + legible + "\n";
	}

	private String valorLegible(Campo campo, Object valor) {
		if (valor == null || String.valueOf(valor).isBlank()) {
			return null;
		}
		if (campo.tipo() == TipoCampo.BOOLEANO) {
			return Boolean.parseBoolean(String.valueOf(valor)) ? "sí" : "no";
		}
		if (campo.tipo().esSeleccion()) {
			if (valor instanceof List<?> seleccion) {
				return seleccion.stream().map(v -> etiquetaOpcion(campo, v)).reduce((a, b) -> a + ", " + b)
						.orElse(null);
			}
			return etiquetaOpcion(campo, valor);
		}
		// Todo lo que no es booleano ni selección acaba aquí: texto libre, números y fechas. El
		// anonimizador sólo actúa sobre lo que parece un teléfono o un correo, así que un importe
		// o una fecha pasan intactos.
		return Anonimizador.limpiar(String.valueOf(valor));
	}

	private String etiquetaOpcion(Campo campo, Object valor) {
		Opcion opcion = campo.opcion(String.valueOf(valor));
		return opcion == null ? String.valueOf(valor) : opcion.etiqueta().texto(IDIOMA_PROMPT);
	}
}
