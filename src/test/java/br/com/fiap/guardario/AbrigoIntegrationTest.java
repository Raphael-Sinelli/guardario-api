package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AbrigoIntegrationTest extends IntegracaoBase {

    @Test
    void ocupacaoMaiorQueCapacidadeDevolve400() throws Exception {
        long area = criarArea("Área Abrigo Inválido " + sufixoUnico());
        postJson("/api/abrigos", tokenAgente(), """
                {"nome": "Ginásio", "endereco": "Rua 1", "capacidade": 10, "ocupacao": 11,
                 "aceitaPets": true, "acessivelPcd": true, "areaId": %d}""".formatted(area))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("capacidade")));
    }

    // PUT sem ocupacao não pode sumir com as pessoas que já estão no abrigo
    @Test
    void atualizarSemOcupacaoMantemAsPessoasAcolhidas() throws Exception {
        long area = criarArea("Área Abrigo PUT " + sufixoUnico());
        long abrigo = criarAbrigo(area, 10, 4, true, true);
        String corpoSemOcupacao = """
                {"nome": "Abrigo Renomeado", "endereco": "Rua 2", "capacidade": %d,
                 "aceitaPets": true, "acessivelPcd": true, "areaId": %d}""";

        putJson("/api/abrigos/" + abrigo, corpoSemOcupacao.formatted(12, area))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocupacao").value(4))
                .andExpect(jsonPath("$.vagas").value(8));

        // capacidade abaixo de quem já está lá dentro
        putJson("/api/abrigos/" + abrigo, corpoSemOcupacao.formatted(3, area))
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente()))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // durante a cheia vários agentes registram entradas ao mesmo tempo: nenhuma entrada confirmada pode se perder
    @Test
    void entradasSimultaneasNaoConfirmamPessoaQueNaoFoiRegistrada() throws Exception {
        long abrigo = criarAbrigo(criarArea("Área Concorrência " + sufixoUnico()), 10, 0, true, true);
        String agente = tokenAgente();
        ExecutorService agentes = Executors.newFixedThreadPool(10);
        List<Future<Integer>> respostas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            respostas.add(agentes.submit(() -> postJson("/api/abrigos/" + abrigo + "/entradas", agente, "{\"pessoas\": 1}")
                    .andReturn().getResponse().getStatus()));
        }
        agentes.shutdown();

        int confirmadas = 0;
        for (Future<Integer> resposta : respostas) {
            int codigo = resposta.get(60, TimeUnit.SECONDS);
            assertThat(codigo).isIn(200, 409);
            if (codigo == 200) {
                confirmadas++;
            }
        }
        String json = mockMvc.perform(get("/api/abrigos/" + abrigo)).andReturn().getResponse().getContentAsString();
        int ocupacao = JsonPath.read(json, "$.ocupacao");
        assertThat(confirmadas).isEqualTo(ocupacao);
    }

    @Test
    void entradaAteLotarESaidaReabre() throws Exception {
        long abrigo = criarAbrigo(criarArea("Área Lotação " + sufixoUnico()), 10, 0, true, true);
        String agente = tokenAgente();

        postJson("/api/abrigos/" + abrigo + "/entradas", agente, "{\"pessoas\": 10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOTADO"))
                .andExpect(jsonPath("$.vagas").value(0))
                .andExpect(jsonPath("$._links['registrar-entrada']").doesNotExist())
                .andExpect(jsonPath("$._links['registrar-saida'].href").exists());

        postJson("/api/abrigos/" + abrigo + "/entradas", agente, "{\"pessoas\": 1}")
                .andExpect(status().isConflict());

        postJson("/api/abrigos/" + abrigo + "/saidas", agente, "{\"pessoas\": 3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.ocupacao").value(7));

        postJson("/api/abrigos/" + abrigo + "/saidas", agente, "{\"pessoas\": 8}")
                .andExpect(status().isConflict());
    }

    @Test
    void abrigoQueLotaSaiDaListaDeDisponiveisEmCache() throws Exception {
        long abrigo = criarAbrigo(criarArea("Área Cache Abrigo " + sufixoUnico()), 5, 0, true, true);

        mockMvc.perform(get("/api/abrigos/disponiveis"))
                .andExpect(jsonPath("$._embedded.abrigos[*].id", hasItem((int) abrigo)));

        // a entrada limpa o cache (@CacheEvict), então a próxima consulta já não mostra o abrigo
        postJson("/api/abrigos/" + abrigo + "/entradas", tokenAgente(), "{\"pessoas\": 5}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/abrigos/disponiveis"))
                .andExpect(jsonPath("$._embedded.abrigos[*].id", not(hasItem((int) abrigo))));
    }

    @Test
    void abrigoFechadoNaoRecebeELotadoNaoEhDefinidoPeloCliente() throws Exception {
        long abrigo = criarAbrigo(criarArea("Área Fechado " + sufixoUnico()), 20, 0, false, true);
        String agente = tokenAgente();

        mockMvc.perform(patch("/api/abrigos/" + abrigo + "/status").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"FECHADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$._links.reabrir.href").exists());

        postJson("/api/abrigos/" + abrigo + "/entradas", agente, "{\"pessoas\": 1}")
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/abrigos/" + abrigo + "/status").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"LOTADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtrosOpcionaisComSpecification() throws Exception {
        long area = criarArea("Área Filtros " + sufixoUnico());
        long petsEPcd = criarAbrigo(area, 50, 10, true, true);
        long soPets = criarAbrigo(area, 50, 10, true, false);
        long petsLotado = criarAbrigo(area, 50, 50, true, true);
        long semPets = criarAbrigo(area, 50, 0, false, true);

        mockMvc.perform(get("/api/abrigos").param("areaId", String.valueOf(area))
                        .param("aceitaPets", "true").param("comVagas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.abrigos[*].id", containsInAnyOrder((int) petsEPcd, (int) soPets)));

        mockMvc.perform(get("/api/abrigos").param("areaId", String.valueOf(area))
                        .param("aceitaPets", "true").param("acessivelPcd", "true").param("comVagas", "true"))
                .andExpect(jsonPath("$._embedded.abrigos[*].id", contains((int) petsEPcd)));

        mockMvc.perform(get("/api/abrigos").param("areaId", String.valueOf(area)).param("status", "LOTADO"))
                .andExpect(jsonPath("$._embedded.abrigos[*].id", contains((int) petsLotado)));

        mockMvc.perform(get("/api/abrigos").param("areaId", String.valueOf(area)))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$._embedded.abrigos[*].id", hasItem((int) semPets)));
    }

    @Test
    void cidadaoNaoGerenciaAbrigo() throws Exception {
        long area = criarArea("Área Abrigo Permissão " + sufixoUnico());
        postJson("/api/abrigos", tokenCidadao(), """
                {"nome": "Abrigo", "endereco": "Rua 1", "capacidade": 10,
                 "aceitaPets": true, "acessivelPcd": true, "areaId": %d}""".formatted(area))
                .andExpect(status().isForbidden());
    }
}
