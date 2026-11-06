package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.RelatoResponse;
import br.com.fiap.guardario.model.Relato;

public final class RelatoMapper {

    private RelatoMapper() {
    }

    public static RelatoResponse toResponse(Relato relato) {
        return new RelatoResponse(
                relato.getId(),
                relato.getTipo(),
                relato.getDescricao(),
                relato.getLatitude(),
                relato.getLongitude(),
                relato.getPrioridade(),
                relato.getStatus(),
                relato.getObservacaoAgente(),
                relato.getAutor().getId(),
                relato.getArea().getId(),
                relato.getCriadoEm(),
                relato.getAtualizadoEm());
    }
}
