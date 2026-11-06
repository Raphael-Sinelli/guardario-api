package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.LeituraResponse;
import br.com.fiap.guardario.model.Leitura;

public final class LeituraMapper {

    private LeituraMapper() {
    }

    public static LeituraResponse toResponse(Leitura leitura) {
        return new LeituraResponse(
                leitura.getId(),
                leitura.getEstacao().getId(),
                leitura.getNivelCm(),
                leitura.getChuvaMmH(),
                leitura.getTemperaturaC(),
                leitura.getUmidadePct(),
                leitura.getNivelRisco(),
                leitura.getMedidaEm());
    }
}
