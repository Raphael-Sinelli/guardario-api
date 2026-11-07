package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.configurations.CamposDeOrdenacao;
import br.com.fiap.guardario.assembler.UsuarioModelAssembler;
import br.com.fiap.guardario.dto.AlterarPapelRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// a área /api/usuarios é do ADMIN no SecurityConfig, menos o GET de um cadastro pelo id, que o próprio usuário também faz;
// o @PreAuthorize repete a regra em cada ação
@RestController
@RequestMapping("/api/usuarios")
@Validated
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gestão de usuários e papéis (ADMIN); cada usuário consulta o próprio cadastro")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioModelAssembler assembler;
    private final PagedResourcesAssembler<UsuarioResponse> pagedAssembler;

    @CamposDeOrdenacao({"nome", "email", "papel", "criadoEm", "id"})
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista os usuários", description = "Paginado e ordenável, ex.: ?page=0&size=10&sort=nome,asc")
    @ApiResponse(responseCode = "200", description = "Página de usuários")
    @ApiResponse(responseCode = "401", description = "Sem token")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    public ResponseEntity<PagedModel<EntityModel<UsuarioResponse>>> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(pagedAssembler.toModel(usuarioService.listar(pageable), assembler));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Busca um usuário pelo id",
            description = "O ADMIN vê qualquer cadastro; os outros papéis, só o próprio. É o endereço do Location "
                    + "devolvido no cadastro (POST /api/auth/register). A resposta não traz a senha.")
    @ApiResponse(responseCode = "200", description = "Usuário encontrado")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "403", description = "O cadastro é de outra pessoa e o usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Usuário não existe")
    public ResponseEntity<EntityModel<UsuarioResponse>> buscarPorId(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(assembler.toModel(usuarioService.buscarPorId(id)));
    }

    @PutMapping("/{id}/papel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Altera o papel de um usuário", description = "Usado para promover um cidadão a AGENTE da Defesa Civil.")
    @ApiResponse(responseCode = "200", description = "Papel alterado")
    @ApiResponse(responseCode = "400", description = "Papel inválido")
    @ApiResponse(responseCode = "403", description = "Usuário logado não é ADMIN")
    @ApiResponse(responseCode = "404", description = "Usuário não existe")
    @ApiResponse(responseCode = "409", description = "O ADMIN tentou alterar o próprio papel")
    public ResponseEntity<EntityModel<UsuarioResponse>> alterarPapel(@PathVariable @Positive Long id,
                                                                     @RequestBody @Valid AlterarPapelRequest request) {
        return ResponseEntity.ok(assembler.toModel(usuarioService.alterarPapel(id, request.papel())));
    }
}
