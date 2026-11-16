package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Confere os parâmetros de lista (page, size e sort) antes do controller. Erro aqui vira 400 no GlobalExceptionHandler.
 * - page e size: page * size é o deslocamento do SELECT; acima do maior inteiro o banco recusa e a resposta seria 500.
 *   O size que entra na conta é o que o Spring Data vai usar: zero, negativo ou que não é número vira o padrão da rota.
 * - sort: o Pageable aceita ordenar por qualquer caminho, inclusive de outra entidade: ?sort=emitidoPor.senha (ou
 *   emitidoPorSenha, que o Spring Data resolve pelo camelCase) ordenaria os alertas pelo hash da senha de quem emitiu,
 *   numa rota pública. Só valem os campos da lista branca de cada rota, declarada com @CamposDeOrdenacao.
 */
@Configuration
public class PaginacaoConfig implements WebMvcConfigurer {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 100; // o mesmo de spring.data.web.pageable.max-page-size
    private static final Set<String> DIRECOES = Set.of("asc", "desc");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                conferirPagina(request, handler);
                conferirOrdenacao(request, handler);
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    private void conferirPagina(HttpServletRequest request, Object handler) {
        long pagina = numero(request.getParameter("page"), 0);
        long pedido = numero(request.getParameter("size"), 0);
        long tamanho = Math.min(pedido > 0 ? pedido : tamanhoPadrao(handler), TAMANHO_MAXIMO);
        if (pagina * tamanho > Integer.MAX_VALUE) {
            throw new CampoInvalidoException("page", "fora do intervalo: page vezes size passa de " + Integer.MAX_VALUE);
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
        List<String> deTexto = anotacao == null ? List.of() : List.of(anotacao.texto());
        // "?sort=emitidoEm,titulo,desc": todo termo que não é direção (asc, desc, ignorecase) é mais um campo para o
        // Spring Data, então todos passam pela lista, e não só o primeiro. O strip tira só espaço: um caractere de
        // controle (como %00) fica no termo e cai na lista, em vez de chegar ao Spring Data e virar 500
        for (String ordenacao : ordenacoes) {
            List<String> campos = new ArrayList<>();
            boolean ignorarCaixa = false;
            for (String termo : ordenacao.split(",")) {
                String campo = termo.strip();
                String minusculo = campo.toLowerCase(Locale.ROOT);
                if (campo.isEmpty() || DIRECOES.contains(minusculo)) {
                    continue;
                }
                if (minusculo.equals("ignorecase")) {
                    ignorarCaixa = true;
                    continue;
                }
                if (!aceitos.contains(campo)) {
                    throw new CampoInvalidoException("sort", aceitos.isEmpty()
                            ? "Esta rota não aceita ordenação"
                            : "Ordenação não permitida por " + campo + ". Campos aceitos: " + String.join(", ", aceitos));
                }
                campos.add(campo);
            }
            // o ignorecase compara em maiúsculas: em número, data ou enum o banco recusa o upper() e a resposta seria 500
            if (ignorarCaixa && !deTexto.containsAll(campos)) {
                throw new CampoInvalidoException("sort", deTexto.isEmpty()
                        ? "Esta rota não aceita ignorecase: nenhum campo da ordenação é texto"
                        : "ignorecase só vale para campos de texto: " + String.join(", ", deTexto));
            }
        }
    }

    // o size do @PageableDefault da rota (10 na maioria), ou o padrão do Spring Data (20) quando a rota não declara.
    // size e value são o mesmo atributo: vale o que foi escrito, como no resolver do Spring Data
    private static long tamanhoPadrao(Object handler) {
        if (handler instanceof HandlerMethod metodo) {
            for (MethodParameter parametro : metodo.getMethodParameters()) {
                PageableDefault padrao = parametro.getParameterAnnotation(PageableDefault.class);
                if (padrao != null) {
                    return padrao.size() != 10 ? padrao.size() : padrao.value();
                }
            }
        }
        return TAMANHO_PADRAO;
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
