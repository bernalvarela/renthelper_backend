package es.agata.renthelper.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * El Jackson con el que Hibernate lee y escribe las columnas JSONB.
 *
 * <p>Hibernate 7 usa Jackson 2 (no el Jackson 3 de Spring Boot) y, por defecto, crea su
 * ObjectMapper con {@code findAndRegisterModules()}: registra todo módulo que encuentre en el
 * classpath. El SDK de OpenAI trae {@code jackson-module-kotlin}, y ese módulo examina los records
 * de Java con kotlin-reflect. En la JVM no se nota; en la imagen nativa kotlin-reflect necesita una
 * reflexión que nadie registra y revienta al leer cualquier columna JSON (formulario, rúbrica,
 * desglose...), con un MissingReflectionRegistrationError sobre RecordComponent.getAccessor.
 *
 * <p>Aquí se construye el mismo mapper sin el módulo de Kotlin, que para clases Java no aporta
 * nada. Los demás (fechas de java.time, Optional) se conservan tal cual.
 */
@Configuration
public class ConfiguracionJsonHibernate {

	@Bean
	HibernatePropertiesCustomizer formatoJsonSinKotlin() {
		List<Module> modulos = ObjectMapper.findModules().stream()
				.filter(modulo -> !modulo.getClass().getName().startsWith("com.fasterxml.jackson.module.kotlin."))
				.toList();
		ObjectMapper mapper = new ObjectMapper().registerModules(modulos);
		return propiedades -> propiedades.put(MappingSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(mapper));
	}
}
