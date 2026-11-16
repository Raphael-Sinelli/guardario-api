package br.com.fiap.guardario.configurations;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.module.SimpleModule;
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

    // O PostgreSQL não guarda o caractere nulo (U+0000) em texto e recusa o INSERT inteiro; o H2 grava. Recusado já
    // na leitura do JSON, ele vira 400 no campo nos dois bancos, e não erro do banco
    @Bean
    public JsonMapperBuilderCustomizer textoSemCaractereNulo() {
        SimpleModule modulo = new SimpleModule("texto-sem-caractere-nulo");
        modulo.addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser leitor, DeserializationContext contexto) {
                String texto = super.deserialize(leitor, contexto);
                if (texto != null && texto.indexOf(0) >= 0) { // o código 0 é o caractere nulo
                    throw new TextoComCaractereNulo(leitor, texto);
                }
                return texto;
            }
        });
        return builder -> builder.addModule(modulo);
    }

    // o GlobalExceptionHandler reconhece este erro e explica o motivo no campo
    public static class TextoComCaractereNulo extends InvalidFormatException {

        TextoComCaractereNulo(JsonParser leitor, String texto) {
            super(leitor, "texto com o caractere nulo (U+0000)", texto, String.class);
        }
    }
}
