package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.model.Abrigo;

public final class AbrigoMapper {

    private AbrigoMapper() {
    }

    public static AbrigoResponse toResponse(Abrigo abrigo) {
        return new AbrigoResponse(
                abrigo.getId(),
                abrigo.getNome(),
                abrigo.getEndereco(),
                abrigo.getCapacidade(),
                abrigo.getOcupacao(),
                abrigo.vagasDisponiveis(),
                abrigo.getAceitaPets(),
                abrigo.getAcessivelPcd(),
                abrigo.getTelefone(),
                abrigo.getLatitude(),
                abrigo.getLongitude(),
                abrigo.getStatus(),
                abrigo.getArea().getId());
    }
}
