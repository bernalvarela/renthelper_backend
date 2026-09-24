package es.agata.renthelper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enruta las dos SPAs.
 *
 * <p>Son dos bundles distintos a propósito: el panel lleva router, tablas y gráficas, y nada de
 * eso debe descargarse en el móvil de un candidato con 4G. El peso de la primera pantalla decide
 * cuántos de los 150 empiezan el formulario.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registro) {
		// Formulario del candidato: /c/{slug} y /c/{slug}/{token}
		registro.addViewController("/c/{slug}").setViewName("forward:/index.html");
		registro.addViewController("/c/{slug}/{token}").setViewName("forward:/index.html");

		// Panel.
		registro.addViewController("/admin").setViewName("forward:/admin.html");
		registro.addViewController("/admin/**").setViewName("forward:/admin.html");
	}
}
