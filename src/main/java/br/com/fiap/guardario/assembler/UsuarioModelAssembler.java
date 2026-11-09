package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.dto.AlterarPapelRequest;
import org.springframework.http.HttpMethod;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.RelatoController;
import br.com.fiap.guardario.controller.UsuarioController;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

// usado nas rotas /api/usuarios (o ADMIN vê todos; o usuário, o próprio cadastro) e no /api/auth/me
@Component
public class UsuarioModelAssembler implements RepresentationModelAssembler<UsuarioResponse, EntityModel<UsuarioResponse>> {

    @Override
    public EntityModel<UsuarioResponse> toModel(UsuarioResponse usuario) {
        EntityModel<UsuarioResponse> model = EntityModel.of(usuario,
                linkTo(methodOn(UsuarioController.class).buscarPorId(usuario.id())).withSelfRel());

        // a lista e a troca de papel são do ADMIN; e o ADMIN não altera o próprio papel (409)
        if (SecurityUtils.temPapel(Papel.ADMIN)) {
            model.add(linkTo(methodOn(UsuarioController.class).listar(null)).withRel("usuarios").expand());
            boolean ehOProprioUsuario = SecurityUtils.usuarioOpcional()
                    .map(logado -> logado.getId().equals(usuario.id()))
                    .orElse(false);
            if (!ehOProprioUsuario) {
                Acoes.adicionar(model, linkTo(methodOn(UsuarioController.class).alterarPapel(usuario.id(), null)).withRel("alterar-papel").withTitle("PUT com {\"papel\": \"AGENTE\"}"), HttpMethod.PUT, AlterarPapelRequest.class);
            }
        }

        if (usuario.areaId() != null) {
            model.add(linkTo(methodOn(AreaController.class).buscarPorId(usuario.areaId())).withRel("area"));
        }
        // quem vê o próprio cadastro (no /me ou no /api/usuarios/{id}) também chega aos próprios relatos
        boolean ehOLogado = SecurityUtils.usuarioOpcional().map(logado -> logado.getId().equals(usuario.id())).orElse(false);
        if (ehOLogado) {
            model.add(linkTo(methodOn(RelatoController.class).meus(null)).withRel("meus-relatos").expand());
        }
        return model;
    }
}
