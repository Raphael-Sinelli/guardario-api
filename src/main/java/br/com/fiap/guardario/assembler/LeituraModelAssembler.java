package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.AlertaController;
import br.com.fiap.guardario.controller.AreaController;
import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.LeituraRegistradaResponse;
import br.com.fiap.guardario.dto.LeituraResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class LeituraModelAssembler implements RepresentationModelAssembler<LeituraResponse, EntityModel<LeituraResponse>> {

    @Override
    public EntityModel<LeituraResponse> toModel(LeituraResponse leitura) {
        return EntityModel.of(leitura,
                self(leitura),
                linkTo(methodOn(EstacaoController.class).buscarPorId(leitura.estacaoId())).withRel("estacao"),
                linkTo(methodOn(EstacaoController.class).estatisticas(leitura.estacaoId(), 24)).withRel("estatisticas-24h"));
    }

    // resposta do POST de leitura: aponta para a leitura criada, a estação, a área e, se nasceu, o alerta automático
    public EntityModel<LeituraRegistradaResponse> toModel(LeituraRegistradaResponse registrada) {
        LeituraResponse leitura = registrada.leitura();
        EntityModel<LeituraRegistradaResponse> model = EntityModel.of(registrada,
                self(leitura),
                linkTo(methodOn(EstacaoController.class).buscarPorId(leitura.estacaoId())).withRel("estacao"),
                linkTo(methodOn(AreaController.class).buscarPorId(registrada.areaId())).withRel("area"));
        if (registrada.alertaGeradoId() != null) {
            model.add(linkTo(methodOn(AlertaController.class).buscarPorId(registrada.alertaGeradoId())).withRel("alerta"));
        }
        return model;
    }

    private Link self(LeituraResponse leitura) {
        return linkTo(methodOn(EstacaoController.class).buscarLeitura(leitura.estacaoId(), leitura.id())).withSelfRel();
    }
}
