package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import br.com.fiap.guardario.dto.ValidationErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/*
 * Todas as rotas ganham o cadeado do bearerAuth; as públicas tiram o cadeado com @SecurityRequirements vazio.
 * No botão Authorize do Swagger UI cole só o token, sem a palavra Bearer.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GuardaRio API",
                version = "1.0.0",
                description = """
                        API do GuardaRio, alerta comunitário de enchentes (Global Solution 2026/2, FIAP).
                        Estações IoT medem o nível do rio, a API classifica o risco, gera alertas automáticos,
                        mostra abrigos com vaga e recebe relatos dos moradores com prioridade calculada.

                        Usuários de teste (perfil dev): admin@guardario.com / Admin@123,
                        agente@guardario.com / Agente@123 e cidadao@guardario.com / Cidadao@123.
                        """,
                contact = @Contact(name = "Equipe GuardaRio")),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token devolvido por POST /api/auth/login (vale 1 hora)")
public class OpenApiConfig {

    private static final String SCHEMAS = "#/components/schemas/";

    /*
     * O springdoc não sabe o formato do corpo de erro que o GlobalExceptionHandler devolve: sozinho, ele repete
     * nas respostas 4xx o tipo de sucesso do método. Este customizer troca o corpo de toda resposta 4xx e 5xx
     * declarada nos controllers pelo schema certo: ErrorCustomResponse, ou, no 400,
     * ValidationErrorResponse (Bean Validation) ou ErrorCustomResponse (JSON mal formado).
     */
    @Bean
    public OpenApiCustomizer respostasDeErroPadronizadas() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            registrarSchema(openApi.getComponents(), ErrorCustomResponse.class);
            registrarSchema(openApi.getComponents(), ValidationErrorResponse.class);

            Schema<?> erroPadrao = new Schema<>().$ref(SCHEMAS + "ErrorCustomResponse");
            Schema<?> erroDe400 = new Schema<>().oneOf(List.of(
                    new Schema<>().$ref(SCHEMAS + "ValidationErrorResponse"),
                    new Schema<>().$ref(SCHEMAS + "ErrorCustomResponse")));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operacao -> {
                if (operacao.getResponses() == null) {
                    return;
                }
                operacao.getResponses().forEach((codigo, resposta) -> {
                    boolean ehErro = codigo.startsWith("4") || codigo.startsWith("5");
                    if (ehErro) {
                        Schema<?> schema = "400".equals(codigo) ? erroDe400 : erroPadrao;
                        resposta.setContent(new Content().addMediaType("application/json", new MediaType().schema(schema)));
                    }
                });
            }));
        };
    }

    private void registrarSchema(Components components, Class<?> tipo) {
        ModelConverters.getInstance(true).readAll(tipo).forEach(components::addSchemas);
    }
}
