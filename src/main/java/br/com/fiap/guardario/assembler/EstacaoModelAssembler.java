package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.EstacaoResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusEstacao;
import br.com.fiap.guardario.security.SecurityUtils;
import br.com.fiap.guardario.service.EstacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
@RequiredArgsConstructor
public class EstacaoModelAssembler implements RepresentationModelAssembler<EstacaoResponse, EntityModel<EstacaoResponse>> {

    private final EstacaoService estacaoService;

    @Override
    public EntityModel<EstacaoResponse> toModel(EstacaoResponse estacao) {
        return toModel(estacao, excluiveis(List.of(estacao)));
    }

    // a página inteira com uma consulta só para saber quais estações podem ser excluídas: sem N+1
    public PagedModel<EntityModel<EstacaoResponse>> toPagedModel(Page<EstacaoResponse> pagina,
                                                                PagedResourcesAssembler<EstacaoResponse> paginador) {
        Set<Long> excluiveis = excluiveis(pagina.getContent());
        return paginador.toModel(pagina, estacao -> toModel(estacao, excluiveis));
    }

    // a lista de críticas também faz uma consulta só, em vez de uma por estação
    @Override
    public CollectionModel<EntityModel<EstacaoResponse>> toCollectionModel(Iterable<? extends EstacaoResponse> estacoes) {
        List<EstacaoResponse> lista = new ArrayList<>();
        estacoes.forEach(lista::add);
        Set<Long> excluiveis = excluiveis(lista);
        return CollectionModel.of(lista.stream().map(estacao -> toModel(estacao, excluiveis)).toList());
    }

    private EntityModel<EstacaoResponse> toModel(EstacaoResponse estacao, Set<Long> excluiveis) {
        EntityModel<EstacaoResponse> model = EntityModel.of(estacao,
                linkTo(methodOn(EstacaoController.class).buscarPorId(estacao.id())).withSelfRel(),
                linkTo(methodOn(AreaController.class).buscarPorId(estacao.areaId())).withRel("area"),
                linkTo(methodOn(EstacaoController.class).listarLeituras(estacao.id(), null)).withRel("leituras"),
                linkTo(methodOn(EstacaoController.class).estatisticas(estacao.id(), 24)).withRel("estatisticas-24h"));

        // só estação ATIVA recebe leitura
        if (estacao.status() == StatusEstacao.ATIVA && SecurityUtils.isAgenteOuAdmin()) {
            model.add(linkTo(methodOn(EstacaoController.class).registrarLeitura(estacao.id(), null)).withRel("registrar-leitura"));
        }
        // excluir só aparece quando a estação não tem alerta no histórico (senão daria 409)
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(EstacaoController.class).atualizar(estacao.id(), null)).withRel("atualizar"));
            if (excluiveis.contains(estacao.id())) {
                model.add(linkTo(methodOn(EstacaoController.class).excluir(estacao.id())).withRel("excluir"));
            }
        }
        return model;
    }

    // só o ADMIN vê o link excluir; para os outros papéis, nem consulta o banco
    private Set<Long> excluiveis(List<EstacaoResponse> estacoes) {
        if (estacoes.isEmpty() || !SecurityUtils.temPapel(Papel.ADMIN)) {
            return Set.of();
        }
        Set<Long> ids = estacoes.stream().map(EstacaoResponse::id).collect(Collectors.toCollection(HashSet::new));
        ids.removeAll(estacaoService.idsComAlertas(ids));
        return ids;
    }
}
