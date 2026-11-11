package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.dto.RelatoRequest;
import br.com.fiap.guardario.dto.AlterarStatusRelatoRequest;
import org.springframework.http.HttpMethod;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.RelatoController;
import br.com.fiap.guardario.dto.RelatoResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/*
 * Os links de ação seguem a máquina de estados do relato (RN07):
 * ABERTO -> atender ou descartar; EM_ATENDIMENTO -> resolver; RESOLVIDO e DESCARTADO não têm ação.
 */
@Component
public class RelatoModelAssembler implements RepresentationModelAssembler<RelatoResponse, EntityModel<RelatoResponse>> {

    @Override
    public EntityModel<RelatoResponse> toModel(RelatoResponse relato) {
        EntityModel<RelatoResponse> model = EntityModel.of(relato,
                linkTo(methodOn(RelatoController.class).buscarPorId(relato.id())).withSelfRel(),
                linkTo(methodOn(AreaController.class).buscarPorId(relato.areaId())).withRel("area"));

        if (SecurityUtils.isAgenteOuAdmin()) {
            var alterarStatus = linkTo(methodOn(RelatoController.class).alterarStatus(relato.id(), null));
            if (relato.status() == StatusRelato.ABERTO) {
                Acoes.adicionar(model, alterarStatus.withRel("atender").withTitle("PATCH com {\"status\": \"EM_ATENDIMENTO\"}"), HttpMethod.PATCH, AlterarStatusRelatoRequest.class, Map.of("status", StatusRelato.EM_ATENDIMENTO));
                Acoes.adicionar(model, alterarStatus.withRel("descartar").withTitle("PATCH com {\"status\": \"DESCARTADO\"}"), HttpMethod.PATCH, AlterarStatusRelatoRequest.class, Map.of("status", StatusRelato.DESCARTADO));
            } else if (relato.status() == StatusRelato.EM_ATENDIMENTO) {
                Acoes.adicionar(model, alterarStatus.withRel("resolver").withTitle("PATCH com {\"status\": \"RESOLVIDO\"}"), HttpMethod.PATCH, AlterarStatusRelatoRequest.class, Map.of("status", StatusRelato.RESOLVIDO));
            }
        }

        boolean ehAutor = SecurityUtils.usuarioOpcional()
                .map(Usuario::getId)
                .map(id -> id.equals(relato.autorId()))
                .orElse(false);
        if (ehAutor) {
            model.add(linkTo(methodOn(RelatoController.class).meus(null)).withRel("meus-relatos").expand());
            if (relato.status() == StatusRelato.ABERTO) {
                Acoes.adicionar(model, linkTo(methodOn(RelatoController.class).atualizar(relato.id(), null)).withRel("editar").withTitle("PUT com o relato inteiro"), HttpMethod.PUT, RelatoRequest.class);
            }
        }
        if ((ehAutor && relato.status() == StatusRelato.ABERTO) || SecurityUtils.temPapel(Papel.ADMIN)) {
            Acoes.adicionar(model, linkTo(methodOn(RelatoController.class).excluir(relato.id())).withRel("excluir").withTitle("DELETE"), HttpMethod.DELETE, null);
        }
        return model;
    }
}
