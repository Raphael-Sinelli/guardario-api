package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "cidadao@guardario.com") @NotBlank @Email
        String email,

        @Schema(example = "Cidadao@123") @NotBlank
        String senha
) {
    // tira o espaço que o autocompletar do celular deixa antes de o @Email validar
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
