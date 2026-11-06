package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AlertaEdicaoRequest;
import br.com.fiap.guardario.dto.AlertaRequest;
import br.com.fiap.guardario.dto.AlertaResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.AlertaMapper;
import br.com.fiap.guardario.model.*;
import br.com.fiap.guardario.repository.AlertaRepository;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.EstacaoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

    private final AlertaRepository alertaRepository;
    private final AreaRiscoRepository areaRepository;
    private final EstacaoRepository estacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public Page<AlertaResponse> listar(StatusAlerta status, Pageable pageable) {
        Page<Alerta> pagina = status == null
                ? alertaRepository.findAll(pageable)
                : alertaRepository.findByStatus(status, pageable);
        return pagina.map(AlertaMapper::toResponse);
    }

    /*
     * Consulta mais chamada da API: o app pergunta os alertas ativos toda vez que abre.
     * Fica em cache e só é limpa quando um alerta nasce, muda ou é encerrado.
     * Ordem: mais grave primeiro, depois o mais recente.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "alertasAtivos")
    public List<AlertaResponse> ativos() {
        return alertaRepository.findByStatusOrderByEmitidoEmDesc(StatusAlerta.ATIVO).stream()
                .sorted(Comparator.comparingInt((Alerta a) -> a.getNivel().getPeso()).reversed())
                .map(AlertaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AlertaResponse buscarPorId(Long id) {
        return AlertaMapper.toResponse(buscarEntidade(id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "alertasAtivos", allEntries = true)
    public AlertaResponse criarManual(AlertaRequest request) {
        if (request.nivel() == NivelRisco.NORMAL) {
            throw new IllegalArgumentException("Um alerta precisa ter nível ATENCAO, ALERTA ou EMERGENCIA");
        }
        AreaRisco area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));

        Estacao estacao = null;
        if (request.estacaoId() != null) {
            estacao = estacaoRepository.findById(request.estacaoId())
                    .orElseThrow(() -> new ResourceNotFoundException("estação", request.estacaoId()));
            if (!estacao.getArea().getId().equals(area.getId())) {
                throw new IllegalArgumentException("A estação informada não pertence à área do alerta");
            }
        }

        List<Alerta> ativosDaArea = alertaRepository.findByAreaIdAndStatus(area.getId(), StatusAlerta.ATIVO);
        if (existeAtivoIgualOuMaior(ativosDaArea, request.nivel())) {
            throw new RegraNegocioException("A área já tem um alerta ativo de nível igual ou maior");
        }
        substituirMenores(ativosDaArea);

        Usuario emissor = usuarioRepository.getReferenceById(SecurityUtils.usuarioLogado().getId());
        Alerta alerta = novoAlerta(area, estacao, request.nivel(), OrigemAlerta.MANUAL,
                request.titulo().trim(), request.mensagem().trim());
        alerta.setEmitidoPor(emissor);
        return AlertaMapper.toResponse(salvarEContar(alerta));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "alertasAtivos", allEntries = true)
    public AlertaResponse editar(Long id, AlertaEdicaoRequest request) {
        Alerta alerta = buscarEntidade(id);
        if (!alerta.isAtivo()) {
            throw new RegraNegocioException("Alerta encerrado não pode ser editado");
        }
        alerta.setTitulo(request.titulo().trim());
        alerta.setMensagem(request.mensagem().trim());
        return AlertaMapper.toResponse(alerta);
    }

    // RN04: o encerramento é sempre decisão humana (AGENTE ou ADMIN, conferido no controller)
    @Override
    @Transactional
    @CacheEvict(cacheNames = "alertasAtivos", allEntries = true)
    public AlertaResponse encerrar(Long id) {
        Alerta alerta = buscarEntidade(id);
        Usuario responsavel = usuarioRepository.getReferenceById(SecurityUtils.usuarioLogado().getId());
        alerta.encerrar(responsavel);
        return AlertaMapper.toResponse(alerta);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "alertasAtivos", allEntries = true)
    public void excluir(Long id) {
        alertaRepository.delete(buscarEntidade(id));
    }

    // RN02: só abre alerta se não houver um ativo de nível igual ou maior; os de nível menor são substituídos
    @Override
    @Transactional
    @CacheEvict(cacheNames = "alertasAtivos", allEntries = true, condition = "#result != null")
    public Long gerarAutomatico(Estacao estacao, NivelRisco risco, Integer nivelCm) {
        AreaRisco area = estacao.getArea();
        List<Alerta> ativosDaArea = alertaRepository.findByAreaIdAndStatus(area.getId(), StatusAlerta.ATIVO);
        if (existeAtivoIgualOuMaior(ativosDaArea, risco)) {
            return null;
        }
        substituirMenores(ativosDaArea);

        String titulo = risco + " em " + area.getNome() + ": " + estacao.getRio() + " com " + nivelCm + " cm";
        String mensagem = "A estação " + estacao.getCodigo() + " (" + estacao.getNome() + ") mediu " + nivelCm
                + " cm. Cota de alerta: " + estacao.getCotaAlertaCm() + " cm; cota de transbordo: "
                + estacao.getCotaTransbordoCm() + " cm. " + risco.getOrientacao();

        Alerta alerta = novoAlerta(area, estacao, risco, OrigemAlerta.AUTOMATICO, titulo, mensagem);
        return salvarEContar(alerta).getId();
    }

    private boolean existeAtivoIgualOuMaior(List<Alerta> ativos, NivelRisco nivel) {
        return ativos.stream().anyMatch(a -> a.getNivel().maiorOuIgualA(nivel));
    }

    // alerta substituído por um mais grave é encerrado sem responsável humano
    private void substituirMenores(List<Alerta> ativosDaArea) {
        ativosDaArea.forEach(a -> a.encerrar(null));
    }

    private Alerta novoAlerta(AreaRisco area, Estacao estacao, NivelRisco nivel, OrigemAlerta origem,
                              String titulo, String mensagem) {
        Alerta alerta = new Alerta();
        alerta.setArea(area);
        alerta.setEstacao(estacao);
        alerta.setNivel(nivel);
        alerta.setOrigem(origem);
        alerta.setTitulo(titulo.length() > 150 ? titulo.substring(0, 150) : titulo);
        alerta.setMensagem(mensagem);
        return alerta;
    }

    // métrica própria, visível em /actuator/metrics/guardario.alertas.emitidos
    private Alerta salvarEContar(Alerta alerta) {
        Alerta salvo = alertaRepository.save(alerta);
        meterRegistry.counter("guardario.alertas.emitidos",
                "origem", alerta.getOrigem().name(),
                "nivel", alerta.getNivel().name()).increment();
        return salvo;
    }

    private Alerta buscarEntidade(Long id) {
        return alertaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("alerta", id));
    }
}
