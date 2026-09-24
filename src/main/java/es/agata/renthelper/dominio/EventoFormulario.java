package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Analítica de abandono. Con 150 personas rellenando desde el móvil, saber en qué paso se
 * caen es lo que dice si el formulario es demasiado largo — y los que abandonan suelen ser
 * los que tienen otras opciones.
 */
@Entity
@Table(name = "evento_formulario")
public class EventoFormulario {

	public enum Tipo {
		ALTA,
		PASO_COMPLETADO,
		ENVIADO
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "candidatura_id", nullable = false)
	private UUID candidaturaId;

	@Column(nullable = false)
	private String tipo;

	private Integer paso;

	@Column(name = "ocurrido_en", nullable = false)
	private Instant ocurridoEn = Instant.now();

	protected EventoFormulario() {
	}

	public EventoFormulario(UUID candidaturaId, Tipo tipo, Integer paso) {
		this.candidaturaId = candidaturaId;
		this.tipo = tipo.name();
		this.paso = paso;
	}

	public Long getId() {
		return id;
	}

	public UUID getCandidaturaId() {
		return candidaturaId;
	}

	public String getTipo() {
		return tipo;
	}

	public Integer getPaso() {
		return paso;
	}

	public Instant getOcurridoEn() {
		return ocurridoEn;
	}
}
