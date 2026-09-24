package es.agata.renthelper.dominio;

import es.agata.renthelper.puntuacion.modelo.CriterioPuntuado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una candidatura tiene N evaluaciones: la PRINCIPAL y las de SOMBRA para comparar modelos.
 * Se guardan proveedor, modelo y versiones de rúbrica y prompt porque sin eso no se puede
 * auditar una puntuación rara ni comparar dos candidaturas puntuadas de forma distinta.
 */
@Entity
@Table(name = "evaluacion")
public class Evaluacion {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "candidatura_id", nullable = false)
	private UUID candidaturaId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RolEvaluacion rol;

	@Column(nullable = false)
	private String proveedor;

	private String modelo;

	@Column(name = "puntuacion_determinista", nullable = false)
	private int puntuacionDeterminista;

	@Column(name = "ajuste_llm", nullable = false)
	private int ajusteLlm;

	@Column(name = "puntuacion_total", nullable = false)
	private int puntuacionTotal;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column
	private List<CriterioPuntuado> desglose;

	@Column(length = 2000)
	private String resumen;

	/**
	 * El juicio del modelo, separado de los hechos.
	 *
	 * <p>Van en dos campos porque al pedir uno solo el modelo resumía y se ahorraba el juicio,
	 * que es justo la parte que no se puede sacar de la tabla.
	 */
	@Column(length = 2000)
	private String valoracion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column
	private List<String> banderas;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "preguntas_pendientes")
	private List<String> preguntasPendientes;

	private String confianza;

	@Column(name = "rubrica_version")
	private Integer rubricaVersion;

	@Column(name = "prompt_version")
	private String promptVersion;

	@Column(name = "tokens_entrada")
	private Integer tokensEntrada;

	@Column(name = "tokens_salida")
	private Integer tokensSalida;

	@Column(name = "latencia_ms")
	private Integer latenciaMs;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_response")
	private Map<String, Object> rawResponse;

	@Column(length = 1000)
	private String error;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn = Instant.now();

	protected Evaluacion() {
	}

	public Evaluacion(UUID candidaturaId, RolEvaluacion rol, String proveedor) {
		this.candidaturaId = candidaturaId;
		this.rol = rol;
		this.proveedor = proveedor;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidaturaId() {
		return candidaturaId;
	}

	public RolEvaluacion getRol() {
		return rol;
	}

	public String getProveedor() {
		return proveedor;
	}

	public String getModelo() {
		return modelo;
	}

	public void setModelo(String modelo) {
		this.modelo = modelo;
	}

	public int getPuntuacionDeterminista() {
		return puntuacionDeterminista;
	}

	public void setPuntuacionDeterminista(int puntuacionDeterminista) {
		this.puntuacionDeterminista = puntuacionDeterminista;
	}

	public int getAjusteLlm() {
		return ajusteLlm;
	}

	public void setAjusteLlm(int ajusteLlm) {
		this.ajusteLlm = ajusteLlm;
	}

	public int getPuntuacionTotal() {
		return puntuacionTotal;
	}

	public void setPuntuacionTotal(int puntuacionTotal) {
		this.puntuacionTotal = puntuacionTotal;
	}

	public List<CriterioPuntuado> getDesglose() {
		return desglose;
	}

	public void setDesglose(List<CriterioPuntuado> desglose) {
		this.desglose = desglose;
	}

	public String getResumen() {
		return resumen;
	}

	public void setResumen(String resumen) {
		this.resumen = resumen;
	}

	public String getValoracion() {
		return valoracion;
	}

	public void setValoracion(String valoracion) {
		this.valoracion = valoracion;
	}

	public List<String> getBanderas() {
		return banderas;
	}

	public void setBanderas(List<String> banderas) {
		this.banderas = banderas;
	}

	public List<String> getPreguntasPendientes() {
		return preguntasPendientes;
	}

	public void setPreguntasPendientes(List<String> preguntasPendientes) {
		this.preguntasPendientes = preguntasPendientes;
	}

	public String getConfianza() {
		return confianza;
	}

	public void setConfianza(String confianza) {
		this.confianza = confianza;
	}

	public Integer getRubricaVersion() {
		return rubricaVersion;
	}

	public void setRubricaVersion(Integer rubricaVersion) {
		this.rubricaVersion = rubricaVersion;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public void setPromptVersion(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public void setTokensEntrada(Integer tokensEntrada) {
		this.tokensEntrada = tokensEntrada;
	}

	public Integer getTokensEntrada() {
		return tokensEntrada;
	}

	public void setTokensSalida(Integer tokensSalida) {
		this.tokensSalida = tokensSalida;
	}

	public Integer getTokensSalida() {
		return tokensSalida;
	}

	public Integer getLatenciaMs() {
		return latenciaMs;
	}

	public void setLatenciaMs(Integer latenciaMs) {
		this.latenciaMs = latenciaMs;
	}

	public void setRawResponse(Map<String, Object> rawResponse) {
		this.rawResponse = rawResponse;
	}

	public Map<String, Object> getRawResponse() {
		return rawResponse;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}
}
