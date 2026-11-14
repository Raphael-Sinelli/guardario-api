package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// sem email, senha nem papel: o que não está no DTO não muda por esta rota (proteção contra mass assignment)
public record AtualizarPerfilRequest(
        @Schema(example = "Maria Aparecida") @NotBlank @Size(max = 120)
        String nome,

        @Schema(description = "DDD e número, só dígitos", example = "11987654321")
        @Pattern(regexp = "\\d{10,11}", message = "deve ter DDD e número, só dígitos (10 ou 11)")
        String telefone,

        @Schema(description = "Área (bairro) onde a pessoa mora; nulo deixa a pessoa sem área", example = "1") @Positive
        Long areaId
) {
    // normaliza antes do @Valid, como no cadastro
    public AtualizarPerfilRequest {
        nome = nome == null ? null : nome.strip();
    }
}
