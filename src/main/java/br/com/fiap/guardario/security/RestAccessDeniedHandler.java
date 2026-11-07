package br.com.fiap.guardario.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 na cadeia de filtros: usuário logado tentando uma área de URL de outro papel
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    // a mesma mensagem do 403 do @PreAuthorize, no GlobalExceptionHandler
    public static final String SEM_PERMISSAO = "Você não tem permissão para esta operação";

    private final JsonErrorWriter jsonErrorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        jsonErrorWriter.escrever(request, response, HttpStatus.FORBIDDEN, SEM_PERMISSAO);
    }
}
