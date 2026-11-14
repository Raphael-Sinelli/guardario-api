package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.StatusEstacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EstacaoRepository extends JpaRepository<Estacao, Long> {

    // a resposta mostra o nome da área: o @EntityGraph traz a área no mesmo SELECT e evita o N+1
    @Override
    @EntityGraph(attributePaths = "area")
    Page<Estacao> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "area")
    Page<Estacao> findByAreaId(Long areaId, Pageable pageable);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByAreaId(Long areaId);

    // trava a estação até o fim da transação: duas leituras da mesma estação não sobrescrevem o último nível uma da outra
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estacao e where e.id = :id")
    Optional<Estacao> buscarComTrava(@Param("id") Long id);

    /*
     * Estações ATIVAS em ALERTA ou EMERGENCIA, das mais perto do transbordo para as mais longe.
     * O join fetch carrega a área junto (sem N+1) e a ordenação usa uma conta entre colunas,
     * coisa que o nome de um derived query não expressa.
     */
    @Query("""
            select e from Estacao e
            join fetch e.area
            where e.status = :status and e.ultimoRisco in :riscos
            order by (e.ultimoNivelCm - e.cotaTransbordoCm) desc
            """)
    List<Estacao> buscarCriticas(@Param("status") StatusEstacao status, @Param("riscos") Collection<NivelRisco> riscos);

    /*
     * Estações ATIVAS que nunca mandaram leitura ou estão sem leitura desde antes do limite.
     * Derived query não expressa bem "is null OU antes do limite" com os nulos primeiro,
     * e a resposta mostra o nome da área: o join fetch traz a área no mesmo SELECT (sem N+1).
     */
    @Query("""
            select e from Estacao e
            join fetch e.area
            where e.status = :status
              and (e.ultimaLeituraEm is null or e.ultimaLeituraEm < :limite)
            order by e.ultimaLeituraEm asc nulls first
            """)
    List<Estacao> buscarSilenciosas(@Param("status") StatusEstacao status, @Param("limite") Instant limite);
}
