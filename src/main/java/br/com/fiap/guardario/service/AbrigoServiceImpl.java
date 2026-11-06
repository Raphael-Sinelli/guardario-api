package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AbrigoRequest;
import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.AbrigoMapper;
import br.com.fiap.guardario.model.Abrigo;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.StatusAbrigo;
import br.com.fiap.guardario.repository.AbrigoRepository;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.specification.AbrigoSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * Toda escrita que pode mudar a lista de abrigos com vaga limpa o cache "abrigosDisponiveis".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbrigoServiceImpl implements AbrigoService {

    private final AbrigoRepository abrigoRepository;
    private final AreaRiscoRepository areaRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AbrigoResponse> listar(Long areaId, Boolean aceitaPets, Boolean acessivelPcd, Boolean comVagas,
                                       StatusAbrigo status, Pageable pageable) {
        var filtro = AbrigoSpecification.comFiltros(areaId, aceitaPets, acessivelPcd, comVagas, status);
        return abrigoRepository.findAll(filtro, pageable).map(AbrigoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "abrigosDisponiveis")
    public List<AbrigoResponse> disponiveis() {
        log.info("Cache 'abrigosDisponiveis' vazio: consultando o banco");
        return abrigoRepository.findByStatusOrderByNomeAsc(StatusAbrigo.ABERTO).stream()
                .map(AbrigoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AbrigoResponse buscarPorId(Long id) {
        return AbrigoMapper.toResponse(buscarEntidade(id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public AbrigoResponse criar(AbrigoRequest request) {
        Abrigo abrigo = new Abrigo();
        preencher(abrigo, request);
        abrigo.atualizarStatus(); // pode já nascer LOTADO se a ocupação informada for igual à capacidade
        return AbrigoMapper.toResponse(abrigoRepository.save(abrigo));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public AbrigoResponse atualizar(Long id, AbrigoRequest request) {
        Abrigo abrigo = buscarEntidade(id);
        preencher(abrigo, request);
        abrigo.atualizarStatus();
        return AbrigoMapper.toResponse(abrigo);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public AbrigoResponse alterarStatus(Long id, StatusAbrigo status) {
        Abrigo abrigo = buscarEntidade(id);
        switch (status) {
            case FECHADO -> abrigo.fechar();
            case ABERTO -> abrigo.reabrir();
            case LOTADO -> throw new IllegalArgumentException("LOTADO é definido pelo sistema quando a ocupação chega à capacidade");
        }
        return AbrigoMapper.toResponse(abrigo);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public AbrigoResponse registrarEntrada(Long id, int pessoas) {
        Abrigo abrigo = buscarEntidade(id);
        abrigo.registrarEntrada(pessoas);
        return AbrigoMapper.toResponse(abrigo);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public AbrigoResponse registrarSaida(Long id, int pessoas) {
        Abrigo abrigo = buscarEntidade(id);
        abrigo.registrarSaida(pessoas);
        return AbrigoMapper.toResponse(abrigo);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "abrigosDisponiveis", allEntries = true)
    public void excluir(Long id) {
        abrigoRepository.delete(buscarEntidade(id));
    }

    private Abrigo buscarEntidade(Long id) {
        return abrigoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("abrigo", id));
    }

    private void preencher(Abrigo abrigo, AbrigoRequest request) {
        AreaRisco area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));
        abrigo.setNome(request.nome().trim());
        abrigo.setEndereco(request.endereco().trim());
        abrigo.setCapacidade(request.capacidade());
        abrigo.setOcupacao(request.ocupacao() != null ? request.ocupacao() : 0);
        abrigo.setAceitaPets(request.aceitaPets());
        abrigo.setAcessivelPcd(request.acessivelPcd());
        abrigo.setTelefone(request.telefone());
        abrigo.setLatitude(request.latitude());
        abrigo.setLongitude(request.longitude());
        abrigo.setArea(area);
    }
}
