package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.StatusEstacao;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

@Relation(collectionRelation = "estacoes", itemRelation = "estacao")
public record EstacaoResponse(
        Long id,
        String codigo,
        String nome,
        String rio,
        Integer cotaAtencaoCm,
        Integer cotaAlertaCm,
        Integer cotaTransbordoCm,
        StatusEstacao status,
        Long areaId,
        String areaNome,
        Integer ultimoNivelCm,
        NivelRisco ultimoRisco,
        Instant ultimaLeituraEm
) {
}
