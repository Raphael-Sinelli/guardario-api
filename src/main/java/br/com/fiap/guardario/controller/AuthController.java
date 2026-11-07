package br.com.fiap.guardario.controller;

import br.com.fiap.guardario.dto.AuthResponse;
import br.com.fiap.guardario.dto.LoginRequest;
import br.com.fiap.guardario.dto.RegisterRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Cadastro, login com JWT e dados do usuário logado")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Cadastra um morador",
            description = "Rota pública. O cadastro sempre cria o papel CIDADAO; só um ADMIN promove alguém a AGENTE.")
    @ApiResponse(responseCode = "201", description = "Usuário criado; o header Location aponta para ele")
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

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário dono do token", description = "Qualquer usuário logado. O app usa para saber o nome e o papel de quem entrou; a senha nunca aparece.")
    @ApiResponse(responseCode = "200", description = "Usuário logado")
    @ApiResponse(responseCode = "401", description = "Sem token, ou token inválido ou expirado")
    public ResponseEntity<UsuarioResponse> me() {
        return ResponseEntity.ok(authService.usuarioAtual());
    }
}
