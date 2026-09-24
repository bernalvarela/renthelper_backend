package es.agata.renthelper.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/** Presupuesto por proveedor y mes. Al agotarse, la cadena pasa al siguiente proveedor. */
@Entity
// La restricción se declara aquí y no sólo en la migración para que el esquema que Hibernate
// genera en local (H2) sea equivalente al que crea Flyway en producción.
@Table(name = "consumo_llm",
		uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "proveedor", "periodo"}))
public class ConsumoLlm {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String proveedor;

	/** Formato aaaa-MM. */
	@Column(nullable = false)
	private String periodo;

	@Column(nullable = false)
	private int llamadas;

	@Column(nullable = false)
	private int fallos;

	protected ConsumoLlm() {
	}

	public ConsumoLlm(String proveedor, String periodo) {
		this.proveedor = proveedor;
		this.periodo = periodo;
	}

	public void registrarLlamada(boolean ok) {
		this.llamadas++;
		if (!ok) {
			this.fallos++;
		}
	}

	public String getProveedor() {
		return proveedor;
	}

	public String getPeriodo() {
		return periodo;
	}

	public int getLlamadas() {
		return llamadas;
	}

	public int getFallos() {
		return fallos;
	}
}
