package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AlterarPapelRequest(
        @Schema(example = "AGENTE") @NotNull
        Papel papel
) {
}
