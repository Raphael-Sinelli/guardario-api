package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.EstatisticaLeiturasResponse;
import br.com.fiap.guardario.dto.LeituraRegistradaResponse;
import br.com.fiap.guardario.dto.LeituraRequest;
import br.com.fiap.guardario.dto.LeituraResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.LeituraMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.Estacao;
import br.com.fiap.guardario.model.Leitura;
import br.com.fiap.guardario.model.NivelRisco;
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

@Service
@RequiredArgsConstructor
public class LeituraServiceImpl implements LeituraService {

    private final EstacaoRepository estacaoRepository;
    private final LeituraRepository leituraRepository;
    private final AlertaService alertaService;

    /*
     * Fluxo da leitura que chega da estação (pela ponte MQTT ou por um agente):
     * classifica o risco (RN01), recalcula o nível da área (RN03) e, se for ALERTA ou pior,
     * pede um alerta automático (RN02). O cache da área só é limpo quando o nível dela muda.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", key = "#result.areaId()", condition = "#result.nivelAreaAlterado()")
    public LeituraRegistradaResponse registrar(Long estacaoId, LeituraRequest request) {
        Estacao estacao = estacaoRepository.findById(estacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("estação", estacaoId));
        if (!estacao.isAtiva()) {
            throw new RegraNegocioException("A estação " + estacao.getCodigo() + " está " + estacao.getStatus() + " e não recebe leituras");
        }

        NivelRisco risco = estacao.classificar(request.nivelCm(), request.chuvaMmH());

        Leitura leitura = new Leitura();
        leitura.setNivelCm(request.nivelCm());
        leitura.setChuvaMmH(request.chuvaMmH());
        leitura.setTemperaturaC(request.temperaturaC());
        leitura.setUmidadePct(request.umidadePct());
        leitura.setNivelRisco(risco);
        leitura.setMedidaEm(request.medidaEm() != null ? request.medidaEm() : Instant.now());

        boolean ehAMaisRecente = estacao.registrarLeitura(leitura);
        leituraRepository.save(leitura);

        AreaRisco area = estacao.getArea();
        NivelRisco nivelAnterior = area.getNivelAtual();
        NivelRisco nivelArea = area.recalcularNivel();

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
