package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.EstatisticaLeiturasResponse;
import br.com.fiap.guardario.dto.LeituraRegistradaResponse;
import br.com.fiap.guardario.dto.LeituraRequest;
import br.com.fiap.guardario.dto.LeituraResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LeituraService {

    LeituraRegistradaResponse registrar(Long estacaoId, LeituraRequest request);

    Page<LeituraResponse> listar(Long estacaoId, Pageable pageable);

    LeituraResponse buscarPorId(Long estacaoId, Long leituraId);

    EstatisticaLeiturasResponse estatisticas(Long estacaoId, int horas);
}
