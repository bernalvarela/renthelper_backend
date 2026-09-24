package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Correo y Telegram, configurables desde el panel.
 *
 * <p>Una única fila por tenant. No es una tabla de clave-valor a propósito: son ocho campos con
 * tipos distintos que se editan juntos en un formulario, y un mapa genérico sólo añadiría
 * conversiones y errores en tiempo de ejecución a cambio de una flexibilidad que nadie pidió.
 *
 * <p>Los dos secretos —contraseña del SMTP y token del bot— van cifrados y no salen nunca hacia
 * el navegador.
 */
@Entity
@Table(name = "ajustes")
public class Ajustes {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "smtp_host")
	private String smtpHost;

	@ColumnDefault("587")
	@Column(name = "smtp_puerto", nullable = false)
	private int smtpPuerto = 587;

	@Column(name = "smtp_usuario")
	private String smtpUsuario;

	@Column(name = "smtp_password_cifrada", length = 2000)
	private String smtpPasswordCifrada;

	/**
	 * Remitente de los correos al candidato.
	 *
	 * <p>Tiene que ser la misma cuenta del SMTP o un alias suyo verificado. Si no, el proveedor
	 * lo reescribe o rechaza el envío, y el fallo aparece lejos de su causa.
	 */
	@Column(name = "correo_remitente")
	private String correoRemitente;

	@ColumnDefault("true")
	@Column(name = "smtp_starttls", nullable = false)
	private boolean smtpStarttls = true;

	@Column(name = "telegram_token_cifrado", length = 2000)
	private String telegramTokenCifrado;

	@Column(name = "telegram_chat_id")
	private String telegramChatId;

	/**
	 * Bonificación fiscal por edad del inquilino.
	 *
	 * <p>Los tramos y porcentajes los declaras tú y NO los deduce el modelo: no tiene acceso a
	 * internet ni sabe qué día es, así que reproduciría de memoria cifras de su entrenamiento sin
	 * avisar de que se las está inventando. Para algo que acaba en tu declaración, no vale.
	 *
	 * <p>Lo que se calcula aquí es sólo si las edades declaradas caen en el tramo. El porcentaje y
	 * los requisitos van en {@link #bonificacionNota}, como texto tuyo que se muestra tal cual.
	 */
	@ColumnDefault("false")
	@Column(name = "bonificacion_activa", nullable = false)
	private boolean bonificacionActiva;

	@ColumnDefault("18")
	@Column(name = "bonificacion_edad_min", nullable = false)
	private int bonificacionEdadMin = 18;

	@ColumnDefault("35")
	@Column(name = "bonificacion_edad_max", nullable = false)
	private int bonificacionEdadMax = 35;

	/** Porcentaje y requisitos, con tus palabras. Se enseña tal cual, no se interpreta. */
	@Column(name = "bonificacion_nota", length = 1000)
	private String bonificacionNota;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn = Instant.now();

	public void tocar() {
		this.actualizadoEn = Instant.now();
	}

	/** Sin host o sin remitente no se puede enviar: el correo se escribe en el log. */
	public boolean correoConfigurado() {
		return smtpHost != null && !smtpHost.isBlank()
				&& correoRemitente != null && !correoRemitente.isBlank();
	}

	public boolean telegramConfigurado() {
		return telegramTokenCifrado != null && !telegramTokenCifrado.isBlank()
				&& telegramChatId != null && !telegramChatId.isBlank();
	}

	public UUID getId() {
		return id;
	}

	public String getSmtpHost() {
		return smtpHost;
	}

	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}

	public int getSmtpPuerto() {
		return smtpPuerto;
	}

	public void setSmtpPuerto(int smtpPuerto) {
		this.smtpPuerto = smtpPuerto <= 0 ? 587 : smtpPuerto;
	}

	public String getSmtpUsuario() {
		return smtpUsuario;
	}

	public void setSmtpUsuario(String smtpUsuario) {
		this.smtpUsuario = smtpUsuario;
	}

	public String getSmtpPasswordCifrada() {
		return smtpPasswordCifrada;
	}

	public void setSmtpPasswordCifrada(String smtpPasswordCifrada) {
		this.smtpPasswordCifrada = smtpPasswordCifrada;
	}

	public String getCorreoRemitente() {
		return correoRemitente;
	}

	public void setCorreoRemitente(String correoRemitente) {
		this.correoRemitente = correoRemitente;
	}

	public boolean isSmtpStarttls() {
		return smtpStarttls;
	}

	public void setSmtpStarttls(boolean smtpStarttls) {
		this.smtpStarttls = smtpStarttls;
	}

	public String getTelegramTokenCifrado() {
		return telegramTokenCifrado;
	}

	public void setTelegramTokenCifrado(String telegramTokenCifrado) {
		this.telegramTokenCifrado = telegramTokenCifrado;
	}

	public String getTelegramChatId() {
		return telegramChatId;
	}

	public void setTelegramChatId(String telegramChatId) {
		this.telegramChatId = telegramChatId;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public boolean isBonificacionActiva() {
		return bonificacionActiva;
	}

	public void setBonificacionActiva(boolean bonificacionActiva) {
		this.bonificacionActiva = bonificacionActiva;
	}

	public int getBonificacionEdadMin() {
		return bonificacionEdadMin;
	}

	public void setBonificacionEdadMin(int bonificacionEdadMin) {
		this.bonificacionEdadMin = bonificacionEdadMin;
	}

	public int getBonificacionEdadMax() {
		return bonificacionEdadMax;
	}

	public void setBonificacionEdadMax(int bonificacionEdadMax) {
		this.bonificacionEdadMax = bonificacionEdadMax;
	}

	public String getBonificacionNota() {
		return bonificacionNota;
	}

	public void setBonificacionNota(String bonificacionNota) {
		this.bonificacionNota = bonificacionNota;
	}
}
