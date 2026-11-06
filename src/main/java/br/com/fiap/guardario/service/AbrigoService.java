package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AbrigoRequest;
import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.model.StatusAbrigo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AbrigoService {

    Page<AbrigoResponse> listar(Long areaId, Boolean aceitaPets, Boolean acessivelPcd, Boolean comVagas,
                                StatusAbrigo status, Pageable pageable);

    List<AbrigoResponse> disponiveis();

    AbrigoResponse buscarPorId(Long id);

    AbrigoResponse criar(AbrigoRequest request);

    AbrigoResponse atualizar(Long id, AbrigoRequest request);

    AbrigoResponse alterarStatus(Long id, StatusAbrigo status);

    AbrigoResponse registrarEntrada(Long id, int pessoas);

    AbrigoResponse registrarSaida(Long id, int pessoas);

    void excluir(Long id);
}
