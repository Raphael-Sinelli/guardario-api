package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.Papel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

// nunca devolve a senha: o DTO evita expor o hash que está na entidade
@Relation(collectionRelation = "usuarios", itemRelation = "usuario")
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Papel papel,
        String telefone,
        Long areaId,
        Instant criadoEm
) {
}
