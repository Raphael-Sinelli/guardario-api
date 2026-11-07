package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.EstacaoRequest;
import br.com.fiap.guardario.dto.EstacaoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EstacaoService {

    Page<EstacaoResponse> listar(Long areaId, Pageable pageable);

    EstacaoResponse buscarPorId(Long id);

    List<EstacaoResponse> criticas();

    EstacaoResponse criar(EstacaoRequest request);

    EstacaoResponse atualizar(Long id, EstacaoRequest request);

    void excluir(Long id);

    // ids, entre os informados, das estações com alerta no histórico, que não podem ser excluídas
    Set<Long> idsComAlertas(Collection<Long> ids);
}
