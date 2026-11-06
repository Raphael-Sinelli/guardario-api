package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;

// montado direto pela consulta JPQL com "select new" (AreaRiscoRepository.rankingPorRelatosEmAberto)
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
