package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import org.springframework.hateoas.server.core.Relation;

// montado direto pela consulta JPQL com "select new" (AreaRiscoRepository.rankingPorRelatosEmAberto)
@Relation(collectionRelation = "ranking", itemRelation = "posicao")
public record RankingAreaResponse(
        Long areaId,
        String nome,
        String cidade,
        String uf,
        NivelRisco nivelAtual,
        Long relatosEmAberto,
        Integer prioridadeMaxima
) {
}
