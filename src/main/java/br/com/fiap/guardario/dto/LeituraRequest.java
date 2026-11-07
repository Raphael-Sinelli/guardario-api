package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.validation.JanelaDeMedicao;
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

        // sem example fixo: uma data fixa vira futuro ou passado demais com o tempo, e o "Try it out" do Swagger daria 400
        @Schema(description = "Momento da medição em UTC, dos últimos 30 dias (aceita até 1 minuto adiantado). "
                + "Se não vier, o servidor usa o horário atual")
        @JanelaDeMedicao(diasAtras = 30, segundosAdiante = 60)
        Instant medidaEm
) {
}
