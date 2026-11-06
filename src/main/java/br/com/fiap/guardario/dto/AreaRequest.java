package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

// o nível atual não entra aqui: quem calcula é o servidor, a partir das leituras
public record AreaRequest(
        @Schema(example = "Jardim Pantanal") @NotBlank @Size(max = 120)
        String nome,

        @Schema(example = "São Paulo") @NotBlank @Size(max = 80)
        String cidade,

        @Schema(example = "SP") @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "deve ter duas letras, como SP")
        String uf,

        @Schema(example = "48000") @PositiveOrZero
        Integer populacaoEstimada,

        @Schema(example = "-23.4985") @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @Schema(example = "-46.4290") @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude
) {
}
