package br.com.fiap.guardario.assembler;

import br.com.fiap.guardario.controller.EstacaoController;
import br.com.fiap.guardario.dto.LeituraResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class LeituraModelAssembler implements RepresentationModelAssembler<LeituraResponse, EntityModel<LeituraResponse>> {

    @Override
    public EntityModel<LeituraResponse> toModel(LeituraResponse leitura) {
        return EntityModel.of(leitura,
                linkTo(methodOn(EstacaoController.class).buscarPorId(leitura.estacaoId())).withRel("estacao"),
                linkTo(methodOn(EstacaoController.class).estatisticas(leitura.estacaoId(), 24)).withRel("estatisticas-24h"));
    }
}
