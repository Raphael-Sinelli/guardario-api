package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AbrigoController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusAbrigo;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class AbrigoModelAssembler implements RepresentationModelAssembler<AbrigoResponse, EntityModel<AbrigoResponse>> {

    @Override
    public EntityModel<AbrigoResponse> toModel(AbrigoResponse abrigo) {
        EntityModel<AbrigoResponse> model = EntityModel.of(abrigo,
                linkTo(methodOn(AbrigoController.class).buscarPorId(abrigo.id())).withSelfRel(),
                linkTo(methodOn(AreaController.class).buscarPorId(abrigo.areaId())).withRel("area"),
                linkTo(methodOn(AbrigoController.class).disponiveis()).withRel("abrigos-disponiveis"));

        if (SecurityUtils.isAgenteOuAdmin()) {
            // cada ação só aparece quando faz sentido no estado atual do abrigo
            if (abrigo.status() == StatusAbrigo.FECHADO) {
                model.add(linkTo(methodOn(AbrigoController.class).alterarStatus(abrigo.id(), null)).withRel("reabrir").withTitle("PATCH com {\"status\": \"ABERTO\"}"));
            } else {
                if (abrigo.vagas() > 0) {
                    model.add(linkTo(methodOn(AbrigoController.class).registrarEntrada(abrigo.id(), null)).withRel("registrar-entrada").withTitle("POST com {\"pessoas\": 1}"));
                }
                model.add(linkTo(methodOn(AbrigoController.class).alterarStatus(abrigo.id(), null)).withRel("fechar").withTitle("PATCH com {\"status\": \"FECHADO\"}"));
            }
            if (abrigo.ocupacao() > 0) {
                model.add(linkTo(methodOn(AbrigoController.class).registrarSaida(abrigo.id(), null)).withRel("registrar-saida").withTitle("POST com {\"pessoas\": 1}"));
            }
            model.add(linkTo(methodOn(AbrigoController.class).atualizar(abrigo.id(), null)).withRel("atualizar").withTitle("PUT com o abrigo inteiro"));
        }
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(AbrigoController.class).excluir(abrigo.id())).withRel("excluir").withTitle("DELETE"));
        }
        return model;
    }
}
