package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.dto.RankingAreaResponse;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.StatusRelato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AreaRiscoRepository extends JpaRepository<AreaRisco, Long> {

    Page<AreaRisco> findByUfIgnoreCase(String uf, Pageable pageable);

    /*
     * Quais áreas da página têm algum vínculo (e por isso não podem ser excluídas): uma consulta para a página
     * inteira, em vez de cinco exists por área. Serve para o link HATEOAS "excluir" só aparecer quando dá certo.
     */
    @Query("""
            select a.id from AreaRisco a
            where a.id in :ids and (
                exists (select 1 from Estacao e where e.area = a)
                or exists (select 1 from Abrigo b where b.area = a)
                or exists (select 1 from Alerta al where al.area = a)
                or exists (select 1 from Relato r where r.area = a)
                or exists (select 1 from Usuario u where u.area = a))
            """)
    Set<Long> buscarIdsComVinculos(@Param("ids") Collection<Long> ids);

    /*
     * SELECT ... FOR UPDATE: a linha da área fica travada até o fim da transação. Duas leituras da mesma área
     * passam a entrar uma de cada vez, e a segunda já enxerga o que a primeira gravou (RN02 e RN03).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AreaRisco a where a.id = :id")
    Optional<AreaRisco> buscarComTrava(@Param("id") Long id);

    // só os ids: a reavaliação carrega cada área já travada, e uma área carregada antes da trava viria da memória
    @Query("select a.id from AreaRisco a where a.nivelAtual <> :nivel")
    List<Long> buscarIdsComNivelDiferenteDe(@Param("nivel") NivelRisco nivel);

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
