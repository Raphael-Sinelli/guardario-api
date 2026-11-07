package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.validation.TamanhoEmBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "cidadao@guardario.com") @NotBlank @Email
        String email,

        @Schema(example = "Cidadao@123") @NotBlank @TamanhoEmBytes(max = 72)
        String senha
) {
    // tira o espaço que o autocompletar do celular deixa antes de o @Email validar
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
