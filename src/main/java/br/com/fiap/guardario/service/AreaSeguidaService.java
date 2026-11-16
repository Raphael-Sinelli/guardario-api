package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaResponse;

import java.util.List;
import java.util.Set;

// áreas que o usuário logado acompanha além da área do cadastro (N:M entre Usuario e AreaRisco)
public interface AreaSeguidaService {

    void seguir(Long areaId);

    void deixarDeSeguir(Long areaId);

    List<AreaResponse> listar();

    // ids das áreas que o usuário logado segue (vazio sem login): é o que decide o link seguir ou deixar de seguir
    Set<Long> idsSeguidos();
}
