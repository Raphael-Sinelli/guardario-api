package br.com.fiap.guardario.security;

import br.com.fiap.guardario.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lê o header "Authorization: Bearer <token>", valida o JWT e coloca o usuário no SecurityContext.
 * Não é @Component de propósito: é criado no SecurityConfig e só roda dentro da cadeia do Spring Security.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(PREFIXO)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIXO.length());
        try {
            String email = jwtService.extrairEmail(token);
            // o papel vem do banco a cada requisição: um "papel" forjado no token não adianta
            usuarioRepository.findByEmail(email).ifPresent(usuario -> {
                var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            });
        } catch (JwtException | IllegalArgumentException e) {
            // token inválido não responde nada aqui: segue sem usuário e, se a rota exigir login, o entry point devolve 401
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
