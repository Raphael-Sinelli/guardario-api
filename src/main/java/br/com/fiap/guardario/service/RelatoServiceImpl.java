package br.com.fiap.guardario.service;

import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.dto.AlterarStatusRelatoRequest;
import br.com.fiap.guardario.dto.RelatoRequest;
import br.com.fiap.guardario.dto.RelatoResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.RelatoMapper;
import br.com.fiap.guardario.model.*;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.RelatoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.SecurityUtils;
import br.com.fiap.guardario.specification.RelatoSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/*
 * Relatos não entram em cache: têm dado pessoal, são por usuário e mudam o tempo todo.
 * Regra de posse (RN09) fica aqui no service: sempre 404 antes de 403.
 */
@Service
@RequiredArgsConstructor
public class RelatoServiceImpl implements RelatoService {

    private final RelatoRepository relatoRepository;
    private final AreaRiscoRepository areaRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public RelatoResponse criar(RelatoRequest request) {
        Usuario autor = usuarioRepository.getReferenceById(SecurityUtils.usuarioLogado().getId());
        AreaRisco area = buscarArea(request.areaId());

        Relato relato = new Relato();
        preencher(relato, request, area);
        relato.setAutor(autor);
        relato.definirPrioridade(area.getNivelAtual()); // RN06: calculada pelo servidor
        return RelatoMapper.toResponse(relatoRepository.save(relato));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelatoResponse> meus(Pageable pageable) {
        Long autorId = SecurityUtils.usuarioLogado().getId();
        return relatoRepository.findByAutorId(autorId, pageable).map(RelatoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelatoResponse> listar(TipoRelato tipo, StatusRelato status, Long areaId, Integer prioridadeMinima,
                                       Instant de, Instant ate, Pageable pageable) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new CampoInvalidoException("de", "precisa ser anterior ao parâmetro 'ate'");
        }
        var filtro = RelatoSpecification.comFiltros(tipo, status, areaId, prioridadeMinima, de, ate);
        return relatoRepository.findAll(filtro, pageable).map(RelatoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RelatoResponse buscarPorId(Long id) {
        Relato relato = buscarEntidade(id);
        Usuario usuario = SecurityUtils.usuarioLogado();
        if (!relato.pertenceA(usuario.getId()) && !SecurityUtils.isAgenteOuAdmin()) {
            throw new AccessDeniedException("Você só pode ver os seus próprios relatos");
        }
        return RelatoMapper.toResponse(relato);
    }

    @Override
    @Transactional
    public RelatoResponse atualizar(Long id, RelatoRequest request) {
        Relato relato = buscarEntidade(id);
        if (!relato.pertenceA(SecurityUtils.usuarioLogado().getId())) {
            throw new AccessDeniedException("Só o autor pode editar o relato");
        }
        if (!relato.isAberto()) {
            throw new RegraNegocioException("O relato já está " + relato.getStatus() + " e não pode mais ser editado");
        }
        AreaRisco area = buscarArea(request.areaId());
        preencher(relato, request, area);
        relato.definirPrioridade(area.getNivelAtual());
        relato.marcarAtualizado();
        return RelatoMapper.toResponse(relato);
    }

    // RN07: a máquina de estados fica na entidade (Relato.alterarStatus); AGENTE ou ADMIN, conferido no controller
    @Override
    @Transactional
    public RelatoResponse alterarStatus(Long id, AlterarStatusRelatoRequest request) {
        Relato relato = buscarEntidade(id);
        relato.alterarStatus(request.status(), request.observacao());
        return RelatoMapper.toResponse(relato);
    }

    // RN09: o cidadão apaga só o que é dele e só enquanto está ABERTO; o ADMIN apaga qualquer um
    @Override
    @Transactional
    public void excluir(Long id) {
        Relato relato = buscarEntidade(id);
        Usuario usuario = SecurityUtils.usuarioLogado();

        if (usuario.getPapel() != Papel.ADMIN) {
            if (!relato.pertenceA(usuario.getId())) {
                throw new AccessDeniedException("Você só pode excluir os seus próprios relatos");
            }
            if (!relato.isAberto()) {
                throw new RegraNegocioException("Relato em atendimento ou finalizado não pode ser excluído");
            }
        }
        relatoRepository.delete(relato);
    }

    private Relato buscarEntidade(Long id) {
        return relatoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("relato", id));
    }

    private AreaRisco buscarArea(Long areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("área", areaId));
    }

    private void preencher(Relato relato, RelatoRequest request, AreaRisco area) {
        relato.setTipo(request.tipo());
        relato.setDescricao(request.descricao().trim());
        relato.setLatitude(request.latitude());
        relato.setLongitude(request.longitude());
        relato.setArea(area);
    }
}
