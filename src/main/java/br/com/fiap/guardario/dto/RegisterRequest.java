package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// não tem campo de papel: o cadastro público sempre cria CIDADAO (RN08)
public record RegisterRequest(
        @Schema(example = "Maria Aparecida") @NotBlank @Size(max = 120)
        String nome,

        @Schema(example = "maria@email.com") @NotBlank @Email @Size(max = 150)
        String email,

        // o BCrypt só considera os primeiros 72 bytes
        @Schema(example = "Senha@123") @NotBlank @Size(min = 8, max = 72)
        String senha,

        @Schema(example = "11987654321") @Size(max = 20)
        String telefone,

        @Schema(description = "Área (bairro) onde a pessoa mora", example = "1") @Positive
        Long areaId
) {
    // normaliza antes do @Valid; a senha fica como veio, porque espaço pode fazer parte dela
    public RegisterRequest {
        nome = nome == null ? null : nome.strip();
        email = email == null ? null : email.strip();
    }
}
