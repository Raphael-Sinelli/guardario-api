package br.com.fiap.guardario.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * O mesmo trabalho do CorsFilter do Spring, com uma diferença: a requisição recusada recebe 403 no JSON padrão da API
 * (ErrorCustomResponse, pelo JsonErrorWriter), com o motivo (origem, método ou header), e não o texto puro
 * "Invalid CORS request".
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
            jsonErrorWriter.escrever(request, response, HttpStatus.FORBIDDEN, motivoDaRecusa(cors, request));
            return;
        }
        // o preflight (OPTIONS) termina aqui, com os headers de CORS já na resposta
        if (CorsUtils.isPreFlightRequest(request)) {
            return;
        }
        chain.doFilter(request, response);
    }

    // o processador só diz que recusou; o motivo sai da mesma configuração, na ordem em que ele confere:
    // origem, depois o método e os headers que o preflight pede
    private static String motivoDaRecusa(CorsConfiguration cors, HttpServletRequest request) {
        String origem = request.getHeader(HttpHeaders.ORIGIN);
        if (cors == null || cors.checkOrigin(origem) == null) {
            return "Origem não permitida pelo CORS: " + origem;
        }
        if (CorsUtils.isPreFlightRequest(request)) {
            String metodo = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
            if (metodo == null || cors.checkHttpMethod(HttpMethod.valueOf(metodo.trim())) == null) {
                return "Método não permitido pelo CORS: " + metodo + ". Aceitos: " + juntar(cors.getAllowedMethods());
            }
            String pedidos = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
            List<String> recusados = pedidos == null ? List.of() : Arrays.stream(pedidos.split(","))
                    .map(String::trim)
                    .filter(header -> !header.isEmpty() && cors.checkHeaders(List.of(header)) == null)
                    .toList();
            if (!recusados.isEmpty()) {
                return "Header não permitido pelo CORS: " + String.join(", ", recusados) + ". Aceitos: "
                        + juntar(cors.getAllowedHeaders());
            }
        }
        return "Requisição recusada pelo CORS";
    }

    private static String juntar(List<String> valores) {
        return valores == null ? "" : String.join(", ", valores);
    }
}
