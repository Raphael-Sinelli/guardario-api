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
import br.com.fiap.guardario.repository.LeituraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class EstacaoServiceImpl implements EstacaoService {

    private final EstacaoRepository estacaoRepository;
    private final AreaRiscoRepository areaRepository;
    private final AlertaRepository alertaRepository;
    private final LeituraRepository leituraRepository;
    private final AlertaService alertaService;
    private final RelatoService relatoService;

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
    public Set<Long> idsComAlertas(Collection<Long> ids) {
        return ids.isEmpty() ? Set.of() : alertaRepository.buscarEstacaoIdsComAlertas(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstacaoResponse> criticas() {
        return estacaoRepository.buscarCriticas(StatusEstacao.ATIVA, List.of(NivelRisco.ALERTA, NivelRisco.EMERGENCIA)).stream()
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
        AreaRisco area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));
        Estacao estacao = new Estacao();
        estacao.setCodigo(codigo);
        preencher(estacao, request, area); // estação nova ainda não tem leitura: não muda o nível da área
        return EstacaoMapper.toResponse(estacaoRepository.save(estacao));
    }

    // mudar a área ou o status da estação muda o nível das áreas envolvidas (RN03): as duas são recalculadas.
    // O cache "areas" é limpo inteiro porque a chave da área antiga não está nos parâmetros do método.
    // A estação e as áreas são travadas antes de qualquer leitura do nível, na ordem do registro de leitura (estação,
    // depois área): um relato enviado ao mesmo tempo espera e lê o nível novo, ou é gravado antes e entra no recálculo
    // das prioridades. Sem a trava, ele lia o nível antigo depois que o recálculo já tinha passado
    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", allEntries = true)
    public EstacaoResponse atualizar(Long id, EstacaoRequest request) {
        Estacao estacao = buscarComTrava(id);
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (!codigo.equalsIgnoreCase(estacao.getCodigo()) && estacaoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new RegraNegocioException("Já existe uma estação com o código " + codigo);
        }
        Map<Long, AreaRisco> areas = travarAreas(estacao.getArea().getId(), request.areaId());
        AreaRisco areaAnterior = areas.get(estacao.getArea().getId());
        boolean estavaAtiva = estacao.isAtiva();
        estacao.setCodigo(codigo);
        preencher(estacao, request, areas.get(request.areaId())); // último nível e último risco vêm das leituras

        // reativada depois de mais de 1 hora parada: o último risco é velho e não volta para a área
        if (!estavaAtiva && estacao.isAtiva() && !estacao.leituraAtualEhRecente()) {
            estacao.esquecerUltimaLeitura();
        }

        // com as cotas novas, o último risco pode mudar na hora, sem esperar a próxima leitura
        NivelRisco riscoAnterior = estacao.getUltimoRisco();
        if (estacao.getUltimaLeituraEm() != null) {
            leituraRepository.findFirstByEstacaoIdAndMedidaEm(estacao.getId(), estacao.getUltimaLeituraEm())
                    .ifPresent(ultima -> estacao.reclassificarUltimaLeitura(ultima.getChuvaMmH()));
        }

        AreaRisco areaNova = estacao.getArea();
        if (areaNova != areaAnterior) {
            areaAnterior.getEstacoes().remove(estacao);
            areaNova.getEstacoes().add(estacao);
            recalcularNivel(areaAnterior);
        }
        recalcularNivel(areaNova);

        // RN02 também quando a régua muda: se a leitura atual, ainda recente, SUBIU para ALERTA ou pior por causa das
        // cotas novas, nasce o alerta automático. Só quando sobe: renomear a estação não reabre um alerta que a Defesa
        // Civil já encerrou. A área já está travada desde o começo, como no registro de leitura
        NivelRisco riscoAtual = estacao.getUltimoRisco();
        boolean subiu = riscoAtual != null && (riscoAnterior == null || riscoAtual.getPeso() > riscoAnterior.getPeso());
        if (estacao.isAtiva() && estacao.leituraAtualEhRecente() && subiu && riscoAtual.maiorOuIgualA(NivelRisco.ALERTA)) {
            alertaService.gerarAutomatico(estacao, estacao.getUltimoRisco(), estacao.getUltimoNivelCm());
        }
        return EstacaoMapper.toResponse(estacao);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", allEntries = true)
    public void excluir(Long id) {
        Estacao estacao = buscarComTrava(id);
        // alerta é histórico de decisão da Defesa Civil: com alerta no histórico, a estação é desativada, não apagada
        if (alertaRepository.existsByEstacaoId(id)) {
            throw new RegraNegocioException("A estação tem alertas no histórico. Mude o status para INATIVA em vez de excluir");
        }
        Long areaId = estacao.getArea().getId();
        AreaRisco area = travarAreas(areaId).get(areaId); // a mesma trava do atualizar
        area.getEstacoes().remove(estacao);
        estacaoRepository.delete(estacao); // as leituras vão junto pelo cascade
        recalcularNivel(area); // RN03: sem esta estação, o nível da área pode cair
    }

    // RN03 e, se o nível mudou, RN06: os relatos em aberto da área acompanham o nível novo
    private void recalcularNivel(AreaRisco area) {
        NivelRisco antes = area.getNivelAtual();
        if (area.recalcularNivel() != antes) {
            relatoService.reavaliarPrioridades(area);
        }
    }

    private Estacao buscarEntidade(Long id) {
        return estacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("estação", id));
    }

    private Estacao buscarComTrava(Long id) {
        return estacaoRepository.buscarComTrava(id)
                .orElseThrow(() -> new ResourceNotFoundException("estação", id));
    }

    // trava as áreas em ordem de id, para duas mudanças cruzadas (estação indo de A para B e outra de B para A) não
    // ficarem esperando uma pela outra. A área ainda não foi lida nesta transação (a da estação é LAZY), então o nível
    // vem do banco já com a trava, e não de uma leitura anterior
    private Map<Long, AreaRisco> travarAreas(Long... ids) {
        Map<Long, AreaRisco> areas = new HashMap<>();
        for (Long areaId : new TreeSet<>(List.of(ids))) {
            areas.put(areaId, areaRepository.buscarComTrava(areaId)
                    .orElseThrow(() -> new ResourceNotFoundException("área", areaId)));
        }
        return areas;
    }

    private void preencher(Estacao estacao, EstacaoRequest request, AreaRisco area) {
        estacao.setNome(request.nome().trim());
        estacao.setRio(request.rio().trim());
        estacao.setCotaAtencaoCm(request.cotaAtencaoCm());
        estacao.setCotaAlertaCm(request.cotaAlertaCm());
        estacao.setCotaTransbordoCm(request.cotaTransbordoCm());
        // sem status no corpo, fica o atual (a estação nova já nasce ATIVA): um PUT não pode reativar a estação sem querer
        if (request.status() != null) {
            estacao.setStatus(request.status());
        }
        estacao.setArea(area);
    }
}
