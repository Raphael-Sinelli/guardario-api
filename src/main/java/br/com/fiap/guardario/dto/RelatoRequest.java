package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.TipoRelato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

// prioridade, status e autor não vêm do cliente: o servidor calcula e pega o autor do token
public record RelatoRequest(
        @Schema(example = "RIO_SUBINDO") @NotNull
        TipoRelato tipo,

        @Schema(example = "A água já passou da segunda marca do pilar da ponte da Rua Capachós.")
        @NotBlank @Size(min = 10, max = 500)
        String descricao,

        @Schema(example = "-23.4987") @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @Schema(example = "-46.4295") @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        @Schema(example = "1") @NotNull @Positive
        Long areaId
) {
}
