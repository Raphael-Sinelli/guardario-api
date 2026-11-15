package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.validation.TamanhoEmBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// o usuário vem do token: o corpo não tem id nem email, só as duas senhas
public record AlterarSenhaRequest(
        @Schema(example = "Senha@123") @NotBlank
        String senhaAtual,

        // mesmas regras da senha do cadastro: o BCrypt só considera os primeiros 72 bytes
        @Schema(example = "NovaSenha@456") @NotBlank @Size(min = 8, max = 72) @TamanhoEmBytes(max = 72)
        String novaSenha
) {
}
