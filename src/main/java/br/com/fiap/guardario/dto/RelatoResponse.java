package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.TipoRelato;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

@Relation(collectionRelation = "relatos", itemRelation = "relato")
public record RelatoResponse(
        Long id,
        TipoRelato tipo,
        String descricao,
        Double latitude,
        Double longitude,
        Integer prioridade,
        StatusRelato status,
        String observacaoAgente,
        Long autorId,
        Long areaId,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
