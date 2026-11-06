package br.com.fiap.guardario.dto;

import br.com.fiap.guardario.model.NivelRisco;

/**
 * Resposta do registro de leitura: além da leitura, conta o efeito dela na área
 * e o id do alerta automático, quando a leitura gerou um.
 */
public record LeituraRegistradaResponse(
        LeituraResponse leitura,
        Long areaId,
        NivelRisco nivelArea,
        boolean nivelAreaAlterado,
        Long alertaGeradoId
) {
}
