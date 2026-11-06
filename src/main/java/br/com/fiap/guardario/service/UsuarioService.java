package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.model.Papel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    Page<UsuarioResponse> listar(Pageable pageable);

    UsuarioResponse buscarPorId(Long id);

    UsuarioResponse alterarPapel(Long id, Papel novoPapel);
}
