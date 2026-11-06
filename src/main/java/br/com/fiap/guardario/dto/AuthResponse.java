package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.Papel;

public record AuthResponse(
        String token,
        String tipoToken,
        long expiraEmSegundos,
        String email,
        Papel papel
) {
}
