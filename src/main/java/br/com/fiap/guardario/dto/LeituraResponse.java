package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

@Relation(collectionRelation = "leituras", itemRelation = "leitura")
public record LeituraResponse(
        Long id,
        Long estacaoId,
        Integer nivelCm,
        Double chuvaMmH,
        Double temperaturaC,
        Double umidadePct,
        NivelRisco nivelRisco,
        Instant medidaEm
) {
}
