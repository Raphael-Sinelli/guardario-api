package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O Pageable aceita ordenar por qualquer caminho, inclusive de outra entidade: ?sort=emitidoPor.senha ordenaria os
 * alertas pelo hash da senha de quem emitiu, numa rota pública. Este interceptor só deixa ordenar pelos campos do
 * próprio recurso (sem ponto) e nunca pela senha. Campo que não existe continua dando 400 pelo Spring Data.
 */
@Configuration
public class OrdenacaoConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String[] ordenacoes = request.getParameterValues("sort");
                if (ordenacoes != null) {
                    for (String ordenacao : ordenacoes) {
                        String campo = ordenacao.split(",")[0].trim();
                        if (campo.contains(".") || campo.equalsIgnoreCase("senha")) {
                            // vira 400 no GlobalExceptionHandler
                            throw new IllegalArgumentException("Ordenação não permitida por " + campo
                                    + ": use um campo do próprio recurso, como nome ou criadoEm");
                        }
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
