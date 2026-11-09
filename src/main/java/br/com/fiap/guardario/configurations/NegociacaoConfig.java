package br.com.fiap.guardario.configurations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Set;

/**
 * Os mapeamentos não declaram produces, então o Spring só confere o Accept depois de o método rodar: um POST com
 * Accept: application/xml gravava a leitura e só então respondia 406, e o cliente que tentasse de novo gravava em
 * dobro. Este interceptor confere o Accept antes das escritas (POST, PUT e PATCH): se o cliente não aceita JSON, o 406
 * sai antes de gravar, pelo GlobalExceptionHandler. O DELETE responde 204, sem corpo, e não tem o que negociar.
 */
@Configuration
public class NegociacaoConfig implements WebMvcConfigurer {

    private static final List<MediaType> PRODUZIDOS = List.of(MediaType.APPLICATION_JSON, MediaTypes.HAL_JSON, MediaTypes.HAL_FORMS_JSON);
    private static final Set<String> ESCRITAS = Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name());

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws HttpMediaTypeNotAcceptableException {
                String accept = request.getHeader(HttpHeaders.ACCEPT);
                if (!ESCRITAS.contains(request.getMethod()) || accept == null || accept.isBlank()) {
                    return true;
                }
                List<MediaType> aceitos;
                try {
                    aceitos = MediaType.parseMediaTypes(accept);
                } catch (InvalidMediaTypeException e) {
                    throw new HttpMediaTypeNotAcceptableException("Accept inválido: " + accept);
                }
                // q=0 quer dizer "não aceito"; o */* e o application/* também servem
                boolean aceitaJson = aceitos.stream()
                        .filter(tipo -> tipo.getQualityValue() > 0)
                        .anyMatch(tipo -> PRODUZIDOS.stream().anyMatch(tipo::isCompatibleWith));
                if (!aceitaJson) {
                    throw new HttpMediaTypeNotAcceptableException(PRODUZIDOS);
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
