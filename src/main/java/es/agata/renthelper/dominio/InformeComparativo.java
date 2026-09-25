package es.agata.renthelper.dominio;

import es.agata.renthelper.llm.InformeComparativoLlm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Informe que compara a los finalistas de un anuncio.
 *
 * <p>Se guarda cada uno que se genera; el panel enseña el último. Habla de personas concretas,
 * así que se borra con cualquiera de sus candidaturas (a mano o por la purga del RGPD).
 */
@Entity
@Table(name = "informe_comparativo")
public class InformeComparativo {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "anuncio_id", nullable = false)
	private UUID anuncioId;

	/** Letra con la que se le presentó al modelo → id de la candidatura. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, String> candidaturas = new LinkedHashMap<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private InformeComparativoLlm contenido;

	@Column(nullable = false)
	private String proveedor;

	private String modelo;

	@Column(name = "prompt_version")
	private String promptVersion;

	@Column(name = "tokens_entrada")
	private Integer tokensEntrada;

	@Column(name = "tokens_salida")
	private Integer tokensSalida;

	@Column(name = "latencia_ms")
	private Integer latenciaMs;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected InformeComparativo() {
	}

	public InformeComparativo(UUID anuncioId, Map<String, String> candidaturas, InformeComparativoLlm contenido,
	                          String proveedor, String modelo, String promptVersion,
	                          Integer tokensEntrada, Integer tokensSalida, Integer latenciaMs) {
		this.anuncioId = anuncioId;
		this.candidaturas = new LinkedHashMap<>(candidaturas);
		this.contenido = contenido;
		this.proveedor = proveedor;
		this.modelo = modelo;
		this.promptVersion = promptVersion;
		this.tokensEntrada = tokensEntrada;
		this.tokensSalida = tokensSalida;
		this.latenciaMs = latenciaMs;
	}

	/** Si en él aparece esa candidatura, con cualquier letra. */
	public boolean incluye(UUID candidaturaId) {
		return candidaturas.containsValue(candidaturaId.toString());
	}

	public UUID getId() {
		return id;
	}

	public UUID getAnuncioId() {
		return anuncioId;
	}

	public Map<String, String> getCandidaturas() {
		return candidaturas;
	}

	public InformeComparativoLlm getContenido() {
		return contenido;
	}

	public String getProveedor() {
		return proveedor;
	}

	public String getModelo() {
		return modelo;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public Integer getLatenciaMs() {
		return latenciaMs;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
