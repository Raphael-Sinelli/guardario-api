package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// alerta manual, emitido por um agente da Defesa Civil
public record AlertaRequest(
        @Schema(example = "1") @NotNull @Positive
        Long areaId,

        @Schema(description = "Opcional: estação relacionada ao alerta", example = "1") @Positive
        Long estacaoId,

        @Schema(description = "ATENCAO, ALERTA ou EMERGENCIA", example = "ALERTA") @NotNull
        NivelRisco nivel,

        @Schema(example = "Previsão de chuva forte na madrugada") @NotBlank @Size(max = 150)
        String titulo,

        @Schema(example = "A Defesa Civil prevê até 80 mm de chuva. Moradores da beira do rio devem se preparar para sair.")
        @NotBlank @Size(max = 1000)
        String mensagem
) {
}
