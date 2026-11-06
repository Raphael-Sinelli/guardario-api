package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.StatusAbrigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusAbrigoRequest(
        @Schema(description = "ABERTO ou FECHADO. LOTADO é calculado pela ocupação", example = "FECHADO") @NotNull
        StatusAbrigo status
) {
}
