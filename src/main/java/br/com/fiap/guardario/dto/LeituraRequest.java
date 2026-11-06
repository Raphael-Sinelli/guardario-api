package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record LeituraRequest(
        @Schema(example = "235") @NotNull @PositiveOrZero @Max(5000)
        Integer nivelCm,

        @Schema(example = "12.4") @NotNull @PositiveOrZero @DecimalMax("500.0")
        Double chuvaMmH,

        @Schema(example = "23.8") @DecimalMin("-50.0") @DecimalMax("70.0")
        Double temperaturaC,

        @Schema(example = "91") @DecimalMin("0.0") @DecimalMax("100.0")
        Double umidadePct,

        @Schema(description = "Momento da medição em UTC. Se não vier, o servidor usa o horário atual",
                example = "2026-11-18T14:30:00Z")
        @PastOrPresent
        Instant medidaEm
) {
}
