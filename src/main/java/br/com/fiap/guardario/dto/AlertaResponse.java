package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.OrigemAlerta;
import br.com.fiap.guardario.model.StatusAlerta;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

@Relation(collectionRelation = "alertas", itemRelation = "alerta")
public record AlertaResponse(
        Long id,
        NivelRisco nivel,
        String titulo,
        String mensagem,
        String orientacao,
        StatusAlerta status,
        OrigemAlerta origem,
        Instant emitidoEm,
        Instant encerradoEm,
        Long areaId,
        String areaNome,
        Long estacaoId,
        Long emitidoPorId,
        Long encerradoPorId
) {
}
