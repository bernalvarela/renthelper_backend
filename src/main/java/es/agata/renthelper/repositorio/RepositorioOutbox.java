package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.MensajeOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepositorioOutbox extends JpaRepository<MensajeOutbox, UUID> {

	/**
	 * Toma un lote bloqueando las filas.
	 *
	 * <p>El poller usa {@code fixedDelay}, así que en un único nodo no se solapa consigo mismo y
	 * el bloqueo es cinturón y tirantes. Si algún día hay réplicas hará falta además SKIP LOCKED
	 * ({@code jakarta.persistence.lock.timeout = -2}), que no está puesto porque H2 no lo
	 * soporta y rompería el arranque en local.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select m from MensajeOutbox m
			where m.estado = :estado
			  and m.proximaEjecucion <= :ahora
			order by m.proximaEjecucion asc
			""")
	List<MensajeOutbox> tomarPendientes(@Param("estado") MensajeOutbox.Estado estado,
	                                    @Param("ahora") Instant ahora,
	                                    Limit limite);

	default List<MensajeOutbox> tomarPendientes(Instant ahora, Limit limite) {
		return tomarPendientes(MensajeOutbox.Estado.PENDIENTE, ahora, limite);
	}

	long countByEstado(MensajeOutbox.Estado estado);

	/**
	 * Las evaluaciones en cola de esas candidaturas, para que el panel marque qué filas se están
	 * repuntuando. Entidades y no una proyección: así no hace falta registrar nada más para la
	 * imagen nativa.
	 */
	List<MensajeOutbox> findByTipoAndEstadoAndReferenciaIdIn(MensajeOutbox.Tipo tipo, MensajeOutbox.Estado estado,
	                                                         Collection<UUID> referencias);

	/** Lo último que ha pasado, para el panel de actividad. Incluye lo ya procesado. */
	List<MensajeOutbox> findTop30ByOrderByCreadoEnDesc();
}
