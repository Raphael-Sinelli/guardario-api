package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AlterarStatusRelatoRequest;
import br.com.fiap.guardario.dto.RelatoRequest;
import br.com.fiap.guardario.dto.RelatoResponse;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.TipoRelato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface RelatoService {

    RelatoResponse criar(RelatoRequest request);

    Page<RelatoResponse> meus(Pageable pageable);

    Page<RelatoResponse> listar(TipoRelato tipo, StatusRelato status, Long areaId, Integer prioridadeMinima,
                                Instant de, Instant ate, Pageable pageable);

    RelatoResponse buscarPorId(Long id);

    RelatoResponse atualizar(Long id, RelatoRequest request);

    RelatoResponse alterarStatus(Long id, AlterarStatusRelatoRequest request);

    void excluir(Long id);
}
