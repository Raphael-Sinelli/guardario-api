package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Alerta;
import br.com.fiap.guardario.model.StatusAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    // Mais grave primeiro, ordenado no banco. O nível é gravado como texto (EnumType.STRING), e a ordem alfabética não é
    // a de gravidade, então o CASE dá a cada nível o peso do NivelRisco (NORMAL 0 a EMERGENCIA 3). No empate, o alerta
    // mais recente e depois o id. O CASE é SQL padrão e roda igual no H2 e no PostgreSQL.
    String MAIS_GRAVE_PRIMEIRO = """
            case a.nivel
                when br.com.fiap.guardario.model.NivelRisco.EMERGENCIA then 3
                when br.com.fiap.guardario.model.NivelRisco.ALERTA then 2
                when br.com.fiap.guardario.model.NivelRisco.ATENCAO then 1
                else 0
            end desc, a.emitidoEm desc, a.id desc""";

    // a resposta do alerta leva o nome da área, então ela vem no mesmo SELECT
    @Override
    @EntityGraph(attributePaths = "area")
    Page<Alerta> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "area")
    Page<Alerta> findByStatus(StatusAlerta status, Pageable pageable);

    // a lista de alertas ativos (GET /api/alertas/ativos); a resposta leva o nome da área, que vem no mesmo SELECT
    @EntityGraph(attributePaths = "area")
    @Query("select a from Alerta a where a.status = :status order by " + MAIS_GRAVE_PRIMEIRO)
    List<Alerta> buscarPorGravidade(@Param("status") StatusAlerta status);

    // a lista de uma área (GET /api/alertas/ativos?areaId= e /api/alertas/minha-area), na mesma ordem
    @EntityGraph(attributePaths = "area")
    @Query("select a from Alerta a where a.area.id = :areaId and a.status = :status order by " + MAIS_GRAVE_PRIMEIRO)
    List<Alerta> buscarDaAreaPorGravidade(@Param("areaId") Long areaId, @Param("status") StatusAlerta status);

    // usada na RN02 para saber se a área já tem alerta ativo
    List<Alerta> findByAreaIdAndStatus(Long areaId, StatusAlerta status);

    boolean existsByAreaId(Long areaId);

    boolean existsByEstacaoId(Long estacaoId);

    // estações da página que têm alerta no histórico (não podem ser excluídas): uma consulta para a página inteira
    @Query("select distinct a.estacao.id from Alerta a where a.estacao.id in :ids")
    Set<Long> buscarEstacaoIdsComAlertas(@Param("ids") Collection<Long> ids);
}
