package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.EstatisticaLeiturasResponse;
import br.com.fiap.guardario.dto.LeituraRegistradaResponse;
import br.com.fiap.guardario.dto.LeituraRequest;
import br.com.fiap.guardario.dto.LeituraResponse;
import br.com.fiap.guardario.dto.LoteLeiturasRequest;
import br.com.fiap.guardario.dto.LoteLeiturasResponse;
import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.LeituraMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.Leitura;
import br.com.fiap.guardario.model.NivelRisco;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.EstacaoRepository;
import br.com.fiap.guardario.repository.LeituraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeituraServiceImpl implements LeituraService {

    private final EstacaoRepository estacaoRepository;
    private final AreaRiscoRepository areaRepository;
    private final LeituraRepository leituraRepository;
    private final AlertaService alertaService;
    private final RelatoService relatoService;

    /*
     * Fluxo da leitura que chega da estação (pela ponte MQTT ou por um agente):
     * classifica o risco (RN01), recalcula o nível da área (RN03) e, se for ALERTA ou pior,
     * pede um alerta automático (RN02). O cache da área só é limpo quando o nível dela muda.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", key = "#result.areaId()", condition = "#result.nivelAreaAlterado()")
    public LeituraRegistradaResponse registrar(Long estacaoId, LeituraRequest request) {
        // trava a estação e depois a área (sempre nessa ordem): leituras simultâneas da mesma área entram uma de cada vez
        Estacao estacao = estacaoRepository.buscarComTrava(estacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("estação", estacaoId));
        if (!estacao.isAtiva()) {
            throw new RegraNegocioException("A estação " + estacao.getCodigo() + " está " + estacao.getStatus() + " e não recebe leituras");
        }
        areaRepository.buscarComTrava(estacao.getArea().getId());
        return registrarNaEstacao(estacao, request);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", key = "#result.areaId()")
    public LoteLeiturasResponse registrarLote(Long estacaoId, LoteLeiturasRequest request) {
        Estacao estacao = estacaoRepository.buscarComTrava(estacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("estação", estacaoId));
        if (!estacao.isAtiva()) {
            throw new RegraNegocioException("A estação " + estacao.getCodigo() + " está " + estacao.getStatus() + " e não recebe leituras");
        }
        areaRepository.buscarComTrava(estacao.getArea().getId());

        List<LeituraRequest> leituras = request.leituras();
        for (int i = 0; i < leituras.size(); i++) {
            if (leituras.get(i).medidaEm() == null) {
                throw new CampoInvalidoException("leituras[" + i + "].medidaEm",
                        "é obrigatório no lote: sem ele não há como pôr as leituras em ordem");
            }
        }

        List<Long> alertasGerados = new ArrayList<>();
        LeituraRegistradaResponse ultima = null;
        for (LeituraRequest leitura : leituras.stream().sorted(Comparator.comparing(LeituraRequest::medidaEm)).toList()) {
            ultima = registrarNaEstacao(estacao, leitura);
            if (ultima.alertaGeradoId() != null) {
                alertasGerados.add(ultima.alertaGeradoId());
            }
        }
        return new LoteLeiturasResponse(leituras.size(), ultima.areaId(), ultima.nivelArea(), alertasGerados);
    }

    private LeituraRegistradaResponse registrarNaEstacao(Estacao estacao, LeituraRequest request) {
        if (request.medidaEm() != null) {
            Instant medidaEm = request.medidaEm().truncatedTo(ChronoUnit.MICROS);
            if (leituraRepository.existsByEstacaoIdAndMedidaEmAndNivelCmAndChuvaMmH(
                    estacao.getId(), medidaEm, request.nivelCm(), request.chuvaMmH())) {
                throw new RegraNegocioException("Leitura já registrada para esta estação em " + medidaEm);
            }
        }

        NivelRisco risco = estacao.classificar(request.nivelCm(), request.chuvaMmH());

        Leitura leitura = new Leitura();
        leitura.setNivelCm(request.nivelCm());
        leitura.setChuvaMmH(request.chuvaMmH());
        leitura.setTemperaturaC(request.temperaturaC());
        leitura.setUmidadePct(request.umidadePct());
        leitura.setNivelRisco(risco);
        // a validação aceita o relógio da estação até 1 minuto adiantado, mas grava no máximo a hora do servidor:
        // gravada no futuro, ela faria a próxima leitura (sem horário) parecer atrasada e segurar o alerta
        Instant agora = Instant.now();
        leitura.setMedidaEm(request.medidaEm() == null || request.medidaEm().isAfter(agora) ? agora : request.medidaEm());

        boolean ehAMaisRecente = estacao.registrarLeitura(leitura);
        leituraRepository.save(leitura);

        AreaRisco area = estacao.getArea();
        NivelRisco nivelAnterior = area.getNivelAtual();
        NivelRisco nivelArea = area.recalcularNivel();
        if (nivelArea != nivelAnterior) {
            relatoService.reavaliarPrioridades(area); // RN06: os relatos em aberto acompanham o nível novo
        }

        // leitura atrasada não gera alerta: o alerta olha para a situação de agora
        Long alertaGeradoId = null;
        if (ehAMaisRecente && risco.maiorOuIgualA(NivelRisco.ALERTA)) {
            alertaGeradoId = alertaService.gerarAutomatico(estacao, risco, request.nivelCm());
        }

        return new LeituraRegistradaResponse(
                LeituraMapper.toResponse(leitura), area.getId(), nivelArea, nivelArea != nivelAnterior, alertaGeradoId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeituraResponse> listar(Long estacaoId, Pageable pageable) {
        garantirQueEstacaoExiste(estacaoId);
        return leituraRepository.findByEstacaoId(estacaoId, pageable).map(LeituraMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LeituraResponse buscarPorId(Long estacaoId, Long leituraId) {
        garantirQueEstacaoExiste(estacaoId);
        return leituraRepository.findByIdAndEstacaoId(leituraId, estacaoId)
                .map(LeituraMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("leitura", leituraId));
    }

    @Override
    @Transactional(readOnly = true)
    public EstatisticaLeiturasResponse estatisticas(Long estacaoId, int horas) {
        garantirQueEstacaoExiste(estacaoId);
        Instant desde = Instant.now().minus(horas, ChronoUnit.HOURS);
        return leituraRepository.estatisticas(estacaoId, desde);
    }

    private void garantirQueEstacaoExiste(Long estacaoId) {
        if (!estacaoRepository.existsById(estacaoId)) {
            throw new ResourceNotFoundException("estação", estacaoId);
        }
    }
}
