package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.assembler.AbrigoModelAssembler;
import br.com.fiap.guardario.dto.AbrigoRequest;
import br.com.fiap.guardario.dto.AbrigoResponse;
import br.com.fiap.guardario.dto.AlterarStatusAbrigoRequest;
import br.com.fiap.guardario.dto.MovimentacaoAbrigoRequest;
import br.com.fiap.guardario.model.StatusAbrigo;
import br.com.fiap.guardario.service.AbrigoService;
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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/abrigos")
@Validated
@RequiredArgsConstructor
@Tag(name = "Abrigos", description = "Abrigos com vaga, lotação e entrada e saída de pessoas")
public class AbrigoController {

    private final AbrigoService abrigoService;
    private final AbrigoModelAssembler assembler;
    private final PagedResourcesAssembler<AbrigoResponse> pagedAssembler;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Busca abrigos com filtros opcionais",
            description = "Pública. Filtros combináveis montados com Specification, ex.: ?aceitaPets=true&acessivelPcd=true&comVagas=true")
    @ApiResponse(responseCode = "200", description = "Página de abrigos")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido")
    public ResponseEntity<PagedModel<EntityModel<AbrigoResponse>>> listar(
            @Parameter(description = "Id da área", example = "1") @RequestParam(required = false) @Positive Long areaId,
            @Parameter(description = "Só os que aceitam animais") @RequestParam(required = false) Boolean aceitaPets,
            @Parameter(description = "Só os acessíveis para pessoas com deficiência") @RequestParam(required = false) Boolean acessivelPcd,
            @Parameter(description = "Só os que têm vaga agora") @RequestParam(required = false) Boolean comVagas,
            @Parameter(description = "ABERTO, LOTADO ou FECHADO") @RequestParam(required = false) StatusAbrigo status,
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        var pagina = abrigoService.listar(areaId, aceitaPets, acessivelPcd, comVagas, status, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(pagina, assembler));
    }

    @GetMapping("/disponiveis")
    @SecurityRequirements
    @Operation(summary = "Abrigos abertos e com vaga", description = "Pública e em cache. O cache é limpo em toda entrada, saída ou alteração de abrigo.")
    @ApiResponse(responseCode = "200", description = "Abrigos com vaga, por nome")
    public ResponseEntity<CollectionModel<EntityModel<AbrigoResponse>>> disponiveis() {
        CollectionModel<EntityModel<AbrigoResponse>> model = assembler.toCollectionModel(abrigoService.disponiveis())
                .add(linkTo(methodOn(AbrigoController.class).disponiveis()).withSelfRel());
        return ResponseEntity.ok(model);
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Busca um abrigo pelo id", description = "Os links de ação mudam com a ocupação e o status.")
    @ApiResponse(responseCode = "200", description = "Abrigo encontrado")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "404", description = "Abrigo não existe")
    public ResponseEntity<EntityModel<AbrigoResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(abrigoService.buscarPorId(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Cadastra um abrigo", description = "AGENTE ou ADMIN. A ocupação não pode passar da capacidade.")
    @ApiResponse(responseCode = "201", description = "Abrigo criado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou ocupação maior que a capacidade")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    public ResponseEntity<EntityModel<AbrigoResponse>> criar(@RequestBody @Valid AbrigoRequest request) {
        EntityModel<AbrigoResponse> model = assembler.toModel(abrigoService.criar(request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Atualiza um abrigo", description = "AGENTE ou ADMIN. O status LOTADO ou ABERTO é recalculado pela ocupação.")
    @ApiResponse(responseCode = "200", description = "Abrigo atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou ocupação maior que a capacidade")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Abrigo ou área não existe")
    public ResponseEntity<EntityModel<AbrigoResponse>> atualizar(@PathVariable @Positive Long id,
                                                                 @RequestBody @Valid AbrigoRequest request) {
        return ResponseEntity.ok(assembler.toModel(abrigoService.atualizar(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Fecha ou reabre um abrigo", description = "AGENTE ou ADMIN. Aceita ABERTO ou FECHADO; LOTADO é calculado pelo sistema.")
    @ApiResponse(responseCode = "200", description = "Status alterado")
    @ApiResponse(responseCode = "400", description = "Status LOTADO enviado pelo cliente")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Abrigo não existe")
    public ResponseEntity<EntityModel<AbrigoResponse>> alterarStatus(@PathVariable @Positive Long id,
                                                                     @RequestBody @Valid AlterarStatusAbrigoRequest request) {
        return ResponseEntity.ok(assembler.toModel(abrigoService.alterarStatus(id, request.status())));
    }

    @PostMapping("/{id}/entradas")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Registra a entrada de pessoas", description = "AGENTE ou ADMIN. Quando a ocupação chega à capacidade, o abrigo vira LOTADO.")
    @ApiResponse(responseCode = "200", description = "Entrada registrada")
    @ApiResponse(responseCode = "400", description = "Quantidade inválida")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Abrigo não existe")
    @ApiResponse(responseCode = "409", description = "Abrigo fechado ou sem vagas suficientes")
    public ResponseEntity<EntityModel<AbrigoResponse>> registrarEntrada(@PathVariable @Positive Long id,
                                                                        @RequestBody @Valid MovimentacaoAbrigoRequest request) {
        return ResponseEntity.ok(assembler.toModel(abrigoService.registrarEntrada(id, request.pessoas())));
    }

    @PostMapping("/{id}/saidas")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Registra a saída de pessoas", description = "AGENTE ou ADMIN. Se o abrigo estava LOTADO, volta a ABERTO.")
    @ApiResponse(responseCode = "200", description = "Saída registrada")
    @ApiResponse(responseCode = "400", description = "Quantidade inválida")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Abrigo não existe")
    @ApiResponse(responseCode = "409", description = "Saída maior que a ocupação atual")
    public ResponseEntity<EntityModel<AbrigoResponse>> registrarSaida(@PathVariable @Positive Long id,
                                                                      @RequestBody @Valid MovimentacaoAbrigoRequest request) {
        return ResponseEntity.ok(assembler.toModel(abrigoService.registrarSaida(id, request.pessoas())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui um abrigo", description = "Somente ADMIN.")
    @ApiResponse(responseCode = "204", description = "Abrigo excluído")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Abrigo não existe")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        abrigoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
