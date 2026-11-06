package br.com.fiap.guardario.dto;

// montado pela consulta JPQL com count, avg, min e max (LeituraRepository.estatisticas)
public record EstatisticaLeiturasResponse(
        Long quantidadeLeituras,
        Double nivelMedioCm,
        Integer nivelMinimoCm,
        Integer nivelMaximoCm,
        Double chuvaMaximaMmH
) {
}
