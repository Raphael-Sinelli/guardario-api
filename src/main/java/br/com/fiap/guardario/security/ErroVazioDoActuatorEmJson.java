package br.com.fiap.guardario.security;

import br.com.fiap.guardario.configurations.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Alguns erros do Actuator saem com o status certo e o corpo vazio: /actuator/health/xyz, um componente que não
 * existe, responde 404 sem nada escrito. O @RestControllerAdvice não vê esse caso, porque não há exceção. Este filtro
 * só olha as rotas do Actuator: se a resposta é de erro e veio vazia, escreve o mesmo JSON de erro do resto da API.
 */
@Component
@RequiredArgsConstructor
public class ErroVazioDoActuatorEmJson extends OncePerRequestFilter {

    private final JsonErrorWriter jsonErrorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper resposta = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, resposta);

        HttpStatus status = HttpStatus.resolve(resposta.getStatus());
        boolean erroSemCorpo = status != null && status.isError() && resposta.getContentSize() == 0;
        if (erroSemCorpo && !HttpMethod.HEAD.matches(request.getMethod()) && !resposta.isCommitted()) {
            jsonErrorWriter.escrever(request, resposta, status, GlobalExceptionHandler.mensagemPadrao(status));
        }
        resposta.copyBodyToResponse();
    }
}
