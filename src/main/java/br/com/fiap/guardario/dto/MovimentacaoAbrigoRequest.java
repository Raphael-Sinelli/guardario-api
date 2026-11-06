package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// entrada ou saída de pessoas no abrigo
public record MovimentacaoAbrigoRequest(
        @Schema(example = "4") @NotNull @Positive @Max(500)
        Integer pessoas
) {
}
