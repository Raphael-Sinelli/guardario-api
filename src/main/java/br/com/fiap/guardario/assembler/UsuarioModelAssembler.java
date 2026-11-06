package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.UsuarioController;
import br.com.fiap.guardario.dto.UsuarioResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

// usado só nas rotas /api/usuarios, que já são exclusivas do ADMIN
@Component
public class UsuarioModelAssembler implements RepresentationModelAssembler<UsuarioResponse, EntityModel<UsuarioResponse>> {

    @Override
    public EntityModel<UsuarioResponse> toModel(UsuarioResponse usuario) {
        EntityModel<UsuarioResponse> model = EntityModel.of(usuario,
                linkTo(methodOn(UsuarioController.class).buscarPorId(usuario.id())).withSelfRel(),
                linkTo(methodOn(UsuarioController.class).alterarPapel(usuario.id(), null)).withRel("alterar-papel"),
                linkTo(methodOn(UsuarioController.class).listar(null)).withRel("usuarios"));

        if (usuario.areaId() != null) {
            model.add(linkTo(methodOn(AreaController.class).buscarPorId(usuario.areaId())).withRel("area"));
        }
        return model;
    }
}
