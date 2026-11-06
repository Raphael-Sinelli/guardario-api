package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.AlertaResponse;
import br.com.fiap.guardario.model.Alerta;

public final class AlertaMapper {

    private AlertaMapper() {
    }

    public static AlertaResponse toResponse(Alerta alerta) {
        return new AlertaResponse(
                alerta.getId(),
                alerta.getNivel(),
                alerta.getTitulo(),
                alerta.getMensagem(),
                alerta.getNivel().getOrientacao(),
                alerta.getStatus(),
                alerta.getOrigem(),
                alerta.getEmitidoEm(),
                alerta.getEncerradoEm(),
                alerta.getArea().getId(),
                alerta.getArea().getNome(),
                alerta.getEstacao() != null ? alerta.getEstacao().getId() : null,
                alerta.getEmitidoPor() != null ? alerta.getEmitidoPor().getId() : null,
                alerta.getEncerradoPor() != null ? alerta.getEncerradoPor().getId() : null);
    }
}
