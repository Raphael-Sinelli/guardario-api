package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.configurations.CamposDeOrdenacao;
import br.com.fiap.guardario.assembler.AreaModelAssembler;
import br.com.fiap.guardario.dto.AreaRequest;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.RankingAreaResponse;
import br.com.fiap.guardario.service.AreaService;
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

@RestController
@RequestMapping("/api/areas")
@Validated
@RequiredArgsConstructor
@Tag(name = "Áreas de risco", description = "Bairros monitorados e o nível de risco atual de cada um")
public class AreaController {

    private final AreaService areaService;
    private final AreaModelAssembler assembler;
    private final PagedResourcesAssembler<AreaResponse> pagedAssembler;

    @CamposDeOrdenacao({"nome", "cidade", "uf", "populacaoEstimada", "nivelAtual", "id"})
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Lista as áreas", description = "Pública, paginada e ordenável (ex.: ?page=0&size=10&sort=nome,asc). Filtro opcional por UF.")
    @ApiResponse(responseCode = "200", description = "Página de áreas com links de navegação")
    @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido")
    public ResponseEntity<PagedModel<EntityModel<AreaResponse>>> listar(
            @Parameter(description = "Sigla do estado", example = "SP") @RequestParam(required = false) String uf,
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(assembler.toPagedModel(areaService.listar(uf, pageable), pagedAssembler));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Busca uma área pelo id", description = "Pública e em cache: mostra o nível de risco atual e o que fazer.")
    @ApiResponse(responseCode = "200", description = "Área encontrada")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "404", description = "Área não existe")
    public ResponseEntity<EntityModel<AreaResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(areaService.buscarPorId(id)));
    }

    @GetMapping("/ranking")
    @SecurityRequirements
    @Operation(summary = "Ranking das áreas por relatos em aberto",
            description = "Consulta JPQL com left join, group by, count e max. Ajuda a Defesa Civil a decidir para onde mandar equipes.")
    @ApiResponse(responseCode = "200", description = "Áreas ordenadas pela quantidade de relatos em aberto")
    public ResponseEntity<CollectionModel<EntityModel<RankingAreaResponse>>> ranking() {
        return ResponseEntity.ok(assembler.toRankingModel(areaService.ranking()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra uma área", description = "Somente ADMIN. A área nasce com nível NORMAL.")
    @ApiResponse(responseCode = "201", description = "Área criada; o header Location aponta para ela")
    @ApiResponse(responseCode = "400", description = "Campos inválidos")
    @ApiResponse(responseCode = "401", description = "Sem token")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    public ResponseEntity<EntityModel<AreaResponse>> criar(@RequestBody @Valid AreaRequest request) {
        EntityModel<AreaResponse> model = assembler.toModel(areaService.criar(request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza uma área", description = "Somente ADMIN. O nível de risco não é editável: vem das leituras.")
    @ApiResponse(responseCode = "200", description = "Área atualizada")
    @ApiResponse(responseCode = "400", description = "Campos inválidos")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Área não existe")
    public ResponseEntity<EntityModel<AreaResponse>> atualizar(@PathVariable @Positive Long id,
                                                               @RequestBody @Valid AreaRequest request) {
        return ResponseEntity.ok(assembler.toModel(areaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui uma área", description = "Somente ADMIN. Área com estações, abrigos, alertas ou relatos não pode ser excluída.")
    @ApiResponse(responseCode = "204", description = "Área excluída")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Área não existe")
    @ApiResponse(responseCode = "409", description = "A área tem registros vinculados")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        areaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
