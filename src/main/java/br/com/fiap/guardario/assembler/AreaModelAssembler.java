package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AbrigoController;
import br.com.fiap.guardario.controller.AlertaController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.RankingAreaResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.security.SecurityUtils;
import br.com.fiap.guardario.service.AreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
@RequiredArgsConstructor
public class AreaModelAssembler implements RepresentationModelAssembler<AreaResponse, EntityModel<AreaResponse>> {

    private final AreaService areaService;

    @Override
    public EntityModel<AreaResponse> toModel(AreaResponse area) {
        return toModel(area, excluiveis(List.of(area)));
    }

    // a página inteira com uma consulta só para saber quais áreas podem ser excluídas: sem N+1
    public PagedModel<EntityModel<AreaResponse>> toPagedModel(Page<AreaResponse> pagina,
                                                             PagedResourcesAssembler<AreaResponse> paginador) {
        Set<Long> excluiveis = excluiveis(pagina.getContent());
        return RespostaHal.pagina(pagina, paginador, area -> toModel(area, excluiveis), AreaResponse.class);
    }

    private EntityModel<AreaResponse> toModel(AreaResponse area, Set<Long> excluiveis) {
        EntityModel<AreaResponse> model = EntityModel.of(area,
                linkTo(methodOn(AreaController.class).buscarPorId(area.id())).withSelfRel(),
                linkTo(methodOn(EstacaoController.class).listar(area.id(), null)).withRel("estacoes").expand(),
                linkTo(methodOn(AbrigoController.class).listar(area.id(), null, null, true, null, null)).withRel("abrigos-com-vaga").expand(),
                linkTo(methodOn(AlertaController.class).ativos()).withRel("alertas-ativos"),
                linkTo(methodOn(AreaController.class).listar(null, null)).withRel("areas").expand());

        // ações só aparecem para quem pode executá-las, e excluir só quando a área não tem vínculos (senão daria 409)
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(AreaController.class).atualizar(area.id(), null)).withRel("atualizar"));
            if (excluiveis.contains(area.id())) {
                model.add(linkTo(methodOn(AreaController.class).excluir(area.id())).withRel("excluir"));
            }
        }
        return model;
    }

    // só o ADMIN vê o link excluir; para os outros papéis, nem consulta o banco
    private Set<Long> excluiveis(List<AreaResponse> areas) {
        if (areas.isEmpty() || !SecurityUtils.temPapel(Papel.ADMIN)) {
            return Set.of();
        }
        Set<Long> ids = areas.stream().map(AreaResponse::id).collect(Collectors.toCollection(HashSet::new));
        ids.removeAll(areaService.idsComVinculos(ids));
        return ids;
    }

    // ranking (JPQL): cada linha aponta para a área e para os abrigos com vaga dela
    public CollectionModel<EntityModel<RankingAreaResponse>> toRankingModel(List<RankingAreaResponse> ranking) {
        List<EntityModel<RankingAreaResponse>> linhas = ranking.stream()
                .map(linha -> EntityModel.of(linha,
                        linkTo(methodOn(AreaController.class).buscarPorId(linha.areaId())).withRel("area"),
                        linkTo(methodOn(AbrigoController.class).listar(linha.areaId(), null, null, true, null, null)).withRel("abrigos-com-vaga").expand()))
                .toList();
        return RespostaHal.colecao(CollectionModel.of(linhas, linkTo(methodOn(AreaController.class).ranking()).withSelfRel()),
                RankingAreaResponse.class);
    }
}
