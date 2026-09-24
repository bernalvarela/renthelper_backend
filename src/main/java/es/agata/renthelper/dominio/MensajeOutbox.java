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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox transaccional. El envío del formulario se confirma al candidato sin esperar a
 * que respondan el LLM y Telegram; la fila se escribe en la misma transacción, así nunca
 * se pierde una evaluación porque un proveedor estuviese caído.
 */
@Entity
@Table(name = "outbox")
public class MensajeOutbox {

	public enum Tipo {
		EVALUAR_CANDIDATURA,
		ALERTA_INMEDIATA,
		/** Enviar al candidato su enlace de continuación por correo. */
		ENVIAR_ENLACE
	}

	public enum Estado {
		PENDIENTE,
		COMPLETADO,
		FALLIDO
	}

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	/**
	 * Varchar en vez del ENUM nativo: H2 crea un tipo cerrado con los valores que existían al
	 * generar la tabla y `ddl-auto: update` no sabe ampliarlo, así que añadir una constante
	 * rompe el insert. En PostgreSQL la columna ya es `text`, con lo que esto además iguala los
	 * dos motores.
	 */
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(name = "referencia_id")
	private UUID referenciaId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column
	private Map<String, Object> payload;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Estado estado = Estado.PENDIENTE;

	@Column(nullable = false)
	private int intentos;

	/** Esperas por proveedor no disponible. Se cuentan aparte de los fallos reales. */
	@ColumnDefault("0")
	@Column(nullable = false)
	private int aplazamientos;

	@Column(name = "ultimo_error", length = 2000)
	private String ultimoError;

	@Column(name = "proxima_ejecucion", nullable = false)
	private Instant proximaEjecucion = Instant.now();

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	@Column(name = "procesado_en")
	private Instant procesadoEn;

	protected MensajeOutbox() {
	}

	public MensajeOutbox(Tipo tipo, UUID referenciaId) {
		this.tipo = tipo;
		this.referenciaId = referenciaId;
	}

	public void completar() {
		this.estado = Estado.COMPLETADO;
		this.procesadoEn = Instant.now();
		this.ultimoError = null;
	}

	/**
	 * Aplaza sin contarlo como fallo.
	 *
	 * <p>Para cuando el proveedor no está disponible por cuota o sobrecarga: no hay nada roto, y
	 * el backoff exponencial de {@link #fallar} —minutos— no sirve cuando la cuota es diaria.
	 * Se reintenta a intervalos largos y constantes hasta agotar los aplazamientos.
	 */
	public void aplazar(long minutos, int maxAplazamientos) {
		this.aplazamientos++;
		this.proximaEjecucion = Instant.now().plus(Duration.ofMinutes(minutos));
		if (this.aplazamientos >= maxAplazamientos) {
			this.estado = Estado.FALLIDO;
			this.procesadoEn = Instant.now();
		}
	}

	/** Backoff exponencial; al agotar intentos se marca FALLIDO y se deja para revisión manual. */
	public void fallar(String mensaje, int maxIntentos, long backoffBaseSegundos) {
		this.intentos++;
		this.ultimoError = mensaje != null && mensaje.length() > 1900 ? mensaje.substring(0, 1900) : mensaje;
		if (this.intentos >= maxIntentos) {
			this.estado = Estado.FALLIDO;
			this.procesadoEn = Instant.now();
		} else {
			long espera = backoffBaseSegundos * (long) Math.pow(2, this.intentos - 1);
			this.proximaEjecucion = Instant.now().plus(Duration.ofSeconds(espera));
		}
	}

	public UUID getId() {
		return id;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public UUID getReferenciaId() {
		return referenciaId;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public void setPayload(Map<String, Object> payload) {
		this.payload = payload;
	}

	public Estado getEstado() {
		return estado;
	}

	public int getAplazamientos() {
		return aplazamientos;
	}

	public int getIntentos() {
		return intentos;
	}

	public String getUltimoError() {
		return ultimoError;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getProcesadoEn() {
		return procesadoEn;
	}

	/** Cuándo volverá a intentarse. Con cuota agotada puede ser dentro de una hora. */
	public Instant getProximaEjecucion() {
		return proximaEjecucion;
	}
}
