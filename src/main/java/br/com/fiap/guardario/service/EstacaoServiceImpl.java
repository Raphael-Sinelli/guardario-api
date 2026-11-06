package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.EstacaoRequest;
import br.com.fiap.guardario.dto.EstacaoResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.EstacaoMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.model.StatusEstacao;
import br.com.fiap.guardario.repository.AlertaRepository;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.EstacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EstacaoServiceImpl implements EstacaoService {

    private final EstacaoRepository estacaoRepository;
    private final AreaRiscoRepository areaRepository;
    private final AlertaRepository alertaRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<EstacaoResponse> listar(Long areaId, Pageable pageable) {
        Page<Estacao> pagina = areaId == null
                ? estacaoRepository.findAll(pageable)
                : estacaoRepository.findByAreaId(areaId, pageable);
        return pagina.map(EstacaoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EstacaoResponse buscarPorId(Long id) {
        return EstacaoMapper.toResponse(buscarEntidade(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstacaoResponse> criticas() {
        return estacaoRepository.buscarCriticas(List.of(NivelRisco.ALERTA, NivelRisco.EMERGENCIA)).stream()
                .map(EstacaoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EstacaoResponse criar(EstacaoRequest request) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (estacaoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new RegraNegocioException("Já existe uma estação com o código " + codigo);
        }
        Estacao estacao = new Estacao();
        estacao.setCodigo(codigo);
        preencher(estacao, request);
        return EstacaoMapper.toResponse(estacaoRepository.save(estacao));
    }

    @Override
    @Transactional
    public EstacaoResponse atualizar(Long id, EstacaoRequest request) {
        Estacao estacao = buscarEntidade(id);
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (!codigo.equalsIgnoreCase(estacao.getCodigo()) && estacaoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new RegraNegocioException("Já existe uma estação com o código " + codigo);
        }
        estacao.setCodigo(codigo);
        preencher(estacao, request); // último nível e último risco não mudam aqui: vêm das leituras
        return EstacaoMapper.toResponse(estacao);
    }

    @Override
    @Transactional
    public void excluir(Long id) {
        Estacao estacao = buscarEntidade(id);
        // alerta é histórico de decisão da Defesa Civil: com alerta no histórico, a estação é desativada, não apagada
        if (alertaRepository.existsByEstacaoId(id)) {
            throw new RegraNegocioException("A estação tem alertas no histórico. Mude o status para INATIVA em vez de excluir");
        }
        estacaoRepository.delete(estacao); // as leituras vão junto pelo cascade
    }

    private Estacao buscarEntidade(Long id) {
        return estacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("estação", id));
    }

    private void preencher(Estacao estacao, EstacaoRequest request) {
        AreaRisco area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));
        estacao.setNome(request.nome().trim());
        estacao.setRio(request.rio().trim());
        estacao.setCotaAtencaoCm(request.cotaAtencaoCm());
        estacao.setCotaAlertaCm(request.cotaAlertaCm());
        estacao.setCotaTransbordoCm(request.cotaTransbordoCm());
        estacao.setStatus(request.status() != null ? request.status() : StatusEstacao.ATIVA);
        estacao.setArea(area);
    }
}
