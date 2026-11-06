package br.com.fiap.guardario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// no PUT só o texto muda; nível e área definem o alerta e não são editáveis
public record AlertaEdicaoRequest(
        @Schema(example = "Rio Tietê acima da cota de alerta") @NotBlank @Size(max = 150)
        String titulo,

        @Schema(example = "Abrigos com vaga: EMEF Jardim Pantanal e Igreja São Miguel.") @NotBlank @Size(max = 1000)
        String mensagem
) {
}
