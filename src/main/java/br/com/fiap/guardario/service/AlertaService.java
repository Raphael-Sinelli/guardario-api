package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AlertaEdicaoRequest;
import br.com.fiap.guardario.dto.AlertaRequest;
import br.com.fiap.guardario.dto.AlertaResponse;
import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.StatusAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AlertaService {

    Page<AlertaResponse> listar(StatusAlerta status, Pageable pageable);

    List<AlertaResponse> ativos();

    AlertaResponse buscarPorId(Long id);

    AlertaResponse criarManual(AlertaRequest request);

    AlertaResponse editar(Long id, AlertaEdicaoRequest request);

    AlertaResponse encerrar(Long id);

    void excluir(Long id);

    /**
     * Chamado pelo registro de leitura. Devolve o id do alerta criado,
     * ou null se a área já tinha alerta ativo de nível igual ou maior.
     */
    Long gerarAutomatico(Estacao estacao, NivelRisco risco, Integer nivelCm);
}
