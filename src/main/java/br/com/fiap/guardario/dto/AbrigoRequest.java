package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.validation.OcupacaoDentroDaCapacidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@OcupacaoDentroDaCapacidade
public record AbrigoRequest(
        @Schema(example = "EMEF Jardim Pantanal") @NotBlank @Size(max = 120)
        String nome,

        @Schema(example = "Rua Capachós, 120") @NotBlank @Size(max = 200)
        String endereco,

        @Schema(example = "120") @NotNull @Positive @Max(5000)
        Integer capacidade,

        @Schema(description = "Pessoas já acolhidas. Se não vier, começa em 0 no cadastro e mantém o valor atual na atualização", example = "0") @PositiveOrZero
        Integer ocupacao,

        @Schema(example = "true") @NotNull
        Boolean aceitaPets,

        @Schema(example = "true") @NotNull
        Boolean acessivelPcd,

        @Schema(example = "1125550101") @Size(max = 20)
        String telefone,

        @Schema(example = "-23.4991") @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @Schema(example = "-46.4302") @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        @Schema(example = "1") @NotNull @Positive
        Long areaId
) {
}
