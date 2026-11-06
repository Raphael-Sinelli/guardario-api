package br.com.fiap.guardario.dto;

import java.time.Instant;

public record ErrorCustomResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
