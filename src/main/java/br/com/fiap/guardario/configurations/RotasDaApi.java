package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Diz quais métodos HTTP uma rota aceita de verdade, lendo os mapeamentos do próprio Spring: é o que vai no header
 * Allow de todo 405. Quando uma rota fixa casa com o caminho (/api/alertas/ativos), valem só os métodos dela, e não os
 * da rota com variável que também casaria (/api/alertas/{id}). O GET também responde ao HEAD, e toda rota responde ao
 * OPTIONS, como o próprio Spring informa numa requisição OPTIONS.
 */
@Component
public class RotasDaApi {

    private static final List<HttpMethod> ORDEM = List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS);

    private final ApplicationContext contexto;

    public RotasDaApi(ApplicationContext contexto) {
        this.contexto = contexto;
    }

    // métodos aceitos no caminho, com a rota fixa na frente da rota com variável; vazio se nenhuma rota casa
    public Set<HttpMethod> metodosAceitos(String caminho) {
        Set<RequestMethod> fixas = EnumSet.noneOf(RequestMethod.class);
        Set<RequestMethod> comVariavel = EnumSet.noneOf(RequestMethod.class);
        boolean casouFixa = false;
        boolean casouComVariavel = false;
        PathContainer alvo = PathContainer.parsePath(caminho);
        for (RequestMappingInfoHandlerMapping mapeamento : contexto.getBeansOfType(RequestMappingInfoHandlerMapping.class).values()) {
            for (RequestMappingInfo info : mapeamento.getHandlerMethods().keySet()) {
                PathPatternsRequestCondition padroes = info.getPathPatternsCondition();
                if (padroes == null) {
                    continue;
                }
                for (PathPattern padrao : padroes.getPatterns()) {
                    if (!padrao.matches(alvo)) {
                        continue;
                    }
                    Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
                    Set<RequestMethod> todos = metodos.isEmpty() ? EnumSet.allOf(RequestMethod.class) : metodos;
                    if (ehFixa(padrao)) {
                        casouFixa = true;
                        fixas.addAll(todos);
                    } else {
                        casouComVariavel = true;
                        comVariavel.addAll(todos);
                    }
                }
            }
        }
        if (!casouFixa && !casouComVariavel) {
            return Set.of();
        }
        Set<RequestMethod> declarados = casouFixa ? fixas : comVariavel;
        Set<HttpMethod> aceitos = new LinkedHashSet<>();
        for (HttpMethod metodo : ORDEM) {
            boolean declarado = declarados.contains(RequestMethod.resolve(metodo));
            boolean headDoGet = metodo == HttpMethod.HEAD && declarados.contains(RequestMethod.GET);
            if (declarado || headDoGet || metodo == HttpMethod.OPTIONS) {
                aceitos.add(metodo);
            }
        }
        return aceitos;
    }

    public Set<HttpMethod> metodosAceitos(HttpServletRequest request) {
        return metodosAceitos(request.getRequestURI().substring(request.getContextPath().length()));
    }

    // a requisição caiu numa rota com variável, mas o caminho é uma rota fixa que não aceita o método
    public boolean rotaFixaNaoAceita(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer alvo = PathContainer.parsePath(caminho);
        boolean existeFixa = false;
        for (RequestMappingInfoHandlerMapping mapeamento : contexto.getBeansOfType(RequestMappingInfoHandlerMapping.class).values()) {
            for (RequestMappingInfo info : mapeamento.getHandlerMethods().keySet()) {
                PathPatternsRequestCondition padroes = info.getPathPatternsCondition();
                if (padroes != null && padroes.getPatterns().stream().anyMatch(p -> ehFixa(p) && p.matches(alvo))) {
                    existeFixa = true;
                }
            }
        }
        return existeFixa && !metodosAceitos(caminho).contains(HttpMethod.valueOf(request.getMethod()));
    }

    private static boolean ehFixa(PathPattern padrao) {
        String texto = padrao.getPatternString();
        return !texto.contains("{") && !texto.contains("*");
    }
}
