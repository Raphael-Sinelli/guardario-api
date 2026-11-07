package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.validation.TamanhoEmBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// não tem campo de papel: o cadastro público sempre cria CIDADAO (RN08)
public record RegisterRequest(
        @Schema(example = "Maria Aparecida") @NotBlank @Size(max = 120)
        String nome,

        // o @Email aceita "x@y"; o regexp exige um ponto no domínio
        @Schema(example = "maria@email.com") @NotBlank @Size(max = 150)
        @Email(regexp = ".+@.+\\..+", message = "deve ser um email válido, como maria@email.com")
        String email,

        // o BCrypt só considera os primeiros 72 bytes: o @Size dá o limite que aparece no Swagger, e o @TamanhoEmBytes
        // pega a senha com acento, que tem menos de 72 caracteres mas passa de 72 bytes
        @Schema(example = "Senha@123") @NotBlank @Size(min = 8, max = 72) @TamanhoEmBytes(max = 72)
        String senha,

        @Schema(description = "DDD e número, só dígitos", example = "11987654321")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter DDD e número, só dígitos (10 ou 11)")
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
