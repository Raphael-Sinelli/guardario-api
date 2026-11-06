package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaRequest;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.RankingAreaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AreaService {

    Page<AreaResponse> listar(String uf, Pageable pageable);

    AreaResponse buscarPorId(Long id);

    List<RankingAreaResponse> ranking();

    AreaResponse criar(AreaRequest request);

    AreaResponse atualizar(Long id, AreaRequest request);

    void excluir(Long id);
}
