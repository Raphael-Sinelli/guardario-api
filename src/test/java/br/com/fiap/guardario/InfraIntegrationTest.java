package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cache, Actuator, Swagger, CORS e papéis vindos do banco.
 */
class InfraIntegrationTest extends IntegracaoBase {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void alertasAtivosFicamEmCacheAteUmNovoAlertaLimpar() throws Exception {
        mockMvc.perform(get("/api/alertas/ativos")).andExpect(status().isOk());
        assertThat(cacheManager.getCache("alertasAtivos").get(SimpleKey.EMPTY)).isNotNull();

        long estacao = criarEstacao(criarArea("Área Cache Alerta " + sufixoUnico()));
        registrarLeitura(estacao, 250, 0.0).andExpect(jsonPath("$.alertaGeradoId").isNumber());

        assertThat(cacheManager.getCache("alertasAtivos").get(SimpleKey.EMPTY)).isNull();
    }

    // o cache de alertas ativos guarda o nome da área: renomear a área precisa limpar esse cache também
    @Test
    void renomearAreaAtualizaONomeNosAlertasAtivosEmCache() throws Exception {
        long area = criarArea("Área Antiga " + sufixoUnico());
        registrarLeitura(criarEstacao(area), 250, 0.0).andExpect(jsonPath("$.alertaGeradoId").isNumber());
        mockMvc.perform(get("/api/alertas/ativos")).andExpect(status().isOk()); // enche o cache

        String nomeNovo = "Área Renomeada " + sufixoUnico();
        mockMvc.perform(put("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"%s\", \"cidade\": \"São Paulo\", \"uf\": \"SP\"}".formatted(nomeNovo)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/alertas/ativos"))
                .andExpect(jsonPath("$._embedded.alertas[?(@.areaId == %d)].areaNome".formatted(area)).value(nomeNovo));
    }

    // o @CacheEvict de dentro de uma transação só vale no commit: sem isso, uma leitura concorrente entre o evict e o
    // commit consultaria o banco ainda sem a mudança e guardaria o dado velho no cache por até 10 minutos
    @Test
    void cacheSoEhLimpoDepoisDoCommitENaoEhLimpoNoRollback() {
        Cache cache = cacheManager.getCache("areas");
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        cache.put("teste-commit", "valor");
        transacao.executeWithoutResult(status -> {
            cache.evict("teste-commit");
            assertThat(cache.get("teste-commit")).isNotNull(); // ainda não houve commit
        });
        assertThat(cache.get("teste-commit")).isNull();

        cache.put("teste-rollback", "valor");
        transacao.executeWithoutResult(status -> {
            cache.evict("teste-rollback");
            status.setRollbackOnly();
        });
        assertThat(cache.get("teste-rollback")).isNotNull(); // o banco não mudou, o cache continua certo
    }

    @Test
    void cacheDaAreaSoEhLimpoQuandoONivelMuda() throws Exception {
        long area = criarArea("Área Cache Nível " + sufixoUnico());
        long estacao = criarEstacao(area);

        mockMvc.perform(get("/api/areas/" + area)).andExpect(jsonPath("$.nivelAtual").value("NORMAL"));
        assertThat(cacheManager.getCache("areas").get(area)).isNotNull();

        registrarLeitura(estacao, 50, 0.0); // continua NORMAL: o cache fica
        assertThat(cacheManager.getCache("areas").get(area)).isNotNull();

        registrarLeitura(estacao, 120, 0.0); // vira ATENCAO: o cache da área sai
        assertThat(cacheManager.getCache("areas").get(area)).isNull();
        mockMvc.perform(get("/api/areas/" + area)).andExpect(jsonPath("$.nivelAtual").value("ATENCAO"));
    }

    @Test
    void acertosDoCacheAparecemNasMetricasDoActuator() throws Exception {
        mockMvc.perform(get("/api/abrigos/disponiveis")).andExpect(status().isOk());
        mockMvc.perform(get("/api/abrigos/disponiveis")).andExpect(status().isOk()); // essa vem do cache

        mockMvc.perform(get("/actuator/metrics/cache.gets")
                        .param("tag", "cache:abrigosDisponiveis")
                        .param("tag", "result:hit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
    }

    // a documentação dos parâmetros de lista fica em português e o sort mostra os campos aceitos daquela rota
    @Test
    void swaggerDocumentaPaginacaoEmPortuguesEO409DoAbrigo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/areas'].get.parameters[?(@.name == 'page')].description",
                        hasItem(containsString("começando em 0"))))
                .andExpect(jsonPath("$.paths['/api/areas'].get.parameters[?(@.name == 'sort')].description",
                        hasItem(containsString("populacaoEstimada"))))
                .andExpect(jsonPath("$.paths['/api/abrigos/{id}'].put.responses['409'].description").exists());
    }

    // o Swagger mostra o Content-Type que a API manda de verdade: hal+json nos recursos com links, json no resto
    @Test
    void swaggerDocumentaOTipoDeConteudoRealDasRespostas() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"*/*\"");
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/areas/{id}'].get.responses['200'].content['application/hal+json']").exists())
                .andExpect(jsonPath("$.paths['/api/areas'].get.responses['200'].content['application/hal+json']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200'].content['application/json']").exists());
        mockMvc.perform(get("/api/areas/1"))
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"));
    }

    // o app segue o href como está: link com template ({?page,size} ou {&aceitaPets}) levaria a uma URL inválida
    @Test
    void nenhumLinkHateoasSaiComTemplate() throws Exception {
        String admin = tokenAdmin();
        List<String> rotas = List.of("/api/areas", "/api/areas/1", "/api/areas/ranking", "/api/estacoes", "/api/estacoes/1",
                "/api/estacoes/criticas", "/api/estacoes/1/leituras", "/api/alertas", "/api/alertas/ativos", "/api/alertas/1",
                "/api/abrigos", "/api/abrigos/1", "/api/abrigos/disponiveis", "/api/relatos", "/api/relatos/1",
                "/api/relatos/meus", "/api/usuarios", "/api/usuarios/1");
        List<String> comTemplate = new ArrayList<>();
        for (String rota : rotas) {
            String corpo = mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (corpo.contains("\"templated\":true")) {
                comTemplate.add(rota);
            }
        }
        assertThat(comTemplate).isEmpty();

        // seguir o link como está funciona
        String area = mockMvc.perform(get("/api/areas/1")).andReturn().getResponse().getContentAsString();
        String abrigosComVaga = JsonPath.read(area, "$._links['abrigos-com-vaga'].href");
        assertThat(abrigosComVaga).endsWith("/api/abrigos?areaId=1&comVagas=true");
        mockMvc.perform(get(abrigosComVaga)).andExpect(status().isOk());
    }

    // o HEAD (o GET sem corpo, usado para checar se a rota responde) segue a regra do GET: público onde o GET é público
    @Test
    void headSegueARegraDoGet() throws Exception {
        mockMvc.perform(head("/api/areas")).andExpect(status().isOk());
        mockMvc.perform(head("/api/abrigos/disponiveis")).andExpect(status().isOk());
        mockMvc.perform(head("/api/relatos")).andExpect(status().isUnauthorized());
        mockMvc.perform(head("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isForbidden());
        mockMvc.perform(head("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())))
                .andExpect(status().isOk());
        mockMvc.perform(head("/api/usuarios")).andExpect(status().isUnauthorized());
        mockMvc.perform(head("/api/areas").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void healthEInfoSaoPublicosEMetricsSoParaAdmin() throws Exception {
        // público vê só o UP; o detalhe (banco, disco com o caminho da pasta) fica para o ADMIN
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        // cada componente também é detalhe: sem token, o 401 padrão; com o ADMIN, o componente
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/actuator/health/db").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // componente que não existe: o Actuator responde 404 com o corpo vazio; sai o JSON de erro da API
        mockMvc.perform(get("/actuator/health/xyz").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/actuator/health/xyz"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("GuardaRio API"))
                .andExpect(jsonPath("$.os").doesNotExist());

        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/guardario.alertas.emitidos").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/caches").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheManagers.cacheManager.caches", hasKey("alertasAtivos")));
    }

    @Test
    void documentacaoOpenApiDisponivel() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("GuardaRio API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths", hasKey("/api/relatos/{id}/status")))
                .andExpect(jsonPath("$.paths['/api/areas/{id}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorCustomResponse"))
                .andExpect(jsonPath("$.components.schemas", hasKey("ValidationErrorResponse")));
    }

    // quem declara @ApiResponse perde os códigos automáticos do springdoc, então o 401 e o 400 precisam ser conferidos
    @Test
    @SuppressWarnings("unchecked")
    void swaggerDescreveTodaRotaComO401DasProtegidasEO400DasComParametro() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Map<String, Object>>> rotas = JsonPath.read(json, "$.paths");
        List<String> semO401 = new ArrayList<>();
        List<String> semO400 = new ArrayList<>();
        List<String> semDescricao = new ArrayList<>();

        rotas.forEach((rota, operacoes) -> operacoes.forEach((metodo, operacao) -> {
            if (operacao.get("summary") == null || operacao.get("description") == null) {
                semDescricao.add(metodo.toUpperCase() + " " + rota);
            }
            Map<String, Object> respostas = (Map<String, Object>) operacao.get("responses");
            boolean publica = operacao.get("security") instanceof List<?> seguranca && seguranca.isEmpty();
            if (!publica && !respostas.containsKey("401")) {
                semO401.add(metodo.toUpperCase() + " " + rota);
            }
            if (operacao.containsKey("parameters") && !respostas.containsKey("400")) {
                semO400.add(metodo.toUpperCase() + " " + rota);
            }
        }));

        assertThat(semDescricao).isEmpty();
        assertThat(semO401).isEmpty();
        assertThat(semO400).isEmpty();
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/alertas/ativos'].get.responses", not(hasKey("401"))))
                .andExpect(jsonPath("$.paths['/api/alertas/{id}/encerrar'].patch.responses['401'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorCustomResponse"));
    }

    @Test
    void corsLiberaOAppEBloqueiaOrigemDesconhecida() throws Exception {
        mockMvc.perform(options("/api/relatos")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location"));

        mockMvc.perform(options("/api/relatos")
                        .header(HttpHeaders.ORIGIN, "http://site-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("site-malicioso.com")));

        // requisição comum (não preflight) de origem recusada também recebe o JSON padrão
        mockMvc.perform(get("/api/areas").header(HttpHeaders.ORIGIN, "http://site-malicioso.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void papelVemDoBancoAPromocaoValeSemNovoLogin() throws Exception {
        String email = "futuro.agente." + sufixoUnico().toLowerCase() + "@email.com";
        long usuario = id(postJson("/api/auth/register", null, """
                {"nome": "Futuro Agente", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isCreated()));
        String tokenAntigo = token(email, "Senha@123");
        long area = criarArea("Área Promoção " + sufixoUnico());
        String abrigo = """
                {"nome": "Abrigo", "endereco": "Rua 1", "capacidade": 10,
                 "aceitaPets": true, "acessivelPcd": true, "areaId": %d}""".formatted(area);

        postJson("/api/abrigos", tokenAntigo, abrigo).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/usuarios/" + usuario + "/papel").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"papel\": \"AGENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("AGENTE"));

        // o filtro JWT carrega o usuário do banco a cada requisição
        postJson("/api/abrigos", tokenAntigo, abrigo).andExpect(status().isCreated());
    }

    @Test
    void adminNaoMudaOProprioPapelECidadaoNaoListaUsuarios() throws Exception {
        String admin = tokenAdmin();
        long adminId = id(mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(admin))));
        mockMvc.perform(put("/api/usuarios/" + adminId + "/papel").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"papel\": \"CIDADAO\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isForbidden());

        // o link de ação só aparece onde a ação é possível: no próprio registro do ADMIN ela daria 409
        mockMvc.perform(get("/api/usuarios/" + adminId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$._links['alterar-papel']").doesNotExist());
        long cidadaoId = id(mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao()))));
        mockMvc.perform(get("/api/usuarios/" + cidadaoId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$._links['alterar-papel'].href").exists());
        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.usuarios[0].senha").doesNotExist());
    }
}
