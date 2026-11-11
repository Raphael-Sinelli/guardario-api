package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.dto.MovimentacaoAbrigoRequest;
import br.com.fiap.guardario.dto.AlterarStatusAbrigoRequest;
import br.com.fiap.guardario.dto.AbrigoRequest;
import org.springframework.http.HttpMethod;
import br.com.fiap.guardario.controller.AbrigoController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusAbrigo;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

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
                Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).alterarStatus(abrigo.id(), null)).withRel("reabrir").withTitle("PATCH com {\"status\": \"ABERTO\"}"), HttpMethod.PATCH, AlterarStatusAbrigoRequest.class, Map.of("status", StatusAbrigo.ABERTO));
            } else {
                if (abrigo.vagas() > 0) {
                    Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).registrarEntrada(abrigo.id(), null)).withRel("registrar-entrada").withTitle("POST com {\"pessoas\": 1}"), HttpMethod.POST, MovimentacaoAbrigoRequest.class);
                }
                Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).alterarStatus(abrigo.id(), null)).withRel("fechar").withTitle("PATCH com {\"status\": \"FECHADO\"}"), HttpMethod.PATCH, AlterarStatusAbrigoRequest.class, Map.of("status", StatusAbrigo.FECHADO));
            }
            if (abrigo.ocupacao() > 0) {
                Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).registrarSaida(abrigo.id(), null)).withRel("registrar-saida").withTitle("POST com {\"pessoas\": 1}"), HttpMethod.POST, MovimentacaoAbrigoRequest.class);
            }
            Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).atualizar(abrigo.id(), null)).withRel("atualizar").withTitle("PUT com o abrigo inteiro"), HttpMethod.PUT, AbrigoRequest.class);
        }
        // excluir só aparece quando dá certo: abrigo vazio
        if (SecurityUtils.temPapel(Papel.ADMIN) && abrigo.ocupacao() == 0) {
            Acoes.adicionar(model, linkTo(methodOn(AbrigoController.class).excluir(abrigo.id())).withRel("excluir").withTitle("DELETE"), HttpMethod.DELETE, null);
        }
        return model;
    }
}
