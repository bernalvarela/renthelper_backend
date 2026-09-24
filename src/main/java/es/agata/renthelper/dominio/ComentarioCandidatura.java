package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que apuntas tú sobre una candidatura, normalmente después de conocer a la persona.
 *
 * <p>Es una tabla aparte y no un campo de texto en la candidatura porque son <b>apuntes con
 * fecha</b>: tras la visita escribes una cosa, tres días después otra, y saber cuándo dijiste
 * cada una importa cuando llevas quince candidatos y han pasado dos semanas. Un único campo
 * obligaría a ir editando un churro de texto y perderías ese orden.
 *
 * <p>Ojo con lo que se escribe aquí: es texto libre sobre una persona identificada, así que cae
 * de lleno en el RGPD. Se borra con la candidatura —tanto en la purga por retención como al
 * ejercer el derecho de supresión— y nunca se envía al modelo.
 */
@Entity
@Table(name = "comentario_candidatura")
public class ComentarioCandidatura {

	@Id
	private UUID id = UUID.randomUUID();

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "candidatura_id", nullable = false)
	private UUID candidaturaId;

	@Column(nullable = false, length = 4000)
	private String texto;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn = Instant.now();

	protected ComentarioCandidatura() {
	}

	public ComentarioCandidatura(UUID candidaturaId, String texto) {
		this.candidaturaId = candidaturaId;
		this.texto = texto;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidaturaId() {
		return candidaturaId;
	}

	public String getTexto() {
		return texto;
	}

	public void setTexto(String texto) {
		this.texto = texto;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
