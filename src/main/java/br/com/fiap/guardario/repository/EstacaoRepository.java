package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.NivelRisco;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EstacaoRepository extends JpaRepository<Estacao, Long> {

    // a resposta mostra o nome da área: o @EntityGraph traz a área no mesmo SELECT e evita o N+1
    @Override
    @EntityGraph(attributePaths = "area")
    Page<Estacao> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "area")
    Page<Estacao> findByAreaId(Long areaId, Pageable pageable);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByAreaId(Long areaId);

    /*
     * Estações em ALERTA ou EMERGENCIA, das mais perto do transbordo para as mais longe.
     * O join fetch carrega a área junto (sem N+1) e a ordenação usa uma conta entre colunas,
     * coisa que o nome de um derived query não expressa.
     */
    @Query("""
            select e from Estacao e
            join fetch e.area
            where e.ultimoRisco in :riscos
            order by (e.ultimoNivelCm - e.cotaTransbordoCm) desc
            """)
    List<Estacao> buscarCriticas(@Param("riscos") Collection<NivelRisco> riscos);
}
