package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LoteLeiturasRequest(
        @Schema(description = "De 1 a 100 leituras, todas com medidaEm")
        @NotEmpty @Size(max = 100)
        List<@Valid LeituraRequest> leituras
) {
}
