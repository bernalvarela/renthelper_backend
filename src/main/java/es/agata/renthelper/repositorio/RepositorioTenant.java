package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepositorioTenant extends JpaRepository<Tenant, UUID> {
}
