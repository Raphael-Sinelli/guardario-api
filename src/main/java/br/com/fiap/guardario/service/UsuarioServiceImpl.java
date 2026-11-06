package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.UsuarioMapper;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(UsuarioMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioMapper.toResponse(buscarEntidade(id));
    }

    @Override
    @Transactional
    public UsuarioResponse alterarPapel(Long id, Papel novoPapel) {
        Usuario usuario = buscarEntidade(id);
        // evita que o sistema fique sem administrador por engano
        if (usuario.getId().equals(SecurityUtils.usuarioLogado().getId())) {
            throw new RegraNegocioException("Um administrador não pode alterar o próprio papel");
        }
        usuario.setPapel(novoPapel);
        return UsuarioMapper.toResponse(usuario);
    }

    private Usuario buscarEntidade(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));
    }
}
