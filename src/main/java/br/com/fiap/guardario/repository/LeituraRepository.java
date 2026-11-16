package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.dto.EstatisticaLeiturasResponse;
import br.com.fiap.guardario.model.Leitura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LeituraRepository extends JpaRepository<Leitura, Long> {

    Page<Leitura> findByEstacaoId(Long estacaoId, Pageable pageable);

    // a leitura que deu o estado atual da estação (a de medidaEm igual a ultimaLeituraEm), para reclassificar com a régua nova
    Optional<Leitura> findFirstByEstacaoIdAndMedidaEm(Long estacaoId, Instant medidaEm);

    // a mesma leitura (mesmo medidaEm e mesmas medidas) já gravada na estação: é o reenvio da ponte MQTT. São quatro
    // condições, e o derived query com elas teria um nome de parágrafo; o JPQL deixa o nome curto e a regra à vista.
    // Os quatro valores nunca chegam nulos (o medidaEm é conferido antes, e as medidas têm @NotNull)
    @Query("""
            select case when count(l) > 0 then true else false end
            from Leitura l
            where l.estacao.id = :estacaoId and l.medidaEm = :medidaEm
              and l.nivelCm = :nivelCm and l.chuvaMmH = :chuvaMmH
            """)
    boolean existeLeituraIgual(@Param("estacaoId") Long estacaoId, @Param("medidaEm") Instant medidaEm,
                               @Param("nivelCm") Integer nivelCm, @Param("chuvaMmH") Double chuvaMmH);

    // a leitura só é encontrada pela URL da estação dona dela
    Optional<Leitura> findByIdAndEstacaoId(Long id, Long estacaoId);

    // agregação (count, avg, min, max) feita no banco, sem trazer as leituras para a memória
    @Query("""
            select new br.com.fiap.guardario.dto.EstatisticaLeiturasResponse(
                count(l), avg(l.nivelCm), min(l.nivelCm), max(l.nivelCm), max(l.chuvaMmH))
            from Leitura l
            where l.estacao.id = :estacaoId and l.medidaEm >= :desde
            """)
    EstatisticaLeiturasResponse estatisticas(@Param("estacaoId") Long estacaoId, @Param("desde") Instant desde);
}
