package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.configurations.CamposDeOrdenacao;
import br.com.fiap.guardario.assembler.AlertaModelAssembler;
import br.com.fiap.guardario.assembler.RespostaHal;
import br.com.fiap.guardario.dto.AlertaEdicaoRequest;
import br.com.fiap.guardario.dto.AlertaRequest;
import br.com.fiap.guardario.dto.AlertaResponse;
import br.com.fiap.guardario.model.StatusAlerta;
import br.com.fiap.guardario.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/alertas")
@Validated
@RequiredArgsConstructor
@Tag(name = "Alertas", description = "Alertas de enchente: automáticos (gerados pelas leituras) e manuais (Defesa Civil)")
public class AlertaController {

    private final AlertaService alertaService;
    private final AlertaModelAssembler assembler;
    private final PagedResourcesAssembler<AlertaResponse> pagedAssembler;

    // sem o nível de risco: ele é gravado como texto (EnumType.STRING), e a ordem alfabética não é a de gravidade
    @CamposDeOrdenacao(value = {"emitidoEm", "encerradoEm", "status", "origem", "titulo", "id"}, texto = {"titulo"})
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Lista os alertas", description = "Público, paginado e ordenável. Filtro opcional por status.")
    @ApiResponse(responseCode = "200", description = "Página de alertas")
    @ApiResponse(responseCode = "400", description = "Status fora do enum (ATIVO, ENCERRADO)")
    public ResponseEntity<PagedModel<EntityModel<AlertaResponse>>> listar(
            @Parameter(description = "ATIVO ou ENCERRADO") @RequestParam(required = false) StatusAlerta status,
            @ParameterObject @PageableDefault(size = 10, sort = "emitidoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(RespostaHal.pagina(alertaService.listar(status, pageable), pagedAssembler, assembler, AlertaResponse.class));
    }

    @GetMapping("/ativos")
    @SecurityRequirements
    @Operation(summary = "Alertas ativos agora, de todas as áreas ou de uma",
            description = "Pública e em cache (é a consulta que o app faz toda vez que abre). Mais grave primeiro. "
                    + "Sem areaId, traz os alertas ativos de todas as áreas. Com areaId, só os daquela área: é para onde "
                    + "leva o link alertas-ativos de cada área.")
    @ApiResponse(responseCode = "200", description = "Alertas ativos (lista vazia se a área não tem nenhum)")
    @ApiResponse(responseCode = "400", description = "areaId menor ou igual a zero, ou que não é número")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    public ResponseEntity<CollectionModel<EntityModel<AlertaResponse>>> ativos(
            @Parameter(description = "Id da área (opcional): só os alertas ativos dela") @RequestParam(required = false) @Positive Long areaId) {
        List<AlertaResponse> alertas = areaId == null ? alertaService.ativos() : alertaService.ativosDaArea(areaId);
        // sem areaId, o expand tira o {?areaId} do link: nenhum link da API sai com template
        CollectionModel<EntityModel<AlertaResponse>> model = assembler.toCollectionModel(alertas)
                .add(linkTo(methodOn(AlertaController.class).ativos(areaId)).withSelfRel().expand());
        return ResponseEntity.ok(RespostaHal.colecao(model, AlertaResponse.class));
    }

    @GetMapping("/minha-area")
    @Operation(summary = "Alertas ativos da área onde o usuário mora",
            description = "Qualquer usuário logado. Devolve só os alertas ativos da área do cadastro de quem está logado "
                    + "(o areaId informado no cadastro), mais grave primeiro, no mesmo formato de /api/alertas/ativos. "
                    + "Quem não tem área recebe a lista vazia. Sem cache: a resposta muda de usuário para usuário.")
    @ApiResponse(responseCode = "200", description = "Alertas ativos da área do usuário (lista vazia se ele não tem área)")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    public ResponseEntity<CollectionModel<EntityModel<AlertaResponse>>> ativosDaMinhaArea() {
        CollectionModel<EntityModel<AlertaResponse>> model = assembler.toCollectionModel(alertaService.ativosDaMinhaArea())
                .add(linkTo(methodOn(AlertaController.class).ativosDaMinhaArea()).withSelfRel());
        return ResponseEntity.ok(RespostaHal.colecao(model, AlertaResponse.class));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Busca um alerta pelo id", description = "Os links mudam com o estado: alerta ATIVO tem a ação encerrar.")
    @ApiResponse(responseCode = "200", description = "Alerta encontrado")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "404", description = "Alerta não existe")
    public ResponseEntity<EntityModel<AlertaResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(alertaService.buscarPorId(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Emite um alerta manual",
            description = "AGENTE ou ADMIN. Alertas ativos de nível menor na mesma área são substituídos.")
    @ApiResponse(responseCode = "201", description = "Alerta emitido")
    @ApiResponse(responseCode = "400", description = "Campos inválidos, nível NORMAL ou estação de outra área")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Área ou estação não existe")
    @ApiResponse(responseCode = "409", description = "A área já tem alerta ativo de nível igual ou maior")
    public ResponseEntity<EntityModel<AlertaResponse>> criar(@RequestBody @Valid AlertaRequest request) {
        EntityModel<AlertaResponse> model = assembler.toModel(alertaService.criarManual(request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Edita o título e a mensagem de um alerta ativo", description = "AGENTE ou ADMIN.")
    @ApiResponse(responseCode = "200", description = "Alerta atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Alerta não existe")
    @ApiResponse(responseCode = "409", description = "Alerta já encerrado")
    public ResponseEntity<EntityModel<AlertaResponse>> editar(@PathVariable @Positive Long id,
                                                              @RequestBody @Valid AlertaEdicaoRequest request) {
        return ResponseEntity.ok(assembler.toModel(alertaService.editar(id, request)));
    }

    @PatchMapping("/{id}/encerrar")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Encerra um alerta", description = "AGENTE ou ADMIN. O encerramento é sempre decisão humana e não pode ser desfeito.")
    @ApiResponse(responseCode = "200", description = "Alerta encerrado")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Alerta não existe")
    @ApiResponse(responseCode = "409", description = "Alerta já estava encerrado")
    public ResponseEntity<EntityModel<AlertaResponse>> encerrar(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(alertaService.encerrar(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui um alerta", description = "Somente ADMIN, para corrigir alerta manual emitido por engano, "
            + "depois de encerrado. O alerta automático registra uma leitura real e faz parte do histórico: não é excluído.")
    @ApiResponse(responseCode = "204", description = "Alerta excluído")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Alerta não existe")
    @ApiResponse(responseCode = "409", description = "Alerta automático, ou alerta manual ainda ativo")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        alertaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
