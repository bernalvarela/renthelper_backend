package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "candidatura")
public class Candidatura {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "anuncio_id", nullable = false)
	private UUID anuncioId;

	/** Versión del esquema que rellenó. Sin esto, cambiar el formulario rompe el histórico. */
	@Column(name = "form_schema_id", nullable = false)
	private UUID formSchemaId;

	/**
	 * Va en la URL, no sólo en cookie: el enlace se abre dentro del navegador embebido de
	 * idealista, donde las cookies pueden no sobrevivir al cierre de la app.
	 */
	@Column(nullable = false, unique = true)
	private String token;

	@Column(nullable = false)
	private String nombre;

	@Column(name = "telefono_normalizado")
	private String telefonoNormalizado;

	private String email;

	@Column(nullable = false)
	private String idioma = "es";

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoCandidatura estado = EstadoCandidatura.BORRADOR;

	@Column(name = "paso_actual", nullable = false)
	private int pasoActual;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> respuestas = new LinkedHashMap<>();

	/** Marca, no descarte: se ordena al fondo pero se evalúa y se muestra igual. */
	@Column(name = "no_cumple_minimos", nullable = false)
	private boolean noCumpleMinimos;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "motivos_minimos")
	private List<String> motivosMinimos = new ArrayList<>();

	private Integer puntuacion;

	/** De prueba. Los proveedores no aptos para datos reales sólo evalúan estas. */
	@Column(nullable = false)
	private boolean sintetica;

	/** Tu nota en el triaje manual. Es el árbitro para comparar modelos. */
	@Column(name = "puntuacion_manual")
	private Integer puntuacionManual;

	/**
	 * Si el nombre parece real o inventado. Es una marca informativa, nunca puntúa: un nombre
	 * infrecuente no es un mal inquilino, y convertir «me suena raro» en puntos es el camino
	 * directo a penalizar nombres extranjeros.
	 */
	@Column(name = "verificacion_nombre")
	private String verificacionNombre;

	@Column(name = "motivo_nombre")
	private String motivoNombre;

	/**
	 * La evaluación con IA se saltó porque el nombre parecía inventado.
	 *
	 * <p>Un nombre de broma suele significar que la solicitud entera lo es, y pagar la llamada
	 * para eso no tiene sentido. Pero la decisión final es tuya: el panel ofrece lanzarla igual.
	 */
	// @ColumnDefault es imprescindible al añadir una columna `not null` a una tabla que ya tiene
	// filas: sin él, el ALTER que genera `ddl-auto: update` no sabe qué poner en las existentes
	// y falla. Hibernate lo traduce al dialecto, así que vale igual para H2 y para Postgres.
	@ColumnDefault("false")
	@Column(name = "llm_omitido_por_nombre", nullable = false)
	private boolean llmOmitidoPorNombre;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn = Instant.now();

	@Column(name = "actualizada_en", nullable = false)
	private Instant actualizadaEn = Instant.now();

	@Column(name = "enviada_en")
	private Instant enviadaEn;

	@Column(name = "notificada_en")
	private Instant notificadaEn;

	@Column(name = "revisada_en")
	private Instant revisadaEn;

	@Column(name = "segundos_cumplimentacion")
	private Integer segundosCumplimentacion;

	@Column(name = "ip_alta")
	private String ipAlta;

	protected Candidatura() {
	}

	public Candidatura(UUID anuncioId, UUID formSchemaId, String token, String nombre, String idioma) {
		this.anuncioId = anuncioId;
		this.formSchemaId = formSchemaId;
		this.token = token;
		this.nombre = nombre;
		this.idioma = idioma;
	}

	public void guardarPaso(int paso, Map<String, Object> parciales) {
		this.respuestas.putAll(parciales);
		this.pasoActual = Math.max(this.pasoActual, paso);
		this.actualizadaEn = Instant.now();
	}

	public void marcarEnviada() {
		this.estado = EstadoCandidatura.ENVIADA;
		this.enviadaEn = Instant.now();
		this.actualizadaEn = this.enviadaEn;
		this.segundosCumplimentacion = (int) (enviadaEn.getEpochSecond() - creadaEn.getEpochSecond());
	}

	public void aplicarEvaluacion(int total, boolean noCumpleMinimos, List<String> motivos) {
		this.puntuacion = total;
		this.noCumpleMinimos = noCumpleMinimos;
		this.motivosMinimos = motivos;
		if (this.estado == EstadoCandidatura.ENVIADA) {
			this.estado = EstadoCandidatura.EVALUADA;
		}
		this.actualizadaEn = Instant.now();
	}

	/**
	 * Alta directa de una candidatura sintética, saltándose el asistente.
	 *
	 * <p>Sólo lo usa el sembrador: estas candidaturas son el árbitro para comparar modelos, y
	 * por eso llevan tu nota manual puesta de entrada.
	 */
	public void sembrarComoEnviada(Map<String, Object> respuestas, Integer puntuacionManual, int pasos) {
		this.respuestas.putAll(respuestas);
		this.pasoActual = pasos;
		this.sintetica = true;
		this.puntuacionManual = puntuacionManual;
		marcarEnviada();
	}

	public void triar(EstadoCandidatura nuevo, Integer puntuacionManual) {
		this.estado = nuevo;
		if (puntuacionManual != null) {
			this.puntuacionManual = puntuacionManual;
		}
		this.revisadaEn = Instant.now();
		this.actualizadaEn = this.revisadaEn;
	}

	/**
	 * Tu nota, sin tocar el estado.
	 *
	 * <p>Acepta null para poder quitarla: el selector del panel tiene la opción «sin nota», y sin
	 * esto no habría forma de deshacer una puesta por error.
	 *
	 * <p>Actualiza `actualizadaEn` porque el panel detecta los cambios comparando esa marca; si
	 * no, la nota no aparecería en otra pestaña abierta hasta el siguiente cambio.
	 */
	public void ponerNotaManual(Integer puntuacionManual) {
		this.puntuacionManual = puntuacionManual;
		this.actualizadaEn = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getAnuncioId() {
		return anuncioId;
	}

	public UUID getFormSchemaId() {
		return formSchemaId;
	}

	public String getToken() {
		return token;
	}

	public String getNombre() {
		return nombre;
	}

	public String getTelefonoNormalizado() {
		return telefonoNormalizado;
	}

	public void setTelefonoNormalizado(String telefonoNormalizado) {
		this.telefonoNormalizado = telefonoNormalizado;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getIdioma() {
		return idioma;
	}

	public void setIdioma(String idioma) {
		this.idioma = idioma;
	}

	public EstadoCandidatura getEstado() {
		return estado;
	}

	public int getPasoActual() {
		return pasoActual;
	}

	public Map<String, Object> getRespuestas() {
		return respuestas;
	}

	public boolean isNoCumpleMinimos() {
		return noCumpleMinimos;
	}

	public List<String> getMotivosMinimos() {
		return motivosMinimos;
	}

	public Integer getPuntuacion() {
		return puntuacion;
	}

	public boolean isSintetica() {
		return sintetica;
	}

	public void setSintetica(boolean sintetica) {
		this.sintetica = sintetica;
	}

	public Integer getPuntuacionManual() {
		return puntuacionManual;
	}

	public String getVerificacionNombre() {
		return verificacionNombre;
	}

	public String getMotivoNombre() {
		return motivoNombre;
	}

	public void anotarVerificacionNombre(String veredicto, String motivo) {
		this.verificacionNombre = veredicto;
		this.motivoNombre = motivo;
	}

	public boolean isLlmOmitidoPorNombre() {
		return llmOmitidoPorNombre;
	}

	public void marcarLlmOmitido(boolean omitido) {
		this.llmOmitidoPorNombre = omitido;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public Instant getActualizadaEn() {
		return actualizadaEn;
	}

	public Instant getEnviadaEn() {
		return enviadaEn;
	}

	public Instant getNotificadaEn() {
		return notificadaEn;
	}

	public void setNotificadaEn(Instant notificadaEn) {
		this.notificadaEn = notificadaEn;
	}

	public Instant getRevisadaEn() {
		return revisadaEn;
	}

	public Integer getSegundosCumplimentacion() {
		return segundosCumplimentacion;
	}

	public void setIpAlta(String ipAlta) {
		this.ipAlta = ipAlta;
	}

	public String getIpAlta() {
		return ipAlta;
	}
}
