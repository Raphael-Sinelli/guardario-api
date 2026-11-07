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
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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
                completarRespostasDeErro(operacao);
                operacao.getResponses().forEach((codigo, resposta) -> {
                    boolean ehErro = codigo.startsWith("4") || codigo.startsWith("5");
                    if (ehErro) {
                        Schema<?> schema = "400".equals(codigo) ? erroDe400 : erroPadrao;
                        resposta.setContent(new Content().addMediaType("application/json", new MediaType().schema(schema)));
                    } else {
                        trocarTipoGenerico(resposta.getContent());
                    }
                });
            }));
        };
    }

    /*
     * Quem declara @ApiResponse perde as respostas automáticas do springdoc, e ele não deduz o 401 da segurança.
     * Rota protegida (sem @SecurityRequirements vazio) ganha o 401; rota com parâmetro ganha o 400 (id que não é
     * número positivo, valor fora do enum, campo de ordenação inexistente). No fim, os códigos ficam em ordem.
     */
    private void completarRespostasDeErro(Operation operacao) {
        ApiResponses respostas = operacao.getResponses();
        boolean protegida = operacao.getSecurity() == null || !operacao.getSecurity().isEmpty();
        if (protegida && !respostas.containsKey("401")) {
            respostas.addApiResponse("401", new ApiResponse().description("Sem token, ou token inválido ou expirado"));
        }
        boolean temParametro = operacao.getParameters() != null && !operacao.getParameters().isEmpty();
        if (temParametro && !respostas.containsKey("400")) {
            respostas.addApiResponse("400", new ApiResponse().description("Parâmetro inválido"));
        }

        ApiResponses emOrdem = new ApiResponses();
        respostas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entrada -> emOrdem.addApiResponse(entrada.getKey(), entrada.getValue()));
        operacao.setResponses(emOrdem);
    }

    /*
     * O springdoc descreve page, size e sort com o texto do Spring Data, em inglês, e não sabe por quais campos cada
     * rota deixa ordenar. Este customizer põe a descrição em português e lista no sort os campos do @CamposDeOrdenacao.
     */
    @Bean
    public OperationCustomizer parametrosDeListaEmPortugues() {
        return (operacao, metodo) -> {
            if (operacao.getParameters() == null) {
                return operacao;
            }
            CamposDeOrdenacao campos = metodo.getMethodAnnotation(CamposDeOrdenacao.class);
            for (Parameter parametro : operacao.getParameters()) {
                if (!"query".equals(parametro.getIn())) {
                    continue;
                }
                switch (parametro.getName()) {
                    case "page" -> parametro.setDescription("Número da página, começando em 0");
                    case "size" -> parametro.setDescription("Itens por página, no máximo 100");
                    case "sort" -> parametro.setDescription(campos == null
                            ? "Esta rota não aceita ordenação"
                            : "Ordenação no formato campo,asc ou campo,desc (pode repetir o parâmetro). Campos aceitos: "
                            + String.join(", ", campos.value()));
                    default -> {
                    }
                }
            }
            return operacao;
        };
    }

    // Sem produces nos mapeamentos, o springdoc documenta o sucesso com o tipo genérico (asterisco/asterisco). A API manda
    // application/hal+json nos recursos com links (EntityModel, PagedModel, CollectionModel) e application/json no resto.
    private void trocarTipoGenerico(Content conteudo) {
        if (conteudo == null || !conteudo.containsKey("*/*")) {
            return;
        }
        MediaType corpo = conteudo.remove("*/*");
        String referencia = corpo.getSchema() == null || corpo.getSchema().get$ref() == null ? "" : corpo.getSchema().get$ref();
        String nome = referencia.substring(referencia.lastIndexOf('/') + 1);
        boolean temLinks = nome.startsWith("EntityModel") || nome.startsWith("PagedModel") || nome.startsWith("CollectionModel");
        conteudo.addMediaType(temLinks ? "application/hal+json" : "application/json", corpo);
    }

    private void registrarSchema(Components components, Class<?> tipo) {
        ModelConverters.getInstance(true).readAll(tipo).forEach(components::addSchemas);
    }
}
