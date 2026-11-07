package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limites que o uso normal não atinge: corpo vazio, campo obrigatório nulo, paginação nos extremos.
 */
class CasosExtremosIntegrationTest extends IntegracaoBase {

    // sem corpo nenhum (ou com "null"), a mensagem diz que o corpo falta, e não que o JSON está mal formado
    @Test
    void corpoAusenteDizQueOCorpoEhObrigatorio() throws Exception {
        for (String corpo : new String[]{"", "null"}) {
            mockMvc.perform(post("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao()))
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("corpo da requisição é obrigatório")));
        }
    }

    @Test
    void campoObrigatorioNuloApontaOCampo() throws Exception {
        String agente = tokenAgente();
        long relato = criarRelato(tokenCidadao(), criarArea("Área Nulos " + sufixoUnico()), "OUTRO");

        mockMvc.perform(patch("/api/relatos/" + relato + "/status").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        postJson("/api/abrigos/1/entradas", agente, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pessoas"));
    }

    @Test
    void paginacaoNosExtremos() throws Exception {
        // size acima do teto vira 100; size zero ou negativo e page negativa voltam ao padrão
        mockMvc.perform(get("/api/areas").param("size", "1000")).andExpect(jsonPath("$.page.size").value(100));
        mockMvc.perform(get("/api/areas").param("size", "0")).andExpect(jsonPath("$.page.size").value(10));
        mockMvc.perform(get("/api/areas").param("page", "-1")).andExpect(jsonPath("$.page.number").value(0));
        // página depois da última: 200, lista vazia (sem _embedded) e a paginação dizendo onde está
        mockMvc.perform(get("/api/areas").param("page", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(99999));
        // texto no lugar do número da página volta ao padrão, como no Spring Data
        mockMvc.perform(get("/api/areas").param("page", "abc")).andExpect(status().isOk());
    }
}
