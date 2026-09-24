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
 * Un modelo de la cadena de evaluación, configurable desde el panel.
 *
 * <p>Estaba en {@code application.yml} y se movió aquí por una razón práctica: los cupos
 * gratuitos cambian, los proveedores retiran modelos con poco aviso y hace falta reordenar la
 * cadena en caliente. Con la configuración en un fichero, cada cambio era recompilar y desplegar.
 *
 * <p><b>Todos hablan la API de OpenAI.</b> No hay un adaptador por proveedor: Groq, OpenRouter,
 * Together, Ollama y también Gemini —que tiene endpoint compatible— aceptan la misma forma, así
 * que un proveedor nuevo es sólo una fila con su url base, su clave y el nombre del modelo. Sin
 * tocar el pom.xml ni desplegar.
 */
@Entity
@Table(name = "proveedor_llm")
public class ProveedorLlm {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	/** Cómo lo llamas tú. Se guarda en cada evaluación, así que conviene que sea reconocible. */
	@Column(nullable = false)
	private String nombre;

	/**
	 * Url base, incluida la versión.
	 *
	 * <p>Con {@code /v1} al final, como espera el cliente de OpenAI: sólo le añade
	 * {@code /chat/completions}. Sin la versión sale un 404 desconcertante que habla de una ruta
	 * que tú nunca escribiste.
	 */
	@Column(name = "url_base", nullable = false)
	private String urlBase;

	@Column(nullable = false)
	private String modelo;

	/** Cifrada. Nunca sale hacia el navegador: la API devuelve sólo una pista. */
	@Column(name = "api_key_cifrada", length = 2000)
	private String apiKeyCifrada;

	@ColumnDefault("true")
	@Column(nullable = false)
	private boolean activo = true;

	/** Posición en la cadena. El primero activo y apto se lleva el rol PRINCIPAL. */
	@ColumnDefault("0")
	@Column(nullable = false)
	private int orden;

	/**
	 * Si puede ver datos de candidatos reales.
	 *
	 * <p>Los tiers gratuitos se usan para entrenar, y para esa cesión no hay base legal con datos
	 * de terceros. Un proveedor no apto sólo evalúa candidaturas sintéticas; con una real, el
	 * orquestador lo salta. Es la salvaguarda que evita que un despiste de configuración mande
	 * los ingresos de ciento cincuenta personas a un tier gratuito.
	 */
	@ColumnDefault("false")
	@Column(name = "apto_datos_reales", nullable = false)
	private boolean aptoDatosReales;

	/** En sombra se ejecuta además del principal, para comparar, y no toca el ranking. */
	@ColumnDefault("false")
	@Column(nullable = false)
	private boolean sombra;

	@ColumnDefault("0")
	@Column(name = "limite_mensual_llamadas", nullable = false)
	private int limiteMensualLlamadas;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	/** Sirve además de clave de caché: si cambia, el ChatModel se reconstruye. */
	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn = Instant.now();

	protected ProveedorLlm() {
	}

	public ProveedorLlm(String nombre, String urlBase, String modelo) {
		this.nombre = nombre;
		this.urlBase = urlBase;
		this.modelo = modelo;
	}

	public void tocar() {
		this.actualizadoEn = Instant.now();
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

	public String getUrlBase() {
		return urlBase;
	}

	public void setUrlBase(String urlBase) {
		this.urlBase = urlBase;
	}

	public String getModelo() {
		return modelo;
	}

	public void setModelo(String modelo) {
		this.modelo = modelo;
	}

	public String getApiKeyCifrada() {
		return apiKeyCifrada;
	}

	public void setApiKeyCifrada(String apiKeyCifrada) {
		this.apiKeyCifrada = apiKeyCifrada;
	}

	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}

	public int getOrden() {
		return orden;
	}

	public void setOrden(int orden) {
		this.orden = orden;
	}

	public boolean isAptoDatosReales() {
		return aptoDatosReales;
	}

	public void setAptoDatosReales(boolean aptoDatosReales) {
		this.aptoDatosReales = aptoDatosReales;
	}

	public boolean isSombra() {
		return sombra;
	}

	public void setSombra(boolean sombra) {
		this.sombra = sombra;
	}

	/** Cero o negativo significa sin límite, que es lo que se espera de un campo vacío. */
	public int getLimiteMensualLlamadas() {
		return limiteMensualLlamadas <= 0 ? Integer.MAX_VALUE : limiteMensualLlamadas;
	}

	public int getLimiteCrudo() {
		return limiteMensualLlamadas;
	}

	public void setLimiteMensualLlamadas(int limiteMensualLlamadas) {
		this.limiteMensualLlamadas = limiteMensualLlamadas;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
