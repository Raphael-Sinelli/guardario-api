package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.assembler.EstacaoModelAssembler;
import br.com.fiap.guardario.assembler.LeituraModelAssembler;
import br.com.fiap.guardario.dto.*;
import br.com.fiap.guardario.service.EstacaoService;
import br.com.fiap.guardario.service.LeituraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/estacoes")
@Validated
@RequiredArgsConstructor
@Tag(name = "Estações e leituras", description = "Estações IoT que medem o rio e as leituras que elas enviam")
public class EstacaoController {

    private final EstacaoService estacaoService;
    private final LeituraService leituraService;
    private final EstacaoModelAssembler assembler;
    private final LeituraModelAssembler leituraAssembler;
    private final PagedResourcesAssembler<EstacaoResponse> pagedAssembler;
    private final PagedResourcesAssembler<LeituraResponse> pagedLeituraAssembler;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Lista as estações", description = "Pública, paginada e ordenável. Filtro opcional por área.")
    @ApiResponse(responseCode = "200", description = "Página de estações")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido")
    public ResponseEntity<PagedModel<EntityModel<EstacaoResponse>>> listar(
            @Parameter(description = "Id da área", example = "1") @RequestParam(required = false) @Positive Long areaId,
            @ParameterObject @PageableDefault(size = 10, sort = "codigo") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(estacaoService.listar(areaId, pageable), assembler));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Busca uma estação pelo id")
    @ApiResponse(responseCode = "200", description = "Estação encontrada")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "404", description = "Estação não existe")
    public ResponseEntity<EntityModel<EstacaoResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(estacaoService.buscarPorId(id)));
    }

    @GetMapping("/criticas")
    @SecurityRequirements
    @Operation(summary = "Estações em ALERTA ou EMERGENCIA",
            description = "JPQL com join fetch (sem N+1), da mais perto do transbordo para a mais longe.")
    @ApiResponse(responseCode = "200", description = "Estações críticas")
    public ResponseEntity<CollectionModel<EntityModel<EstacaoResponse>>> criticas() {
        CollectionModel<EntityModel<EstacaoResponse>> model = assembler.toCollectionModel(estacaoService.criticas())
                .add(linkTo(methodOn(EstacaoController.class).criticas()).withSelfRel());
        return ResponseEntity.ok(model);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra uma estação",
            description = "Somente ADMIN. As cotas precisam ser crescentes: atenção < alerta < transbordo.")
    @ApiResponse(responseCode = "201", description = "Estação criada")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou cotas fora de ordem")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    @ApiResponse(responseCode = "409", description = "Código de estação já usado")
    public ResponseEntity<EntityModel<EstacaoResponse>> criar(@RequestBody @Valid EstacaoRequest request) {
        EntityModel<EstacaoResponse> model = assembler.toModel(estacaoService.criar(request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza uma estação", description = "Somente ADMIN. Para tirar uma estação do ar, mude o status para MANUTENCAO ou INATIVA.")
    @ApiResponse(responseCode = "200", description = "Estação atualizada")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou cotas fora de ordem")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Estação ou área não existe")
    @ApiResponse(responseCode = "409", description = "Código de estação já usado por outra")
    public ResponseEntity<EntityModel<EstacaoResponse>> atualizar(@PathVariable @Positive Long id,
                                                                  @RequestBody @Valid EstacaoRequest request) {
        return ResponseEntity.ok(assembler.toModel(estacaoService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui uma estação", description = "Somente ADMIN. As leituras vão junto (cascade). Estação com alertas no histórico não pode ser excluída.")
    @ApiResponse(responseCode = "204", description = "Estação excluída")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Estação não existe")
    @ApiResponse(responseCode = "409", description = "A estação tem alertas no histórico")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        estacaoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- leituras (recurso aninhado: a estação vem da URL) ----------

    @GetMapping("/{id}/leituras")
    @SecurityRequirements
    @Operation(summary = "Histórico de leituras da estação", description = "Público, paginado; por padrão da mais recente para a mais antiga.")
    @ApiResponse(responseCode = "200", description = "Página de leituras")
    @ApiResponse(responseCode = "404", description = "Estação não existe")
    public ResponseEntity<PagedModel<EntityModel<LeituraResponse>>> listarLeituras(
            @PathVariable @Positive Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "medidaEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pagedLeituraAssembler.toModel(leituraService.listar(id, pageable), leituraAssembler));
    }

    @PostMapping("/{id}/leituras")
    @PreAuthorize("hasAnyRole('AGENTE', 'ADMIN')")
    @Operation(summary = "Registra uma leitura da estação",
            description = """
                    AGENTE ou ADMIN (é a rota que a ponte MQTT chama). O servidor classifica o risco pela régua da estação,
                    sobe um nível com chuva de 50 mm/h ou mais, recalcula o nível da área e, se o risco for ALERTA ou pior,
                    abre um alerta automático. O corpo da resposta traz o alertaGeradoId quando isso acontece.""")
    @ApiResponse(responseCode = "201", description = "Leitura registrada. O Location aponta para a leitura, e o link alerta aparece quando um alerta automático nasce")
    @ApiResponse(responseCode = "400", description = "Valores fora da faixa ou data no futuro")
    @ApiResponse(responseCode = "403", description = "Usuário logado é CIDADAO")
    @ApiResponse(responseCode = "404", description = "Estação não existe")
    @ApiResponse(responseCode = "409", description = "Estação em MANUTENCAO ou INATIVA")
    public ResponseEntity<EntityModel<LeituraRegistradaResponse>> registrarLeitura(@PathVariable @Positive Long id,
                                                                                   @RequestBody @Valid LeituraRequest request) {
        EntityModel<LeituraRegistradaResponse> model = leituraAssembler.toModel(leituraService.registrar(id, request));
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping("/{id}/leituras/{leituraId}")
    @SecurityRequirements
    @Operation(summary = "Busca uma leitura da estação", description = "Público. É o endereço do Location devolvido no registro da leitura.")
    @ApiResponse(responseCode = "200", description = "Leitura encontrada")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "404", description = "Estação não existe, ou a leitura não existe nesta estação")
    public ResponseEntity<EntityModel<LeituraResponse>> buscarLeitura(@PathVariable @Positive Long id,
                                                                      @PathVariable @Positive Long leituraId) {
        return ResponseEntity.ok(leituraAssembler.toModel(leituraService.buscarPorId(id, leituraId)));
    }

    @GetMapping("/{id}/leituras/estatisticas")
    @SecurityRequirements
    @Operation(summary = "Estatística das leituras numa janela de horas",
            description = "JPQL com count, avg, min e max calculados no banco.")
    @ApiResponse(responseCode = "200", description = "Estatística da janela pedida")
    @ApiResponse(responseCode = "400", description = "Janela fora de 1 a 720 horas")
    @ApiResponse(responseCode = "404", description = "Estação não existe")
    public ResponseEntity<EstatisticaLeiturasResponse> estatisticas(
            @PathVariable @Positive Long id,
            @Parameter(description = "Janela em horas (1 a 720)", example = "24")
            @RequestParam(defaultValue = "24") @Positive @Max(720) Integer horas) {
        return ResponseEntity.ok(leituraService.estatisticas(id, horas));
    }
}
