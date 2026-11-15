package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;

import java.util.List;

public record LoteLeiturasResponse(
        int quantidade,
        Long areaId,
        NivelRisco nivelArea,
        List<Long> alertasGerados
) {
}
