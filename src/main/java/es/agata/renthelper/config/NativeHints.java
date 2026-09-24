package es.agata.renthelper.config;

import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import es.agata.renthelper.llm.AjusteEvaluacion;
import es.agata.renthelper.puntuacion.modelo.CriterioPuntuado;
import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

/**
 * Lo que la imagen nativa de GraalVM necesita y Spring no deduce por su cuenta.
 *
 * <p>En la imagen nativa no hay reflexión ni recursos salvo los que se declaran al compilar. El
 * AOT de Spring ya cubre los beans, las entidades y los tipos de {@code @RequestBody} y de
 * respuesta de los controladores. Lo de aquí se usa por otros caminos y, sin declarar, compila
 * bien y revienta en ejecución.
 *
 * <p>Se registra desde {@code RentHelperApplication} con {@code @ImportRuntimeHints}.
 */
public class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// Las migraciones de Flyway y los JSON del sembrador se leen del classpath. Un recurso
		// que nadie declara no se copia al binario: sin las migraciones, Flyway arrancaría
		// diciendo que la base ya está al día.
		hints.resources().registerPattern("db/migration/*.sql");
		hints.resources().registerPattern("seed/*.json");

		// El dialecto de PostgreSQL, por su constructor vacío. Normalmente Hibernate lo deduce
		// de la conexión, pero si ésta falla al arrancar (contraseña mala, Postgres aún
		// levantándose) lo instancia por reflexión, y el MissingReflectionRegistrationError
		// taparía el error de verdad.
		hints.reflection().registerType(PostgreSQLDialect.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

		BindingReflectionHintsRegistrar registrar = new BindingReflectionHintsRegistrar();
		registrar.registerReflectionHints(hints.reflection(),
				// Columnas JSONB: Hibernate las (de)serializa con Jackson, fuera de ningún
				// controlador. El registrador recorre los tipos anidados y los genéricos.
				DefinicionFormulario.class,
				DefinicionRubrica.class,
				CriterioPuntuado.class,
				// La salida estructurada del LLM: BeanOutputConverter genera el esquema JSON y
				// parsea la respuesta por reflexión.
				AjusteEvaluacion.class,
				// Las candidaturas sintéticas que lee SembradorDatos. Es un record privado, así
				// que va por nombre.
				ClassUtils.resolveClassName(
						"es.agata.renthelper.config.SembradorDatos$CandidaturaSemilla", classLoader));
	}
}
