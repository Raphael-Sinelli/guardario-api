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
        // página depois da última: 200, lista vazia (o _embedded vem, com a lista vazia), a paginação dizendo onde
        // está e só links para páginas que existem: sem prev apontando para outra página vazia
        mockMvc.perform(get("/api/areas").param("page", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.areas").isEmpty())
                .andExpect(jsonPath("$.page.number").value(99999))
                .andExpect(jsonPath("$._links.prev").doesNotExist())
                .andExpect(jsonPath("$._links.next").doesNotExist())
                .andExpect(jsonPath("$._links.first.href").exists())
                .andExpect(jsonPath("$._links.last.href").exists());
        // filtro que não acha nada: a mesma estrutura, com a lista vazia, e o app lê sempre o mesmo formato
        mockMvc.perform(get("/api/abrigos").param("areaId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.abrigos").isArray())
                .andExpect(jsonPath("$._embedded.abrigos").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        // texto no lugar do número da página volta ao padrão, como no Spring Data
        mockMvc.perform(get("/api/areas").param("page", "abc")).andExpect(status().isOk());
    }
    // o banco guarda microssegundos: a data que o POST devolve precisa ser a mesma que a busca seguinte mostra
    @Test
    void dataDevolvidaNaEscritaEhAMesmaQueFicaGravada() throws Exception {
        String cidadao = tokenCidadao();
        String relato = postJson("/api/relatos", cidadao, """
                {"tipo": "OUTRO", "descricao": "Relato para conferir a data gravada", "areaId": 1}""")
                .andReturn().getResponse().getContentAsString();
        String relatoGravado = mockMvc.perform(get("/api/relatos/" + id(relato, "$.id"))
                .header(HttpHeaders.AUTHORIZATION, bearer(cidadao))).andReturn().getResponse().getContentAsString();
        assertMesmaData(relato, relatoGravado, "$.criadoEm");
        assertMesmaData(relato, relatoGravado, "$.atualizadoEm");

        String agente = tokenAgente();
        String alerta = postJson("/api/alertas", agente, """
                {"areaId": %d, "nivel": "ATENCAO", "titulo": "Alerta da data", "mensagem": "Conferir a data gravada"}"""
                .formatted(criarArea("Área Data Gravada " + sufixoUnico()))).andReturn().getResponse().getContentAsString();
        String alertaGravado = mockMvc.perform(get("/api/alertas/" + id(alerta, "$.id"))).andReturn().getResponse().getContentAsString();
        assertMesmaData(alerta, alertaGravado, "$.emitidoEm");
        String encerrado = mockMvc.perform(patch("/api/alertas/" + id(alerta, "$.id") + "/encerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(agente))).andReturn().getResponse().getContentAsString();
        String encerradoGravado = mockMvc.perform(get("/api/alertas/" + id(alerta, "$.id"))).andReturn().getResponse().getContentAsString();
        assertMesmaData(encerrado, encerradoGravado, "$.encerradoEm");

        String email = "data" + sufixoUnico().toLowerCase() + "@email.com";
        String cadastro = postJson("/api/auth/register", null, """
                {"nome": "Pessoa da Data", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andReturn().getResponse().getContentAsString();
        String cadastroGravado = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token(email, "Senha@123"))))
                .andReturn().getResponse().getContentAsString();
        assertMesmaData(cadastro, cadastroGravado, "$.criadoEm");
    }

    private static void assertMesmaData(String resposta, String gravado, String campo) {
        String devolvida = com.jayway.jsonpath.JsonPath.read(resposta, campo);
        String lida = com.jayway.jsonpath.JsonPath.read(gravado, campo);
        org.assertj.core.api.Assertions.assertThat(devolvida).as(campo).isEqualTo(lida).matches(".*(:\\d{2}|\\.\\d{1,6})Z");
    }
}
