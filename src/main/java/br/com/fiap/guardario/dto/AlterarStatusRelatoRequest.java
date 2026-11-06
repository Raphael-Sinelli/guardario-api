package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.StatusRelato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlterarStatusRelatoRequest(
        @Schema(example = "EM_ATENDIMENTO") @NotNull
        StatusRelato status,

        @Schema(example = "Equipe de resgate a caminho.") @Size(max = 500)
        String observacao
) {
}
