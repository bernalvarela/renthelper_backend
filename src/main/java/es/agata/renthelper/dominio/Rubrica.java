package es.agata.renthelper.dominio;

import es.agata.renthelper.puntuacion.modelo.DefinicionRubrica;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Reglas de puntuación, versionadas para poder repuntuar en lote y comparar rankings. */
@Entity
@Table(name = "rubrica")
public class Rubrica {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String nombre;

	@Column(nullable = false)
	private int version;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private DefinicionRubrica definicion;

	@Column(name = "publicada_en")
	private Instant publicadaEn;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected Rubrica() {
	}

	public Rubrica(String nombre, int version, DefinicionRubrica definicion) {
		this.nombre = nombre;
		this.version = version;
		this.definicion = definicion;
	}

	public UUID getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public DefinicionRubrica getDefinicion() {
		return definicion;
	}

	public void setDefinicion(DefinicionRubrica definicion) {
		this.definicion = definicion;
	}

	public Instant getPublicadaEn() {
		return publicadaEn;
	}

	public void publicar() {
		this.publicadaEn = Instant.now();
	}
}
