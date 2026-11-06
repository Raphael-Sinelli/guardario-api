package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.model.AreaRisco;

public final class AreaMapper {

    private AreaMapper() {
    }

    public static AreaResponse toResponse(AreaRisco area) {
        return new AreaResponse(
                area.getId(),
                area.getNome(),
                area.getCidade(),
                area.getUf(),
                area.getPopulacaoEstimada(),
                area.getLatitude(),
                area.getLongitude(),
                area.getNivelAtual(),
                area.getNivelAtual().getOrientacao());
    }
}
