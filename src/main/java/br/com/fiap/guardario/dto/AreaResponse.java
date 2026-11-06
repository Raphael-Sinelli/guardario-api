package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "areas", itemRelation = "area")
public record AreaResponse(
        Long id,
        String nome,
        String cidade,
        String uf,
        Integer populacaoEstimada,
        Double latitude,
        Double longitude,
        NivelRisco nivelAtual,
        String orientacao
) {
}
