package es.agata.renthelper.llm;

import es.agata.renthelper.dominio.ProveedorLlm;
import es.agata.renthelper.repositorio.RepositorioProveedorLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Resumen de la cadena de proveedores al arrancar.
 *
 * <p>Sin esto, un proveedor mal configurado sólo se nota cuando una candidatura acaba con
 * «no hubo proveedor de LLM disponible», que no dice por qué. Aquí se ve de un vistazo y antes
 * de que entre la primera candidatura.
 */
@Configuration
public class DiagnosticoLlm {

	private static final Logger log = LoggerFactory.getLogger(DiagnosticoLlm.class);

	@Bean
	@Order(3)
	public ApplicationRunner diagnosticarProveedoresLlm(RepositorioProveedorLlm repoProveedores,
	                                                    EvaluadorLlm evaluador) {
		return args -> {
			var proveedores = repoProveedores.findAllByOrderByOrdenAscNombreAsc();
			if (proveedores.isEmpty()) {
				log.warn("Sin proveedores de LLM configurados: sólo se puntuará con las reglas.");
				return;
			}

			log.info("Cadena de proveedores de LLM ({} configurados):", proveedores.size());
			boolean algunoUtil = false;

			for (ProveedorLlm proveedor : proveedores) {
				boolean disponible = proveedor.isActivo() && evaluador.disponible(proveedor);
				String rol = proveedor.isSombra() ? "sombra" : "principal";

				if (disponible) {
					algunoUtil = algunoUtil || !proveedor.isSombra();
					log.info("  [OK]     {} ({}) · modelo {} · {} · datos reales: {} · tope {}/mes",
							proveedor.getNombre(), rol, proveedor.getModelo(), proveedor.getUrlBase(),
							proveedor.isAptoDatosReales() ? "sí" : "NO (sólo candidaturas sintéticas)",
							proveedor.getLimiteMensualLlamadas() == Integer.MAX_VALUE
									? "sin" : proveedor.getLimiteMensualLlamadas());
				} else {
					log.info("  [NO]     {} ({}) · sin clave de API, sin url o desactivado."
									+ " Se configura en el panel, en Ajustes.",
							proveedor.getNombre(), rol);
				}
			}

			if (!algunoUtil) {
				log.warn("Ningún proveedor principal disponible: las candidaturas se puntuarán sólo"
						+ " con el motor de reglas y la evaluación quedará con proveedor DETERMINISTA.");
			}
		};
	}
}
