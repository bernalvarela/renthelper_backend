package es.agata.renthelper.config;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
// Spring Boot 4 partió la autoconfiguración en módulos: esta interfaz estaba en
// org.springframework.boot.autoconfigure.orm.jpa hasta la 3.x.
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Filtrado multi-tenant automático.
 *
 * <p>Se usa la multi-tenancy por discriminador de Hibernate ({@code @TenantId} en las entidades)
 * en vez de añadir a mano un {@code WHERE tenant_id = ?} en cada consulta: Hibernate lo aplica a
 * todas las consultas y rellena la columna en los insert. Una consulta nueva no puede olvidarse
 * del filtro porque no llega a escribirlo nadie.
 */
@Component
public class ResolutorTenant implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return ContextoTenant.actual();
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return false;
	}

	@Override
	public void customize(Map<String, Object> hibernateProperties) {
		hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}
}
