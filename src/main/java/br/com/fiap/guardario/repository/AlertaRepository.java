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

    // a resposta do alerta leva o nome da área, então ela vem no mesmo SELECT
    @Override
    @EntityGraph(attributePaths = "area")
    Page<Alerta> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "area")
    Page<Alerta> findByStatus(StatusAlerta status, Pageable pageable);

    @EntityGraph(attributePaths = "area")
    List<Alerta> findByStatusOrderByEmitidoEmDesc(StatusAlerta status);

    // usada na RN02 para saber se a área já tem alerta ativo
    List<Alerta> findByAreaIdAndStatus(Long areaId, StatusAlerta status);

    boolean existsByAreaId(Long areaId);

    boolean existsByEstacaoId(Long estacaoId);

    // estações da página que têm alerta no histórico (não podem ser excluídas): uma consulta para a página inteira
    @Query("select distinct a.estacao.id from Alerta a where a.estacao.id in :ids")
    Set<Long> buscarEstacaoIdsComAlertas(@Param("ids") Collection<Long> ids);
}
