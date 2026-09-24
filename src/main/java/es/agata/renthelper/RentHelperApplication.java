package es.agata.renthelper;

import es.agata.renthelper.config.NativeHints;
import es.agata.renthelper.config.PropiedadesRentHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PropiedadesRentHelper.class)
@ImportRuntimeHints(NativeHints.class)
public class RentHelperApplication {

	public static void main(String[] args) {
		SpringApplication.run(RentHelperApplication.class, args);
	}
}
