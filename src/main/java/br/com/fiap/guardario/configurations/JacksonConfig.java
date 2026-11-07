package br.com.fiap.guardario.configurations;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * O allow-coercion-of-scalars do application.yml barra texto em campo numérico e número em campo booleano, mas não
 * cobre o caminho contrário: "nome": 12345 e "endereco": true eram aceitos e gravados como "12345" e "true".
 * Aqui o campo de texto também passa a exigir o valor entre aspas; o erro sai como erro de campo, pelo
 * GlobalExceptionHandler, igual aos outros tipos.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer textoSoEntreAspas() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, regra -> regra
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
