package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.EnumSet;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diz quais métodos HTTP uma rota aceita de verdade, lendo os mapeamentos do próprio Spring: é o que vai no header
 * Allow de todo 405 e do OPTIONS. Quando várias rotas casam com o caminho, valem só os métodos das que têm menos
 * variáveis: a rota fixa /api/alertas/ativos na frente de /api/alertas/{id}, e /api/estacoes/{id}/leituras/lote na
 * frente de /api/estacoes/{id}/leituras/{leituraId}. O GET também responde ao HEAD, e toda
 * rota responde ao OPTIONS. Sem rota de controller, um arquivo estático que existe (a página do Swagger UI) aceita
 * GET, HEAD e OPTIONS, como o Spring o serve. A página de erro não conta: não é rota da API.
 */
@Component
public class RotasDaApi {

    private static final List<HttpMethod> ORDEM = List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS);

    private final ApplicationContext contexto;

    public RotasDaApi(ApplicationContext contexto) {
        this.contexto = contexto;
    }

    // métodos aceitos no caminho, com a rota de menos variáveis na frente; vazio se nada responde no caminho
    public Set<HttpMethod> metodosAceitos(String caminho) {
        Set<RequestMethod> declarados = EnumSet.noneOf(RequestMethod.class);
        int menosVariaveis = Integer.MAX_VALUE;
        PathContainer alvo = PathContainer.parsePath(caminho);
        for (Map.Entry<RequestMappingInfo, HandlerMethod> rota : rotasDosControllers()) {
            PathPatternsRequestCondition padroes = rota.getKey().getPathPatternsCondition();
            for (PathPattern padrao : padroes.getPatterns()) {
                if (!padrao.matches(alvo)) {
                    continue;
                }
                Set<RequestMethod> metodos = rota.getKey().getMethodsCondition().getMethods();
                int variaveis = variaveis(padrao);
                if (variaveis < menosVariaveis) {
                    menosVariaveis = variaveis;
                    declarados.clear();
                }
                if (variaveis == menosVariaveis) {
                    declarados.addAll(metodos.isEmpty() ? EnumSet.allOf(RequestMethod.class) : metodos);
                }
            }
        }
        if (menosVariaveis == Integer.MAX_VALUE) {
            if (!arquivoEstatico(alvo)) {
                return Set.of();
            }
            declarados = EnumSet.of(RequestMethod.GET);
        }
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

    // a requisição caiu numa rota com mais variáveis (como /api/alertas/{id}), mas o caminho tem uma rota mais
    // específica (/api/alertas/ativos) que não aceita o método
    public boolean rotaFixaNaoAceita(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer alvo = PathContainer.parsePath(caminho);
        IntSummaryStatistics variaveis = rotasDosControllers().stream()
                .flatMap(rota -> rota.getKey().getPathPatternsCondition().getPatterns().stream())
                .filter(padrao -> padrao.matches(alvo))
                .mapToInt(RotasDaApi::variaveis)
                .summaryStatistics();
        boolean existeMaisEspecifica = variaveis.getCount() > 0 && variaveis.getMin() < variaveis.getMax();
        return existeMaisEspecifica && !metodosAceitos(caminho).contains(HttpMethod.valueOf(request.getMethod()));
    }

    // as rotas dos controllers e do Actuator, sem a página de erro (o /error não é rota: é o destino interno dos erros)
    private List<Map.Entry<RequestMappingInfo, HandlerMethod>> rotasDosControllers() {
        return contexto.getBeansOfType(RequestMappingInfoHandlerMapping.class).values().stream()
                .flatMap(mapeamento -> mapeamento.getHandlerMethods().entrySet().stream())
                .filter(rota -> rota.getKey().getPathPatternsCondition() != null)
                .filter(rota -> !ErrorController.class.isAssignableFrom(rota.getValue().getBeanType()))
                .toList();
    }

    // o arquivo existe num dos diretórios de arquivos estáticos? Vale o mapeamento mais específico que casa com o
    // caminho, como no Spring: /swagger-ui/index.html é procurado só no do Swagger UI, e não no /**
    private boolean arquivoEstatico(PathContainer alvo) {
        ResourceUrlProvider provedor = contexto.getBeanProvider(ResourceUrlProvider.class).getIfUnique();
        if (provedor == null) {
            return false;
        }
        PathPattern melhor = null;
        ResourceHttpRequestHandler arquivos = null;
        for (Map.Entry<String, ResourceHttpRequestHandler> mapeamento : provedor.getHandlerMap().entrySet()) {
            PathPattern padrao = PathPatternParser.defaultInstance.parse(mapeamento.getKey());
            if (padrao.matches(alvo) && (melhor == null || PathPattern.SPECIFICITY_COMPARATOR.compare(padrao, melhor) < 0)) {
                melhor = padrao;
                arquivos = mapeamento.getValue();
            }
        }
        if (arquivos == null) {
            return false;
        }
        String dentroDoMapeamento = melhor.extractPathWithinPattern(alvo).value();
        Resource arquivo = new Cadeia(arquivos.getResourceResolvers(), 0)
                .resolveResource(null, dentroDoMapeamento, arquivos.getLocations());
        return arquivo != null;
    }

    // variáveis e curingas do padrão: /api/alertas/ativos tem 0, /api/estacoes/{id}/leituras/{leituraId} tem 2
    private static int variaveis(PathPattern padrao) {
        return (int) padrao.getPatternString().chars().filter(c -> c == '{' || c == '*').count();
    }

    // a cadeia de resolvers do mapeamento, na ordem dele (a do Spring não é pública)
    private record Cadeia(List<ResourceResolver> resolvers, int posicao) implements ResourceResolverChain {

        @Override
        public Resource resolveResource(HttpServletRequest request, String caminho, List<? extends Resource> locais) {
            return posicao < resolvers.size()
                    ? resolvers.get(posicao).resolveResource(request, caminho, locais, new Cadeia(resolvers, posicao + 1))
                    : null;
        }

        @Override
        public String resolveUrlPath(String caminho, List<? extends Resource> locais) {
            return posicao < resolvers.size()
                    ? resolvers.get(posicao).resolveUrlPath(caminho, locais, new Cadeia(resolvers, posicao + 1))
                    : null;
        }
    }
}
