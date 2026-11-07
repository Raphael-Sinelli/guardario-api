package br.com.fiap.guardario.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * O mesmo trabalho do CorsFilter do Spring, com uma diferença: a origem recusada recebe 403 no JSON padrão da API
 * (ErrorCustomResponse, pelo JsonErrorWriter), e não o texto puro "Invalid CORS request".
 * Como o JwtAuthenticationFilter, não é @Component: o SecurityConfig cria e põe na cadeia de segurança.
 */
public class CorsComErroEmJson extends OncePerRequestFilter {

    private final CorsConfigurationSource configuracao;
    private final JsonErrorWriter jsonErrorWriter;

    // o processador do Spring decide se a origem passa; quem escreve a recusa é este filtro
    private final DefaultCorsProcessor processador = new DefaultCorsProcessor() {
        @Override
        protected void rejectRequest(ServerHttpResponse response) {
        }
    };

    public CorsComErroEmJson(CorsConfigurationSource configuracao, JsonErrorWriter jsonErrorWriter) {
        this.configuracao = configuracao;
        this.jsonErrorWriter = jsonErrorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CorsConfiguration cors = configuracao.getCorsConfiguration(request);
        if (!processador.processRequest(cors, request, response)) {
            jsonErrorWriter.escrever(request, response, HttpStatus.FORBIDDEN,
                    "Origem não permitida pelo CORS: " + request.getHeader("Origin"));
            return;
        }
        // o preflight (OPTIONS) termina aqui, com os headers de CORS já na resposta
        if (CorsUtils.isPreFlightRequest(request)) {
            return;
        }
        chain.doFilter(request, response);
    }
}
