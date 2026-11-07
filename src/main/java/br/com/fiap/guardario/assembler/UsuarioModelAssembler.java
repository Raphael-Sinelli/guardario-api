package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.UsuarioController;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.security.SecurityUtils;
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
                linkTo(methodOn(UsuarioController.class).listar(null)).withRel("usuarios").expand());

        // o ADMIN não altera o próprio papel (409): no registro dele a ação não aparece
        boolean ehOProprioUsuario = SecurityUtils.usuarioOpcional()
                .map(logado -> logado.getId().equals(usuario.id()))
                .orElse(false);
        if (!ehOProprioUsuario) {
            model.add(linkTo(methodOn(UsuarioController.class).alterarPapel(usuario.id(), null)).withRel("alterar-papel"));
        }

        if (usuario.areaId() != null) {
            model.add(linkTo(methodOn(AreaController.class).buscarPorId(usuario.areaId())).withRel("area"));
        }
        return model;
    }
}
