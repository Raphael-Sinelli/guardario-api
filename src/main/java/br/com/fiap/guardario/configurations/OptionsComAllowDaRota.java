package br.com.fiap.guardario.configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * O OPTIONS do Spring MVC junta no Allow os métodos de toda rota que casa com o caminho: em /api/alertas/ativos, que é
 * só GET, ele anunciava também o PUT e o DELETE de /api/alertas/{id}. Este filtro troca esse Allow pelo da RotasDaApi,
 * o mesmo que vai no 405. Onde a RotasDaApi não conhece o caminho, fica o Allow que o Spring escreveu.
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
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String nome, String valor) {
                super.setHeader(nome, HttpHeaders.ALLOW.equalsIgnoreCase(nome) ? allowDaRota(request, valor) : valor);
            }

            @Override
            public void addHeader(String nome, String valor) {
                if (HttpHeaders.ALLOW.equalsIgnoreCase(nome)) {
                    super.setHeader(nome, allowDaRota(request, valor));
                } else {
                    super.addHeader(nome, valor);
                }
            }
        });
    }

    private String allowDaRota(HttpServletRequest request, String allowDoSpring) {
        Set<HttpMethod> aceitos = rotas.metodosAceitos(request);
        if (aceitos.isEmpty()) {
            return allowDoSpring;
        }
        return aceitos.stream().map(HttpMethod::name).collect(Collectors.joining(","));
    }
}
