package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.AreaMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// o usuário vem sempre do token: não existe rota para ver ou mudar as áreas seguidas de outra pessoa
@Service
@RequiredArgsConstructor
public class AreaSeguidaServiceImpl implements AreaSeguidaService {

    private static final Collator ORDEM_DE_NOME = Collator.getInstance(Locale.forLanguageTag("pt-BR"));

    private final UsuarioRepository usuarioRepository;
    private final AreaRiscoRepository areaRepository;

    @Override
    @Transactional
    public void seguir(Long areaId) {
        AreaRisco area = buscarArea(areaId); // 404 antes de qualquer outra regra
        usuarioLogadoComTrava().seguir(area);
    }

    @Override
    @Transactional
    public void deixarDeSeguir(Long areaId) {
        AreaRisco area = buscarArea(areaId);
        usuarioLogadoComTrava().deixarDeSeguir(area);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponse> listar() {
        Long id = SecurityUtils.usuarioLogado().getId();
        return usuarioRepository.findComAreasSeguidasById(id)
                .map(usuario -> usuario.getAreasSeguidas().stream()
                        .sorted(Comparator.comparing(AreaRisco::getNome, ORDEM_DE_NOME))
                        .map(AreaMapper::toResponse)
                        .toList())
                .orElse(List.of());
    }

    private AreaRisco buscarArea(Long areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("área", areaId));
    }

    // recarrega pelo id do token, já travado: o usuário do filtro JWT foi carregado fora desta transação
    private Usuario usuarioLogadoComTrava() {
        Long id = SecurityUtils.usuarioLogado().getId();
        return usuarioRepository.buscarComTrava(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));
    }
}
