package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.StatusEstacao;
import br.com.fiap.guardario.validation.CotasCrescentes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@CotasCrescentes
public record EstacaoRequest(
        @Schema(example = "GR-SP-004") @NotBlank @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9-]+", message = "use só letras, números e hífen")
        String codigo,

        @Schema(example = "Ponte da Rua Capachós") @NotBlank @Size(max = 120)
        String nome,

        @Schema(example = "Rio Tietê") @NotBlank @Size(max = 80)
        String rio,

        @Schema(example = "150") @NotNull @Positive @Max(5000)
        Integer cotaAtencaoCm,

        @Schema(example = "220") @NotNull @Positive @Max(5000)
        Integer cotaAlertaCm,

        @Schema(example = "300") @NotNull @Positive @Max(5000)
        Integer cotaTransbordoCm,

        @Schema(description = "Se não vier, a estação nasce ATIVA e, na atualização, mantém o status atual", example = "ATIVA")
        StatusEstacao status,

        @Schema(example = "1") @NotNull @Positive
        Long areaId
) {
}
