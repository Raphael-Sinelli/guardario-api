package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.configurations.CamposDeOrdenacao;
import br.com.fiap.guardario.assembler.RelatoModelAssembler;
import br.com.fiap.guardario.dto.AlterarStatusRelatoRequest;
import br.com.fiap.guardario.dto.RelatoRequest;
import br.com.fiap.guardario.dto.RelatoResponse;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.TipoRelato;
import br.com.fiap.guardario.service.RelatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/relatos")
@Validated
@RequiredArgsConstructor
@Tag(name = "Relatos", description = "Relatos dos moradores com prioridade calculada e atendimento da Defesa Civil")
public class RelatoController {

    private final RelatoService relatoService;
    private final RelatoModelAssembler assembler;
    private final PagedResourcesAssembler<RelatoResponse> pagedAssembler;

    @PostMapping
    @Operation(summary = "Envia um relato",
            description = "Qualquer usuário logado. O autor vem do token e a prioridade (1 a 5) é calculada pelo servidor: "
                    + "base do tipo, +1 se a área estiver em ALERTA ou EMERGENCIA.")
    @ApiResponse(responseCode = "201", description = "Relato criado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (descrição com menos de 10 caracteres, tipo inexistente...)")
    @ApiResponse(responseCode = "401", description = "Sem token")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    public ResponseEntity<EntityModel<RelatoResponse>> criar(@RequestBody @Valid RelatoRequest request) {
        EntityModel<RelatoResponse> model = assembler.toModel(relatoService.criar(request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @CamposDeOrdenacao({"criadoEm", "atualizadoEm", "prioridade", "status", "tipo", "id"})
    @GetMapping("/meus")
    @Operation(summary = "Relatos do usuário logado", description = "Paginado; por padrão do mais recente para o mais antigo.")
    @ApiResponse(responseCode = "200", description = "Página com os relatos do usuário")
    @ApiResponse(responseCode = "401", description = "Sem token")
    public ResponseEntity<PagedModel<EntityModel<RelatoResponse>>> meus(
            @ParameterObject @PageableDefault(size = 10, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(relatoService.meus(pageable), assembler));
    }

    @CamposDeOrdenacao({"criadoEm", "atualizadoEm", "prioridade", "status", "tipo", "id"})
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Painel de relatos da Defesa Civil",
            description = "AGENTE ou ADMIN. Filtros opcionais e combináveis montados com Specification. "
                    + "Por padrão, maior prioridade primeiro e, no empate, o mais antigo primeiro.")
    @ApiResponse(responseCode = "200", description = "Página de relatos filtrados")
    @ApiResponse(responseCode = "400", description = "Filtro inválido (tipo ou status fora do enum, data mal formatada, 'de' depois de 'ate')")
    @ApiResponse(responseCode = "401", description = "Sem token")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    public ResponseEntity<PagedModel<EntityModel<RelatoResponse>>> listar(
            @Parameter(description = "Tipo do relato") @RequestParam(required = false) TipoRelato tipo,
            @Parameter(description = "Status do relato") @RequestParam(required = false) StatusRelato status,
            @Parameter(description = "Id da área", example = "1") @RequestParam(required = false) @Positive Long areaId,
            @Parameter(description = "Prioridade mínima (1 a 5)", example = "4")
            @RequestParam(required = false) @Min(1) @Max(5) Integer prioridadeMin,
            @Parameter(description = "Criados a partir de (UTC, ISO 8601, como 2026-11-01T00:00:00Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @Parameter(description = "Criados até (UTC, ISO 8601, como 2026-11-30T23:59:59Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @ParameterObject @PageableDefault(size = 10)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "prioridade", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "criadoEm", direction = Sort.Direction.ASC)}) Pageable pageable) {
        var pagina = relatoService.listar(tipo, status, areaId, prioridadeMin, de, ate, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(pagina, assembler));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um relato pelo id", description = "O autor vê o próprio relato; AGENTE e ADMIN veem todos.")
    @ApiResponse(responseCode = "200", description = "Relato encontrado")
    @ApiResponse(responseCode = "401", description = "Sem token")
    @ApiResponse(responseCode = "403", description = "O relato é de outra pessoa")
    @ApiResponse(responseCode = "404", description = "Relato não existe")
    public ResponseEntity<EntityModel<RelatoResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(relatoService.buscarPorId(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita um relato", description = "Só o autor e só enquanto o relato está ABERTO. A prioridade é recalculada.")
    @ApiResponse(responseCode = "200", description = "Relato atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos")
    @ApiResponse(responseCode = "403", description = "O relato é de outra pessoa")
    @ApiResponse(responseCode = "404", description = "Relato ou área não existe")
    @ApiResponse(responseCode = "409", description = "O relato já está em atendimento ou finalizado")
    public ResponseEntity<EntityModel<RelatoResponse>> atualizar(@PathVariable @Positive Long id,
                                                                 @RequestBody @Valid RelatoRequest request) {
        return ResponseEntity.ok(assembler.toModel(relatoService.atualizar(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Muda o status do relato",
            description = "AGENTE ou ADMIN. ABERTO vai para EM_ATENDIMENTO ou DESCARTADO; EM_ATENDIMENTO vai para RESOLVIDO.")
    @ApiResponse(responseCode = "200", description = "Status alterado")
    @ApiResponse(responseCode = "400", description = "Status fora do enum")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Relato não existe")
    @ApiResponse(responseCode = "409", description = "Transição de status inválida")
    public ResponseEntity<EntityModel<RelatoResponse>> alterarStatus(@PathVariable @Positive Long id,
                                                                     @RequestBody @Valid AlterarStatusRelatoRequest request) {
        return ResponseEntity.ok(assembler.toModel(relatoService.alterarStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um relato", description = "O autor exclui o próprio relato enquanto está ABERTO; o ADMIN exclui qualquer um.")
    @ApiResponse(responseCode = "204", description = "Relato excluído")
    @ApiResponse(responseCode = "403", description = "O relato é de outra pessoa")
    @ApiResponse(responseCode = "404", description = "Relato não existe")
    @ApiResponse(responseCode = "409", description = "O relato já está em atendimento ou finalizado")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        relatoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
