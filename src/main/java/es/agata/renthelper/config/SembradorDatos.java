package es.agata.renthelper.config;

// Spring Boot 4 va con Jackson 3: el ObjectMapper autoconfigurado es `tools.jackson.databind`,
// no el `com.fasterxml.jackson.databind` de Jackson 2. Las anotaciones (@JsonIgnoreProperties,
// @JsonValue, @JsonCreator) sí siguen en com.fasterxml.jackson.annotation.
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import es.agata.renthelper.dominio.Anuncio;
import es.agata.renthelper.dominio.Candidatura;
import es.agata.renthelper.dominio.EsquemaFormulario;
import es.agata.renthelper.dominio.Rubrica;
import es.agata.renthelper.dominio.Tenant;
import es.agata.renthelper.formularios.NormalizadorTelefono;
import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import es.agata.renthelper.repositorio.RepositorioAnuncio;
import es.agata.renthelper.repositorio.RepositorioCandidatura;
import es.agata.renthelper.repositorio.RepositorioEsquemaFormulario;
import es.agata.renthelper.repositorio.RepositorioRubrica;
import es.agata.renthelper.repositorio.RepositorioTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Datos iniciales.
 *
 * <p>Estaban en una migración de Flyway y se movieron aquí por una razón concreta: el seed usaba
 * {@code jsonb}, {@code text[]} y <em>dollar quoting</em>, y nada de eso existe en H2. Mantener
 * dos juegos de SQL en paralelo —uno para Postgres y otro para local— es garantía de que se
 * desincronicen justo en lo que no se prueba.
 *
 * <p>Con el seed en Java, el mismo código puebla Postgres y H2, y las definiciones de formulario
 * y rúbrica viven en {@code resources/seed/*.json}, que es donde se pueden leer y editar.
 *
 * <p>Es idempotente: si el anuncio de ejemplo ya existe, no toca nada.
 */
@Configuration
public class SembradorDatos {

	private static final Logger log = LoggerFactory.getLogger(SembradorDatos.class);

	/**
	 * Fecha con la que se escribieron las fechas del fichero semilla.
	 *
	 * <p>Todo lo de `candidaturas-sinteticas.json` está fijado contra esta referencia; al sembrar
	 * se desplaza en bloque para que no envejezca.
	 */
	private static final LocalDate REFERENCIA_SEMILLA = LocalDate.of(2026, 1, 1);

	/** El piso demo queda libre dentro de un mes: da margen a las visitas sin parecer lejano. */
	private static final int DIAS_HASTA_DISPONIBLE = 30;

	private static final Pattern ISO_FECHA = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	private static final String SLUG_DEMO = "demo2026";

	/** Antes que {@link BootstrapAdmin}: el usuario tiene clave ajena contra el tenant. */
	@Bean
	@Order(1)
	@Transactional
	public ApplicationRunner sembrarDatosIniciales(RepositorioTenant repoTenants,
	                                               RepositorioEsquemaFormulario repoEsquemas,
	                                               RepositorioRubrica repoRubricas,
	                                               RepositorioAnuncio repoAnuncios,
	                                               RepositorioCandidatura repoCandidaturas,
	                                               ObjectMapper mapper) {
		return args -> {
			if (repoTenants.findById(ContextoTenant.POR_DEFECTO).isEmpty()) {
				repoTenants.save(new Tenant(ContextoTenant.POR_DEFECTO, "Principal"));
			}
			if (repoAnuncios.existsBySlug(SLUG_DEMO)) {
				return;
			}

			DefinicionFormulario definicionFormulario =
					leer(mapper, "seed/formulario-estandar.json", DefinicionFormulario.class);
			DefinicionRubrica definicionRubrica =
					leer(mapper, "seed/rubrica-estandar.json", DefinicionRubrica.class);

			EsquemaFormulario esquema = new EsquemaFormulario(
					"Formulario estándar de alquiler", definicionFormulario.version(), definicionFormulario);
			esquema.publicar();
			repoEsquemas.save(esquema);

			Rubrica rubrica = new Rubrica("Rúbrica estándar", definicionRubrica.version(), definicionRubrica);
			rubrica.publicar();
			repoRubricas.save(rubrica);

			Anuncio anuncio = new Anuncio(SLUG_DEMO, "Piso de 3 habitaciones, exterior y tranquilo",
					new BigDecimal("750.00"), esquema.getId(), rubrica.getId());
			anuncio.setDireccion("A Coruña");
			anuncio.setHabitaciones(3);
			anuncio.setDisponibleDesde(LocalDate.now().plusDays(DIAS_HASTA_DISPONIBLE));
			anuncio.setIdiomas(List.of("es", "gl"));
			anuncio.setIdiomaPorDefecto("es");
			anuncio.setUmbralAlerta(80);
			repoAnuncios.save(anuncio);

			sembrarSinteticas(mapper, repoCandidaturas, anuncio, esquema, definicionFormulario);
			log.info("Datos iniciales creados. Formulario de ejemplo en /c/{}", SLUG_DEMO);
		};
	}

	/**
	 * Candidaturas de prueba con tu nota manual puesta.
	 *
	 * <p>Son el árbitro de la comparación de modelos: sin un criterio contra el que medir, la
	 * correlación de Spearman no significa nada. Y al ser datos inventados se pueden machacar
	 * contra el tier gratuito de Gemini, que no es apto para datos reales de candidatos.
	 */
	private void sembrarSinteticas(ObjectMapper mapper, RepositorioCandidatura repoCandidaturas,
	                               Anuncio anuncio, EsquemaFormulario esquema,
	                               DefinicionFormulario definicion) {
		List<CandidaturaSemilla> semillas = leerLista(mapper, "seed/candidaturas-sinteticas.json");
		long dias = ChronoUnit.DAYS.between(REFERENCIA_SEMILLA, anuncio.getDisponibleDesde());

		for (CandidaturaSemilla semilla : semillas) {
			Candidatura candidatura = new Candidatura(anuncio.getId(), esquema.getId(),
					semilla.token(), semilla.nombre(), semilla.idioma());
			Map<String, Object> respuestas = desplazarFechas(semilla.respuestas(), dias);
			candidatura.sembrarComoEnviada(respuestas, semilla.puntuacionManual(),
					definicion.numeroPasos());

			Object telefono = respuestas.get("telefono");
			if (telefono != null) {
				candidatura.setTelefonoNormalizado(NormalizadorTelefono.normalizar(String.valueOf(telefono)));
			}
			Object email = respuestas.get("email");
			if (email != null) {
				candidatura.setEmail(String.valueOf(email));
			}
			repoCandidaturas.save(candidatura);
		}
	}

	/**
	 * Desplaza en bloque las fechas de la semilla para que sigan teniendo sentido.
	 *
	 * <p>Estaban fijas en enero de 2026 y envejecieron: llegó el día en que las candidaturas de
	 * prueba pedían entrar nueve meses ANTES de que el piso quedase libre, y el modelo —con toda
	 * la razón— lo señalaba como contradicción insalvable. Unos datos de ejemplo que caducan
	 * hacen perder el tiempo persiguiendo un fallo que no existe.
	 *
	 * <p>Se aplica el MISMO desplazamiento a todas, de modo que se conservan las distancias
	 * relativas que cada caso quería ilustrar: quien entra el primer día, quien tarda tres meses,
	 * quien pide ver el piso antes de que se libere.
	 *
	 * <p>Recorre también listas y mapas anidados: las fechas de hoy están en el primer nivel,
	 * pero el formulario es configurable y mañana puede haber una dentro de un grupo repetible.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> desplazarFechas(Map<String, Object> respuestas, long dias) {
		return (Map<String, Object>) desplazar(respuestas, dias);
	}

	private Object desplazar(Object valor, long dias) {
		if (valor instanceof Map<?, ?> mapa) {
			Map<String, Object> copia = new LinkedHashMap<>();
			mapa.forEach((clave, v) -> copia.put(String.valueOf(clave), desplazar(v, dias)));
			return copia;
		}
		if (valor instanceof List<?> lista) {
			return lista.stream().map(v -> desplazar(v, dias)).toList();
		}
		if (valor instanceof String texto && ISO_FECHA.matcher(texto).matches()) {
			return LocalDate.parse(texto).plusDays(dias).toString();
		}
		return valor;
	}

	private record CandidaturaSemilla(String token, String nombre, String idioma,
	                                  Integer puntuacionManual, Map<String, Object> respuestas) {
	}

	private <T> T leer(ObjectMapper mapper, String ruta, Class<T> tipo) {
		try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
			return mapper.readValue(entrada, tipo);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer " + ruta, e);
		}
	}

	private List<CandidaturaSemilla> leerLista(ObjectMapper mapper, String ruta) {
		try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
			return mapper.readValue(entrada, new TypeReference<List<CandidaturaSemilla>>() {
			});
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer " + ruta, e);
		}
	}
}
