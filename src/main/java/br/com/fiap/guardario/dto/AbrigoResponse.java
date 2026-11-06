package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.StatusAbrigo;
import org.springframework.hateoas.server.core.Relation;

@Relation(collectionRelation = "abrigos", itemRelation = "abrigo")
public record AbrigoResponse(
        Long id,
        String nome,
        String endereco,
        Integer capacidade,
        Integer ocupacao,
        Integer vagas,
        Boolean aceitaPets,
        Boolean acessivelPcd,
        String telefone,
        Double latitude,
        Double longitude,
        StatusAbrigo status,
        Long areaId
) {
}
