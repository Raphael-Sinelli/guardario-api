package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AbrigoController;
import br.com.fiap.guardario.controller.AlertaController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.AlertaResponse;
import br.com.fiap.guardario.model.OrigemAlerta;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusAlerta;
import br.com.fiap.guardario.security.SecurityUtils;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class AlertaModelAssembler implements RepresentationModelAssembler<AlertaResponse, EntityModel<AlertaResponse>> {

    @Override
    public EntityModel<AlertaResponse> toModel(AlertaResponse alerta) {
        EntityModel<AlertaResponse> model = EntityModel.of(alerta,
                linkTo(methodOn(AlertaController.class).buscarPorId(alerta.id())).withSelfRel(),
                linkTo(methodOn(AreaController.class).buscarPorId(alerta.areaId())).withRel("area"),
                // quem recebe o alerta precisa saber para onde ir
                linkTo(methodOn(AbrigoController.class).listar(alerta.areaId(), null, null, true, null, null)).withRel("abrigos-com-vaga").expand());

        if (alerta.estacaoId() != null) {
            model.add(linkTo(methodOn(EstacaoController.class).buscarPorId(alerta.estacaoId())).withRel("estacao"));
        }

        // as ações dependem do estado: alerta encerrado não tem mais o que fazer
        if (alerta.status() == StatusAlerta.ATIVO && SecurityUtils.isAgenteOuAdmin()) {
            model.add(linkTo(methodOn(AlertaController.class).encerrar(alerta.id())).withRel("encerrar").withTitle("PATCH, sem corpo"));
            model.add(linkTo(methodOn(AlertaController.class).editar(alerta.id(), null)).withRel("editar").withTitle("PUT com {\"titulo\": \"...\", \"mensagem\": \"...\"}"));
        }
        // excluir só aparece quando dá certo: alerta manual já encerrado (o automático é histórico da estação)
        if (SecurityUtils.temPapel(Papel.ADMIN) && alerta.origem() == OrigemAlerta.MANUAL && alerta.status() == StatusAlerta.ENCERRADO) {
            model.add(linkTo(methodOn(AlertaController.class).excluir(alerta.id())).withRel("excluir").withTitle("DELETE"));
        }
        return model;
    }
}
