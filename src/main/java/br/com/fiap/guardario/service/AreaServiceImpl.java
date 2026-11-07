package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AreaRequest;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.RankingAreaResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.AreaMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaServiceImpl implements AreaService {

    private static final List<StatusRelato> RELATOS_EM_ABERTO = List.of(StatusRelato.ABERTO, StatusRelato.EM_ATENDIMENTO);

    private final AreaRiscoRepository areaRepository;
    private final EstacaoRepository estacaoRepository;
    private final AbrigoRepository abrigoRepository;
    private final AlertaRepository alertaRepository;
    private final RelatoRepository relatoRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AreaResponse> listar(String uf, Pageable pageable) {
        Page<AreaRisco> pagina = (uf == null || uf.isBlank())
                ? areaRepository.findAll(pageable)
                : areaRepository.findByUfIgnoreCase(uf.trim(), pageable);
        return pagina.map(AreaMapper::toResponse);
    }

    // o app abre a tela da área o tempo todo para ver o nível; ele só muda quando chega leitura que altera o risco
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "areas", key = "#id")
    public AreaResponse buscarPorId(Long id) {
        log.info("Cache 'areas' sem a área {}: consultando o banco", id);
        return AreaMapper.toResponse(buscarEntidade(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RankingAreaResponse> ranking() {
        return areaRepository.rankingPorRelatosEmAberto(RELATOS_EM_ABERTO);
    }

    @Override
    @Transactional
    public AreaResponse criar(AreaRequest request) {
        AreaRisco area = new AreaRisco();
        preencher(area, request);
        return AreaMapper.toResponse(areaRepository.save(area));
    }

    // os alertas ativos em cache guardam o nome da área: renomear a área também invalida esse cache
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "areas", key = "#id"),
            @CacheEvict(cacheNames = "alertasAtivos", allEntries = true)
    })
    public AreaResponse atualizar(Long id, AreaRequest request) {
        AreaRisco area = buscarEntidade(id);
        preencher(area, request); // nivelAtual não muda aqui: só as leituras mudam o risco
        return AreaMapper.toResponse(area);
    }

    // sem cascade na área: apagar uma área com histórico apagaria estações, alertas e relatos junto
    @Override
    @Transactional
    @CacheEvict(cacheNames = "areas", key = "#id")
    public void excluir(Long id) {
        AreaRisco area = buscarEntidade(id);
        boolean temVinculos = estacaoRepository.existsByAreaId(id)
                || abrigoRepository.existsByAreaId(id)
                || alertaRepository.existsByAreaId(id)
                || relatoRepository.existsByAreaId(id);
        if (temVinculos) {
            throw new RegraNegocioException("A área tem estações, abrigos, alertas ou relatos vinculados e não pode ser excluída");
        }
        areaRepository.delete(area);
    }

    private AreaRisco buscarEntidade(Long id) {
        return areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("área", id));
    }

    private void preencher(AreaRisco area, AreaRequest request) {
        area.setNome(request.nome().trim());
        area.setCidade(request.cidade().trim());
        area.setUf(request.uf().toUpperCase(Locale.ROOT));
        area.setPopulacaoEstimada(request.populacaoEstimada());
        area.setLatitude(request.latitude());
        area.setLongitude(request.longitude());
    }
}
