package br.com.fiap.guardario.configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * O OPTIONS do Spring MVC junta no Allow os métodos de toda rota que casa com o caminho: em /api/alertas/ativos, que é
 * só GET, ele anunciava também o PUT e o DELETE de /api/alertas/{id}. Este filtro troca esse Allow pelo da RotasDaApi,
 * o mesmo que vai no 405. Onde nada responde no caminho (o /error, um caminho do Actuator que não existe), o OPTIONS
 * é 404, como o GET, sem anunciar método nenhum. Caminho de outro servlet (o console do H2, no perfil dev) fica como o
 * servlet dele responder.
 */
@Component
public class OptionsComAllowDaRota extends OncePerRequestFilter {

    private final RotasDaApi rotas;

    public OptionsComAllowDaRota(RotasDaApi rotas) {
        this.rotas = rotas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!caminhoDoSpring(request)) {
            chain.doFilter(request, response);
            return;
        }
        Set<HttpMethod> aceitos = rotas.metodosAceitos(request);
        if (aceitos.isEmpty()) {
            response.sendError(HttpStatus.NOT_FOUND.value()); // o corpo sai no JSON padrão, pelo /error
            return;
        }
        String allow = aceitos.stream().map(HttpMethod::name).collect(Collectors.joining(","));
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String nome, String valor) {
                super.setHeader(nome, HttpHeaders.ALLOW.equalsIgnoreCase(nome) ? allow : valor);
            }

            @Override
            public void addHeader(String nome, String valor) {
                if (HttpHeaders.ALLOW.equalsIgnoreCase(nome)) {
                    super.setHeader(nome, allow);
                } else {
                    super.addHeader(nome, valor);
                }
            }
        });
    }

    // a requisição vai para o DispatcherServlet (no MockMvc o nome do servlet vem vazio)
    private static boolean caminhoDoSpring(HttpServletRequest request) {
        HttpServletMapping mapeamento = request.getHttpServletMapping();
        String servlet = mapeamento != null ? mapeamento.getServletName() : null;
        return servlet == null || servlet.isEmpty()
                || DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME.equals(servlet);
    }
}
