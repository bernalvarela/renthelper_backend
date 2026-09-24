package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String rol = "ADMIN";

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected Usuario() {
	}

	public Usuario(UUID tenantId, String email, String passwordHash) {
		this.tenantId = tenantId;
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getRol() {
		return rol;
	}
}
