package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.assembler.AreaModelAssembler;
import br.com.fiap.guardario.assembler.UsuarioModelAssembler;
import br.com.fiap.guardario.dto.AlterarSenhaRequest;
import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.dto.AtualizarPerfilRequest;
import br.com.fiap.guardario.dto.AuthResponse;
import br.com.fiap.guardario.dto.LoginRequest;
import br.com.fiap.guardario.dto.RegisterRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.service.AreaSeguidaService;
import br.com.fiap.guardario.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@Validated
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Cadastro, login com JWT e dados do usuário logado")
public class AuthController {

    private final AuthService authService;
    private final UsuarioModelAssembler assembler;
    private final AreaSeguidaService areaSeguidaService;
    private final AreaModelAssembler areaAssembler;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Cadastra um morador",
            description = "Rota pública. O cadastro sempre cria o papel CIDADAO; só um ADMIN promove alguém a AGENTE.")
    @ApiResponse(responseCode = "201", description = "Usuário criado; o header Location aponta para o cadastro, que o próprio usuário consulta depois do login")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (email mal formado, senha com menos de 8 caracteres...)")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    @ApiResponse(responseCode = "409", description = "Email já cadastrado")
    public ResponseEntity<UsuarioResponse> registrar(@RequestBody @Valid RegisterRequest request) {
        UsuarioResponse criado = authService.registrar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/usuarios/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Faz login e devolve o token JWT",
            description = "Rota pública. Use o token no header Authorization: Bearer <token>. Ele vale 1 hora.")
    @ApiResponse(responseCode = "200", description = "Login feito; o corpo traz o token")
    @ApiResponse(responseCode = "400", description = "Email ou senha em branco")
    @ApiResponse(responseCode = "401", description = "Email ou senha inválidos")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PutMapping("/me")
    @Operation(summary = "Edita o perfil do usuário dono do token",
            description = "Qualquer usuário logado. O usuário vem do token. Muda só o nome, o telefone e a área onde a pessoa mora "
                    + "(areaId nulo deixa a pessoa sem área). Email, senha e papel não mudam por esta rota: o corpo nem tem esses "
                    + "campos, e mandar \"papel\" não tem efeito. A resposta tem o mesmo formato e os mesmos links do GET /api/auth/me.")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (nome em branco, telefone fora do formato, areaId menor ou igual a zero)")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    @ApiResponse(responseCode = "404", description = "A área informada não existe")
    @ApiResponse(responseCode = "409", description = "O cadastro foi alterado por outra requisição ao mesmo tempo (troca de senha, de papel ou do perfil): tente de novo")
    public ResponseEntity<EntityModel<UsuarioResponse>> atualizarPerfil(@RequestBody @Valid AtualizarPerfilRequest request) {
        return ResponseEntity.ok(assembler.toModel(authService.atualizarPerfil(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário dono do token", description = "Qualquer usuário logado. O app usa para saber o nome e o papel de quem entrou; a senha nunca aparece. "
            + "Os links levam ao cadastro (self), à área onde a pessoa mora e aos relatos dela.")
    @ApiResponse(responseCode = "200", description = "Usuário logado")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    public ResponseEntity<EntityModel<UsuarioResponse>> me() {
        return ResponseEntity.ok(assembler.toModel(authService.usuarioAtual()));
    }

    @PutMapping("/senha")
    @Operation(summary = "Troca a senha do usuário dono do token",
            description = "Qualquer usuário logado. O usuário vem do token, nunca de um id no corpo ou na URL. "
                    + "Confere a senha atual e grava a nova (de 8 a 72 caracteres, até 72 bytes) com BCrypt. "
                    + "A nova senha precisa ser diferente da atual. Depois da troca, todo token emitido antes deixa de valer, "
                    + "inclusive o usado nesta chamada e o de outras sessões (401): faça login de novo com a senha nova.")
    @ApiResponse(responseCode = "204", description = "Senha trocada; a resposta não tem corpo")
    @ApiResponse(responseCode = "400", description = "Senha atual incorreta (campo senhaAtual), ou nova senha em branco, com menos de 8 caracteres, "
            + "mais de 72 bytes ou igual à atual (campo novaSenha)")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    @ApiResponse(responseCode = "409", description = "O cadastro foi alterado por outra requisição ao mesmo tempo (troca de senha, de papel ou do perfil): tente de novo")
    public ResponseEntity<Void> alterarSenha(@RequestBody @Valid AlterarSenhaRequest request) {
        authService.alterarSenha(request);
        return ResponseEntity.noContent().build();
    }

    // ---------- áreas seguidas: o usuário acompanha outras áreas além da área do cadastro (N:M) ----------

    @PutMapping("/me/areas-seguidas/{areaId}")
    @Operation(summary = "Segue uma área",
            description = "Qualquer usuário logado, sempre no próprio cadastro: o usuário vem do token, e não existe rota para "
                    + "as áreas seguidas de outra pessoa. Acompanha a área além da área do cadastro. Idempotente: seguir de "
                    + "novo uma área que já segue também dá 204. O limite é de 20 áreas seguidas por usuário.")
    @ApiResponse(responseCode = "204", description = "Área seguida, agora ou de antes; a resposta não tem corpo")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero, ou o usuário já segue 20 áreas (campo areasSeguidas)")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    @ApiResponse(responseCode = "404", description = "A área não existe")
    public ResponseEntity<Void> seguirArea(@PathVariable @Positive Long areaId) {
        areaSeguidaService.seguir(areaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/areas-seguidas/{areaId}")
    @Operation(summary = "Deixa de seguir uma área",
            description = "Qualquer usuário logado, sempre no próprio cadastro. Idempotente: deixar de seguir uma área que "
                    + "não segue também dá 204. A área do cadastro não muda por esta rota.")
    @ApiResponse(responseCode = "204", description = "A área não é mais seguida; a resposta não tem corpo")
    @ApiResponse(responseCode = "400", description = "Id menor ou igual a zero")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    @ApiResponse(responseCode = "404", description = "A área não existe")
    public ResponseEntity<Void> deixarDeSeguirArea(@PathVariable @Positive Long areaId) {
        areaSeguidaService.deixarDeSeguir(areaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/areas-seguidas")
    @Operation(summary = "Lista as áreas seguidas",
            description = "Qualquer usuário logado, sempre as do próprio cadastro. Ordenadas por nome, no formato HAL e com os "
                    + "mesmos links de GET /api/areas/{id}. Quem não segue nenhuma recebe a lista vazia. Carrega o usuário e as "
                    + "áreas num SELECT só (@EntityGraph), sem N+1.")
    @ApiResponse(responseCode = "200", description = "Áreas seguidas (no máximo 20)")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    public ResponseEntity<CollectionModel<EntityModel<AreaResponse>>> areasSeguidas() {
        return ResponseEntity.ok(areaAssembler.toCollectionModel(areaSeguidaService.listar(),
                linkTo(methodOn(AuthController.class).areasSeguidas()).withSelfRel()));
    }
}
