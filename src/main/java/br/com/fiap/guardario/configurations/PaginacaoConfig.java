package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Confere os parâmetros de lista (page, size e sort) antes do controller. Erro aqui vira 400 no GlobalExceptionHandler.
 * - page e size: page * size é o deslocamento do SELECT; acima do maior inteiro o banco recusa e a resposta seria 500.
 * - sort: o Pageable aceita ordenar por qualquer caminho, inclusive de outra entidade: ?sort=emitidoPor.senha (ou
 *   emitidoPorSenha, que o Spring Data resolve pelo camelCase) ordenaria os alertas pelo hash da senha de quem emitiu,
 *   numa rota pública. Só valem os campos da lista branca de cada rota, declarada com @CamposDeOrdenacao.
 */
@Configuration
public class PaginacaoConfig implements WebMvcConfigurer {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 100; // o mesmo de spring.data.web.pageable.max-page-size
    private static final Set<String> PALAVRAS_DE_DIRECAO = Set.of("asc", "desc", "ignorecase");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                conferirPagina(request);
                conferirOrdenacao(request, handler);
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

    private void conferirOrdenacao(HttpServletRequest request, Object handler) {
        String[] ordenacoes = request.getParameterValues("sort");
        if (ordenacoes == null) {
            return;
        }
        CamposDeOrdenacao anotacao = handler instanceof HandlerMethod metodo
                ? metodo.getMethodAnnotation(CamposDeOrdenacao.class) : null;
        List<String> aceitos = anotacao == null ? List.of() : List.of(anotacao.value());
        // "?sort=emitidoEm,titulo,desc": todo termo que não é direção (asc, desc, ignorecase) é mais um campo para o
        // Spring Data, então todos passam pela lista, e não só o primeiro
        for (String ordenacao : ordenacoes) {
            for (String termo : ordenacao.split(",")) {
                String campo = termo.trim();
                if (!campo.isEmpty() && !PALAVRAS_DE_DIRECAO.contains(campo.toLowerCase(Locale.ROOT)) && !aceitos.contains(campo)) {
                    throw new IllegalArgumentException(aceitos.isEmpty()
                            ? "Esta rota não aceita ordenação"
                            : "Ordenação não permitida por " + campo + ". Campos aceitos: " + String.join(", ", aceitos));
                }
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
