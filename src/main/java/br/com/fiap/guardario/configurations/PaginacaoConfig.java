package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Confere os parâmetros de lista (page, size e sort) antes do controller. Erro aqui vira 400 no GlobalExceptionHandler.
 * - page e size: page * size é o deslocamento do SELECT; acima do maior inteiro o banco recusa e a resposta seria 500.
 * - sort: o Pageable aceita ordenar por qualquer caminho, inclusive de outra entidade: ?sort=emitidoPor.senha ordenaria
 *   os alertas pelo hash da senha de quem emitiu, numa rota pública. Só vale campo do próprio recurso, e nunca a senha.
 */
@Configuration
public class PaginacaoConfig implements WebMvcConfigurer {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 100; // o mesmo de spring.data.web.pageable.max-page-size

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                conferirPagina(request);
                conferirOrdenacao(request);
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    private void conferirPagina(HttpServletRequest request) {
        long pagina = numero(request.getParameter("page"), 0);
        long tamanho = Math.min(numero(request.getParameter("size"), TAMANHO_PADRAO), TAMANHO_MAXIMO);
        if (pagina * tamanho > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Parâmetro page fora do intervalo: page vezes size passa de " + Integer.MAX_VALUE);
        }
    }

    private void conferirOrdenacao(HttpServletRequest request) {
        String[] ordenacoes = request.getParameterValues("sort");
        if (ordenacoes == null) {
            return;
        }
        for (String ordenacao : ordenacoes) {
            String campo = ordenacao.split(",")[0].trim();
            if (campo.contains(".") || campo.equalsIgnoreCase("senha")) {
                throw new IllegalArgumentException("Ordenação não permitida por " + campo
                        + ": use um campo do próprio recurso, como nome ou criadoEm");
            }
        }
    }

    // valor que não é número fica com o padrão, como faz o próprio Spring Data
    private static long numero(String valor, long padrao) {
        try {
            return valor == null ? padrao : Math.max(Long.parseLong(valor.trim()), 0);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
