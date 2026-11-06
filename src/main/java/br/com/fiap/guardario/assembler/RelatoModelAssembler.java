package br.com.fiap.guardario.assembler;

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
                model.add(alterarStatus.withRel("atender"));
                model.add(alterarStatus.withRel("descartar"));
            } else if (relato.status() == StatusRelato.EM_ATENDIMENTO) {
                model.add(alterarStatus.withRel("resolver"));
            }
        }

        boolean ehAutor = SecurityUtils.usuarioOpcional()
                .map(Usuario::getId)
                .map(id -> id.equals(relato.autorId()))
                .orElse(false);
        if (ehAutor) {
            model.add(linkTo(methodOn(RelatoController.class).meus(null)).withRel("meus-relatos"));
            if (relato.status() == StatusRelato.ABERTO) {
                model.add(linkTo(methodOn(RelatoController.class).atualizar(relato.id(), null)).withRel("editar"));
            }
        }
        if ((ehAutor && relato.status() == StatusRelato.ABERTO) || SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(RelatoController.class).excluir(relato.id())).withRel("excluir"));
        }
        return model;
    }
}
