package es.agata.renthelper.dominio;

import es.agata.renthelper.formularios.modelo.DefinicionFormulario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Versión publicada de un formulario. Una candidatura guarda a qué versión respondió,
 * así las respuestas antiguas siguen siendo interpretables cuando cambia el esquema.
 */
@Entity
@Table(name = "form_schema")
public class EsquemaFormulario {

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
	private DefinicionFormulario definicion;

	@Column(name = "publicado_en")
	private Instant publicadoEn;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected EsquemaFormulario() {
	}

	public EsquemaFormulario(String nombre, int version, DefinicionFormulario definicion) {
		this.nombre = nombre;
		this.version = version;
		this.definicion = definicion;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
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

	public DefinicionFormulario getDefinicion() {
		return definicion;
	}

	public void setDefinicion(DefinicionFormulario definicion) {
		this.definicion = definicion;
	}

	public Instant getPublicadoEn() {
		return publicadoEn;
	}

	public void publicar() {
		this.publicadoEn = Instant.now();
	}
}
