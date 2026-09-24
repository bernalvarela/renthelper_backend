package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "anuncio")
public class Anuncio {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	/** Lo que se pega en idealista. Corto y legible, no un UUID. */
	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private String titulo;

	private String direccion;

	@Column(name = "renta_mensual", nullable = false)
	private BigDecimal rentaMensual;

	@Column(name = "habitaciones")
	private Integer habitaciones;

	@Column(name = "disponible_desde")
	private LocalDate disponibleDesde;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> idiomas = List.of("es");

	@Column(name = "idioma_por_defecto", nullable = false)
	private String idiomaPorDefecto = "es";

	@Column(name = "form_schema_id", nullable = false)
	private UUID formSchemaId;

	@Column(name = "rubrica_id", nullable = false)
	private UUID rubricaId;

	/**
	 * Interruptor de alta. Apagarlo evita tener que despublicar el anuncio en idealista
	 * cuando te saturas: los que ya empezaron el formulario pueden terminarlo.
	 */
	@Column(name = "aceptando_candidaturas", nullable = false)
	private boolean aceptandoCandidaturas = true;

	/** Por encima de este total se manda alerta inmediata; el resto espera al digest. */
	@Column(name = "umbral_alerta", nullable = false)
	private int umbralAlerta = 80;

	@Column(name = "ultimo_digest_en")
	private Instant ultimoDigestEn;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected Anuncio() {
	}

	public Anuncio(String slug, String titulo, BigDecimal rentaMensual, UUID formSchemaId, UUID rubricaId) {
		this.slug = slug;
		this.titulo = titulo;
		this.rentaMensual = rentaMensual;
		this.formSchemaId = formSchemaId;
		this.rubricaId = rubricaId;
	}

	public boolean soportaIdioma(String idioma) {
		return idioma != null && idiomas.contains(idioma);
	}

	public String idiomaValido(String solicitado) {
		return soportaIdioma(solicitado) ? solicitado : idiomaPorDefecto;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getSlug() {
		return slug;
	}

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getDireccion() {
		return direccion;
	}

	public void setDireccion(String direccion) {
		this.direccion = direccion;
	}

	public BigDecimal getRentaMensual() {
		return rentaMensual;
	}

	public void setRentaMensual(BigDecimal rentaMensual) {
		this.rentaMensual = rentaMensual;
	}

	public Integer getHabitaciones() {
		return habitaciones;
	}

	public void setHabitaciones(Integer habitaciones) {
		this.habitaciones = habitaciones;
	}

	public LocalDate getDisponibleDesde() {
		return disponibleDesde;
	}

	public void setDisponibleDesde(LocalDate disponibleDesde) {
		this.disponibleDesde = disponibleDesde;
	}

	public List<String> getIdiomas() {
		return idiomas;
	}

	public void setIdiomas(List<String> idiomas) {
		this.idiomas = idiomas;
	}

	public String getIdiomaPorDefecto() {
		return idiomaPorDefecto;
	}

	public void setIdiomaPorDefecto(String idiomaPorDefecto) {
		this.idiomaPorDefecto = idiomaPorDefecto;
	}

	public UUID getFormSchemaId() {
		return formSchemaId;
	}

	public void setFormSchemaId(UUID formSchemaId) {
		this.formSchemaId = formSchemaId;
	}

	public UUID getRubricaId() {
		return rubricaId;
	}

	public void setRubricaId(UUID rubricaId) {
		this.rubricaId = rubricaId;
	}

	public boolean isAceptandoCandidaturas() {
		return aceptandoCandidaturas;
	}

	public void setAceptandoCandidaturas(boolean aceptandoCandidaturas) {
		this.aceptandoCandidaturas = aceptandoCandidaturas;
	}

	public int getUmbralAlerta() {
		return umbralAlerta;
	}

	public void setUmbralAlerta(int umbralAlerta) {
		this.umbralAlerta = umbralAlerta;
	}

	public Instant getUltimoDigestEn() {
		return ultimoDigestEn;
	}

	public void setUltimoDigestEn(Instant ultimoDigestEn) {
		this.ultimoDigestEn = ultimoDigestEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
