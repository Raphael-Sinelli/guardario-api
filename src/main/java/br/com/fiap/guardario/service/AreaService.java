package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaRequest;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.RankingAreaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AreaService {

    Page<AreaResponse> listar(String uf, Pageable pageable);

    AreaResponse buscarPorId(Long id);

    List<RankingAreaResponse> ranking();

    // ids, entre os informados, das áreas que têm vínculos e não podem ser excluídas
    Set<Long> idsComVinculos(Collection<Long> ids);

    AreaResponse criar(AreaRequest request);

    AreaResponse atualizar(Long id, AreaRequest request);

    void excluir(Long id);

    // solta o risco velho das áreas cujas estações pararam de mandar leitura; true se o nível de alguma área mudou
    boolean reavaliarNiveis();
}
