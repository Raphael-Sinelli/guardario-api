package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cache, Actuator, Swagger, CORS e papéis vindos do banco.
 */
class InfraIntegrationTest extends IntegracaoBase {

    @Autowired
    private CacheManager cacheManager;

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

    @Test
    void healthEInfoSaoPublicosEMetricsSoParaAdmin() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("GuardaRio API"));

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
                .andExpect(status().isForbidden());
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
        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.usuarios[0].senha").doesNotExist());
    }
}
