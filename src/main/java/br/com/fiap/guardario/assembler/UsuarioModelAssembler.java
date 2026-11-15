package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.dto.AlterarPapelRequest;
import br.com.fiap.guardario.dto.AlterarSenhaRequest;
import br.com.fiap.guardario.dto.AtualizarPerfilRequest;
import org.springframework.http.HttpMethod;
import br.com.fiap.guardario.controller.AlertaController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.AuthController;
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
            model.add(linkTo(methodOn(AlertaController.class).ativosDaMinhaArea()).withRel("alertas-da-minha-area"));
            // as ações do próprio cadastro, com o template HAL-FORMS de cada uma (só para o dono: o ADMIN que vê o
            // cadastro de outra pessoa não edita o perfil nem troca a senha dela)
            Acoes.adicionar(model, linkTo(methodOn(AuthController.class).atualizarPerfil(null)).withRel("editar-perfil")
                    .withTitle("PUT com {\"nome\", \"telefone\", \"areaId\"}"), HttpMethod.PUT, AtualizarPerfilRequest.class);
            // as áreas seguidas (N:M): a lista e as duas ações, com o {areaId} da área no lugar do id
            model.add(linkTo(methodOn(AuthController.class).areasSeguidas()).withRel("areas-seguidas")
                    .withTitle("GET: as áreas que você segue, além da área do cadastro"));
            Acoes.adicionar(model, linkTo(methodOn(AuthController.class).seguirArea(null)).withRel("seguir-area")
                    .withTitle("PUT sem corpo, com o id da área no lugar de {areaId} (até 20 áreas)"), HttpMethod.PUT, null);
            Acoes.adicionar(model, linkTo(methodOn(AuthController.class).deixarDeSeguirArea(null)).withRel("deixar-de-seguir-area")
                    .withTitle("DELETE, com o id da área no lugar de {areaId}"), HttpMethod.DELETE, null);
            Acoes.adicionar(model, linkTo(methodOn(AuthController.class).alterarSenha(null)).withRel("trocar-senha")
                    .withTitle("PUT com {\"senhaAtual\", \"novaSenha\"}; depois, faça login de novo"), HttpMethod.PUT, AlterarSenhaRequest.class);
        }
        return model;
    }
}
