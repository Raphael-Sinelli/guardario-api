package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AbrigoController;
import br.com.fiap.guardario.controller.AlertaController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class AreaModelAssembler implements RepresentationModelAssembler<AreaResponse, EntityModel<AreaResponse>> {

    @Override
    public EntityModel<AreaResponse> toModel(AreaResponse area) {
        EntityModel<AreaResponse> model = EntityModel.of(area,
                linkTo(methodOn(AreaController.class).buscarPorId(area.id())).withSelfRel(),
                linkTo(methodOn(EstacaoController.class).listar(area.id(), null)).withRel("estacoes"),
                linkTo(methodOn(AbrigoController.class).listar(area.id(), null, null, true, null, null)).withRel("abrigos-com-vaga"),
                linkTo(methodOn(AlertaController.class).ativos()).withRel("alertas-ativos"),
                linkTo(methodOn(AreaController.class).listar(null, null)).withRel("areas"));

        // ações só aparecem para quem pode executá-las
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(AreaController.class).atualizar(area.id(), null)).withRel("atualizar"));
            model.add(linkTo(methodOn(AreaController.class).excluir(area.id())).withRel("excluir"));
        }
        return model;
    }
}
