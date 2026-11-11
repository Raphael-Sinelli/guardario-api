package br.com.fiap.guardario;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toda rota protegida, com o papel mínimo de cada uma: sem token dá 401, papel abaixo do mínimo dá 403.
 * O corpo vai vazio ({}) de propósito: a negação precisa vir antes da validação, senão quem não tem permissão
 * recebe 400 e descobre as regras do corpo de uma rota que não pode usar.
 */
class MatrizDeAutorizacaoIntegrationTest extends IntegracaoBase {

    @ParameterizedTest(name = "{0} {1} exige {2}")
    @CsvSource({
            // qualquer usuário logado
            "GET,    /api/auth/me,                    LOGADO",
            "POST,   /api/relatos,                    LOGADO",
            "GET,    /api/relatos/meus,               LOGADO",
            "GET,    /api/relatos/1,                  LOGADO",
            "PUT,    /api/relatos/1,                  LOGADO",
            "DELETE, /api/relatos/1,                  LOGADO",
            "GET,    /api/usuarios/1,                 LOGADO",
            // AGENTE ou ADMIN
            "POST,   /api/estacoes/1/leituras,        AGENTE",
            "POST,   /api/alertas,                    AGENTE",
            "PUT,    /api/alertas/1,                  AGENTE",
            "PATCH,  /api/alertas/1/encerrar,         AGENTE",
            "POST,   /api/abrigos,                    AGENTE",
            "PUT,    /api/abrigos/1,                  AGENTE",
            "PATCH,  /api/abrigos/1/status,           AGENTE",
            "POST,   /api/abrigos/1/entradas,         AGENTE",
            "POST,   /api/abrigos/1/saidas,           AGENTE",
            "GET,    /api/relatos,                    AGENTE",
            "PATCH,  /api/relatos/1/status,           AGENTE",
            // só ADMIN
            "POST,   /api/areas,                      ADMIN",
            "PUT,    /api/areas/1,                    ADMIN",
            "DELETE, /api/areas/1,                    ADMIN",
            "POST,   /api/estacoes,                   ADMIN",
            "PUT,    /api/estacoes/1,                 ADMIN",
            "DELETE, /api/estacoes/1,                 ADMIN",
            "DELETE, /api/alertas/1,                  ADMIN",
            "DELETE, /api/abrigos/1,                  ADMIN",
            "GET,    /api/usuarios,                   ADMIN",
            "PUT,    /api/usuarios/1/papel,           ADMIN",
            "GET,    /actuator/metrics,               ADMIN",
            "GET,    /actuator/health/db,             ADMIN",
            "GET,    /actuator/caches,                ADMIN"
    })
    void rotaProtegidaNegaQuemNaoTemOPapelAntesDeValidarOCorpo(String metodo, String rota, String papelMinimo) throws Exception {
        mockMvc.perform(requisicao(metodo, rota, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        if (!papelMinimo.equals("LOGADO")) {
            mockMvc.perform(requisicao(metodo, rota, tokenCidadao()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        if (papelMinimo.equals("ADMIN")) {
            mockMvc.perform(requisicao(metodo, rota, tokenAgente()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    // o outro lado da matriz: informação de segurança (nível do rio, alertas e abrigos) responde sem login
    @ParameterizedTest(name = "{0} sem token")
    @ValueSource(strings = {
            "/api/areas", "/api/areas/1", "/api/areas/ranking",
            "/api/estacoes", "/api/estacoes/1", "/api/estacoes/criticas",
            "/api/estacoes/1/leituras", "/api/estacoes/1/leituras/estatisticas",
            "/api/alertas", "/api/alertas/ativos",
            "/api/abrigos", "/api/abrigos/1", "/api/abrigos/disponiveis",
            "/actuator/health", "/actuator/info", "/v3/api-docs", "/v3/api-docs.yaml"
    })
    void rotaPublicaRespondeSemToken(String rota) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder requisicao(String metodo, String rota, String token) {
        MockHttpServletRequestBuilder requisicao = request(HttpMethod.valueOf(metodo), rota);
        if (!metodo.equals("GET") && !metodo.equals("DELETE")) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (token != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao;
    }
}
