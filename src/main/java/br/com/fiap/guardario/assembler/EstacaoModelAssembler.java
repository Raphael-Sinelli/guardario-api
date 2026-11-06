package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.EstacaoResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusEstacao;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class EstacaoModelAssembler implements RepresentationModelAssembler<EstacaoResponse, EntityModel<EstacaoResponse>> {

    @Override
    public EntityModel<EstacaoResponse> toModel(EstacaoResponse estacao) {
        EntityModel<EstacaoResponse> model = EntityModel.of(estacao,
                linkTo(methodOn(EstacaoController.class).buscarPorId(estacao.id())).withSelfRel(),
                linkTo(methodOn(AreaController.class).buscarPorId(estacao.areaId())).withRel("area"),
                linkTo(methodOn(EstacaoController.class).listarLeituras(estacao.id(), null)).withRel("leituras"),
                linkTo(methodOn(EstacaoController.class).estatisticas(estacao.id(), 24)).withRel("estatisticas-24h"));

        // só estação ATIVA recebe leitura
        if (estacao.status() == StatusEstacao.ATIVA && SecurityUtils.isAgenteOuAdmin()) {
            model.add(linkTo(methodOn(EstacaoController.class).registrarLeitura(estacao.id(), null)).withRel("registrar-leitura"));
        }
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(EstacaoController.class).atualizar(estacao.id(), null)).withRel("atualizar"));
            model.add(linkTo(methodOn(EstacaoController.class).excluir(estacao.id())).withRel("excluir"));
        }
        return model;
    }
}
