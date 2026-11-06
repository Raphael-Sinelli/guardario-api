package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.EstacaoResponse;
import br.com.fiap.guardario.model.Estacao;

public final class EstacaoMapper {

    private EstacaoMapper() {
    }

    // usa area.getNome(): quem lista estações precisa buscar a área junto (@EntityGraph ou join fetch)
    public static EstacaoResponse toResponse(Estacao estacao) {
        return new EstacaoResponse(
                estacao.getId(),
                estacao.getCodigo(),
                estacao.getNome(),
                estacao.getRio(),
                estacao.getCotaAtencaoCm(),
                estacao.getCotaAlertaCm(),
                estacao.getCotaTransbordoCm(),
                estacao.getStatus(),
                estacao.getArea().getId(),
                estacao.getArea().getNome(),
                estacao.getUltimoNivelCm(),
                estacao.getUltimoRisco(),
                estacao.getUltimaLeituraEm());
    }
}
