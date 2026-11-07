package br.com.fiap.guardario.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

/**
 * A Bean Validation escreve as mensagens no idioma da requisição: com o padrão do Spring, o Accept-Language do
 * navegador ou o locale da JVM (uma VM Linux costuma vir em inglês) trocavam "não deve estar em branco" por
 * "must not be blank", e o mesmo erro saía metade em português e metade em inglês. A API responde sempre em pt-BR.
 */
@Configuration
public class IdiomaConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.forLanguageTag("pt-BR"));
    }
}
