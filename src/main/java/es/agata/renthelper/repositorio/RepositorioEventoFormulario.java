package es.agata.renthelper.repositorio;

import es.agata.renthelper.dominio.EventoFormulario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepositorioEventoFormulario extends JpaRepository<EventoFormulario, Long> {

	/** Abandono por paso: [paso, cuántas candidaturas lo completaron]. */
	@Query("""
			select e.paso, count(distinct e.candidaturaId)
			from EventoFormulario e
			where e.tipo = 'PASO_COMPLETADO'
			  and e.candidaturaId in (select c.id from Candidatura c where c.anuncioId = :anuncioId)
			group by e.paso
			order by e.paso
			""")
	List<Object[]> embudoPorPaso(@Param("anuncioId") UUID anuncioId);

	/** Cuántas candidaturas se dieron de alta y cuántas llegaron a enviar. Los dos extremos del embudo. */
	@Query("""
			select e.tipo, count(distinct e.candidaturaId)
			from EventoFormulario e
			where e.tipo in ('ALTA', 'ENVIADO')
			  and e.candidaturaId in (select c.id from Candidatura c where c.anuncioId = :anuncioId)
			group by e.tipo
			""")
	List<Object[]> conteosPorTipo(@Param("anuncioId") UUID anuncioId);

	void deleteByCandidaturaId(UUID candidaturaId);
}
