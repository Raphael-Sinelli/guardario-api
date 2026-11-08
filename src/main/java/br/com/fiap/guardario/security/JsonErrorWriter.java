package br.com.fiap.guardario.security;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Os erros 401 e 403 da cadeia de filtros acontecem antes do DispatcherServlet,
 * onde o @RestControllerAdvice não alcança. Esta classe escreve o mesmo JSON do GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class JsonErrorWriter {

    private final ObjectMapper objectMapper;

    public void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String mensagem)
            throws IOException {
        ErrorCustomResponse corpo = new ErrorCustomResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), mensagem, request.getRequestURI());

        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, desafioBearer(request));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }

    // RFC 6750: todo 401 diz como se autenticar (WWW-Authenticate: Bearer). Se a requisição mandou um token e ele
    // não valeu (adulterado, expirado ou de outra chave), o desafio diz que o token é inválido
    public static String desafioBearer(HttpServletRequest request) {
        String autorizacao = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean mandouToken = autorizacao != null && autorizacao.regionMatches(true, 0, "Bearer ", 0, 7);
        return mandouToken ? "Bearer error=\"invalid_token\"" : "Bearer";
    }
}
