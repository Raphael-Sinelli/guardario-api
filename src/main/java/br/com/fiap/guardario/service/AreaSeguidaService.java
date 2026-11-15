package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaResponse;

import java.util.List;

// áreas que o usuário logado acompanha além da área do cadastro (N:M entre Usuario e AreaRisco)
public interface AreaSeguidaService {

    void seguir(Long areaId);

    void deixarDeSeguir(Long areaId);

    List<AreaResponse> listar();
}
