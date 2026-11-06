package br.com.fiap.guardario.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 401: rota protegida sem token, ou com token inválido ou expirado
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonErrorWriter jsonErrorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        jsonErrorWriter.escrever(request, response, HttpStatus.UNAUTHORIZED,
                "Token ausente, inválido ou expirado. Faça login em /api/auth/login");
    }
}
