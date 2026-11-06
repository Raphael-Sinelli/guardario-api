package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.dto.RankingAreaResponse;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.StatusRelato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AreaRiscoRepository extends JpaRepository<AreaRisco, Long> {

    Page<AreaRisco> findByUfIgnoreCase(String uf, Pageable pageable);

    /*
     * Ranking das áreas pelos relatos ainda em aberto. Derived query não faz isso:
     * precisa de left join (área sem relato também aparece), group by, count, max
     * e projeção direta no record com "select new".
     */
    @Query("""
            select new br.com.fiap.guardario.dto.RankingAreaResponse(
                a.id, a.nome, a.cidade, a.uf, a.nivelAtual, count(r.id), coalesce(max(r.prioridade), 0))
            from AreaRisco a
            left join Relato r on r.area = a and r.status in :statusEmAberto
            group by a.id, a.nome, a.cidade, a.uf, a.nivelAtual
            order by count(r.id) desc, coalesce(max(r.prioridade), 0) desc, a.nome
            """)
    List<RankingAreaResponse> rankingPorRelatosEmAberto(@Param("statusEmAberto") Collection<StatusRelato> statusEmAberto);
}
