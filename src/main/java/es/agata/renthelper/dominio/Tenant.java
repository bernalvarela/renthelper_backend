package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Propietario. Hoy sólo hay uno; la tabla existe para que multi-tenant sea una pantalla, no una migración. */
@Entity
@Table(name = "tenant")
public class Tenant {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String nombre;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected Tenant() {
	}

	public Tenant(UUID id, String nombre) {
		this.id = id;
		this.nombre = nombre;
	}

	public UUID getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
