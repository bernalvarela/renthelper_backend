package es.agata.renthelper.config;

import es.agata.renthelper.dominio.Usuario;
import es.agata.renthelper.repositorio.RepositorioUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza el usuario administrador desde la configuración en cada arranque.
 *
 * <p>No se siembra en Flyway a propósito: así {@code .env} es la única fuente de verdad y cambiar
 * la contraseña es reiniciar el contenedor, no escribir SQL contra producción.
 */
@Configuration
public class BootstrapAdmin {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

	/** Después de {@link SembradorDatos}: el usuario tiene clave ajena contra el tenant. */
	@Bean
	@Order(2)
	@Transactional
	public ApplicationRunner sincronizarAdmin(RepositorioUsuario repositorio, PropiedadesRentHelper propiedades,
	                                          PasswordEncoder codificador, Environment entorno) {
		return args -> {
			var admin = propiedades.admin();
			if (admin == null || admin.email() == null || (!admin.tieneHash() && !admin.tienePlano())) {
				log.warn("Sin administrador configurado: el panel quedará inaccesible.");
				return;
			}

			String hash;
			if (admin.tieneHash()) {
				hash = admin.passwordHash();
			} else {
				hash = codificador.encode(admin.password());
				if (!entorno.matchesProfiles("dev", "dev-pg")) {
					// Es la opción por defecto porque un hash BCrypt lleva `$` y Docker Compose
					// lo interpolaría, lo que impide compartir el mismo .env con Spring. El
					// secreto que protege el panel es el mismo en los dos casos: lo que importa
					// son los permisos del .env en el servidor.
					log.info("Contraseña del panel tomada en claro de la configuración y cifrada al"
							+ " arrancar. Asegúrate de que el .env del servidor no es legible por"
							+ " terceros, o usa RENTHELPER_ADMIN_PASSWORD_HASH.");
				}
			}

			repositorio.findByEmailIgnoreCase(admin.email())
					.ifPresentOrElse(existente -> {
						// Con contraseña en claro el hash cambia en cada arranque (la sal es
						// aleatoria), así que sólo se reescribe si de verdad no coincide.
						if (!codificadorCoincide(codificador, admin, existente.getPasswordHash(), hash)) {
							existente.setPasswordHash(hash);
							repositorio.save(existente);
							log.info("Contraseña del administrador actualizada desde la configuración.");
						}
					}, () -> {
						repositorio.save(new Usuario(ContextoTenant.POR_DEFECTO, admin.email(), hash));
						log.info("Administrador creado: {}", admin.email());
					});
		};
	}

	private boolean codificadorCoincide(PasswordEncoder codificador, PropiedadesRentHelper.Admin admin,
	                                    String hashGuardado, String hashCalculado) {
		return admin.tienePlano()
				? codificador.matches(admin.password(), hashGuardado)
				: hashGuardado.equals(hashCalculado);
	}
}
