package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RelatoIntegrationTest extends IntegracaoBase {

    @Test
    void prioridadeEhCalculadaPeloServidor() throws Exception {
        long area = criarArea("Área Prioridade " + sufixoUnico());
        String cidadao = tokenCidadao();

        long bueiroNormal = criarRelato(cidadao, area, "BUEIRO_ENTUPIDO");
        mockMvc.perform(get("/api/relatos/" + bueiroNormal).header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(jsonPath("$.prioridade").value(2))
                .andExpect(jsonPath("$.status").value("ABERTO"));

        // a área entra em ALERTA: o mesmo tipo de relato ganha +1
        registrarLeitura(criarEstacao(area), 210, 0.0).andExpect(status().isCreated());
        long bueiroEmAlerta = criarRelato(cidadao, area, "BUEIRO_ENTUPIDO");
        long ilhada = criarRelato(cidadao, area, "PESSOA_ILHADA");

        String agente = tokenAgente();
        mockMvc.perform(get("/api/relatos/" + bueiroEmAlerta).header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(jsonPath("$.prioridade").value(3));
        mockMvc.perform(get("/api/relatos/" + ilhada).header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(jsonPath("$.prioridade").value(5)); // teto
    }

    // o mínimo de 10 caracteres vale para o texto, não para os espaços em volta
    @Test
    void descricaoSoComEspacosEmVoltaNaoPassaDoMinimo() throws Exception {
        long area = criarArea("Área Espaços " + sufixoUnico());
        postJson("/api/relatos", tokenCidadao(), """
                {"tipo": "OUTRO", "descricao": "                    x", "areaId": %d}""".formatted(area))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("descricao"));
    }

    @Test
    void maquinaDeEstadosComLinksQueAcompanham() throws Exception {
        long relato = criarRelato(tokenCidadao(), criarArea("Área Status " + sufixoUnico()), "RIO_SUBINDO");
        String agente = tokenAgente();
        String url = "/api/relatos/" + relato + "/status";

        mockMvc.perform(get("/api/relatos/" + relato).header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(jsonPath("$._links.atender.href").exists())
                .andExpect(jsonPath("$._links.descartar.href").exists())
                .andExpect(jsonPath("$._links.resolver").doesNotExist());

        mudarStatus(url, agente, "RESOLVIDO").andExpect(status().isConflict());

        mudarStatus(url, agente, "EM_ATENDIMENTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$._links.resolver.href").exists())
                .andExpect(jsonPath("$._links.atender").doesNotExist());

        mudarStatus(url, agente, "RESOLVIDO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.resolver").doesNotExist());

        mudarStatus(url, agente, "ABERTO").andExpect(status().isConflict());
        mudarStatus(url, tokenCidadao(), "DESCARTADO").andExpect(status().isForbidden());
    }

    @Test
    void cidadaoSoVeOsProprios404AntesDe403() throws Exception {
        long relatoDaCida = criarRelato(tokenCidadao(), criarArea("Área Posse " + sufixoUnico()), "OUTRO");
        String maria = tokenMaria();

        mockMvc.perform(get("/api/relatos/" + relatoDaCida).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/relatos/999999").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/relatos/" + relatoDaCida).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())))
                .andExpect(status().isOk());
    }

    @Test
    void meusRelatosTrazSoOsDoUsuarioDoToken() throws Exception {
        long area = criarArea("Área Meus " + sufixoUnico());
        long daMaria = criarRelato(tokenMaria(), area, "RIO_SUBINDO");

        mockMvc.perform(get("/api/relatos/meus").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.relatos[*].id", not(hasItem((int) daMaria))));
        mockMvc.perform(get("/api/relatos/meus").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(jsonPath("$._embedded.relatos[*].id", hasItem((int) daMaria)));
    }

    @Test
    void painelDeRelatosComFiltrosDinamicos() throws Exception {
        long area = criarArea("Área Painel " + sufixoUnico());
        String cidadao = tokenCidadao();
        long ilhada = criarRelato(cidadao, area, "PESSOA_ILHADA");
        long deslizamento = criarRelato(cidadao, area, "DESLIZAMENTO");
        criarRelato(cidadao, area, "BUEIRO_ENTUPIDO");

        mockMvc.perform(get("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isForbidden());

        String agente = tokenAgente();
        mockMvc.perform(get("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .param("areaId", String.valueOf(area)).param("prioridadeMin", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.relatos[*].id", contains((int) ilhada, (int) deslizamento))); // maior prioridade primeiro

        mockMvc.perform(get("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .param("areaId", String.valueOf(area)).param("tipo", "BUEIRO_ENTUPIDO").param("status", "ABERTO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .param("de", "2026-11-20T00:00:00Z").param("ate", "2026-11-19T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exclusaoRespeitaAutorEStatus() throws Exception {
        long area = criarArea("Área Exclusão " + sufixoUnico());
        String cidadao = tokenCidadao();

        long aberto = criarRelato(cidadao, area, "OUTRO");
        mockMvc.perform(delete("/api/relatos/" + aberto).header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/relatos/" + aberto).header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isNoContent());

        long emAtendimento = criarRelato(cidadao, area, "OUTRO");
        mudarStatus("/api/relatos/" + emAtendimento + "/status", tokenAgente(), "EM_ATENDIMENTO");
        mockMvc.perform(delete("/api/relatos/" + emAtendimento).header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/relatos/" + emAtendimento).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isNoContent());
    }

    @Test
    void relatoExigeLoginEDescricaoMinima() throws Exception {
        long area = criarArea("Área Relato Inválido " + sufixoUnico());
        String corpo = """
                {"tipo": "OUTRO", "descricao": "curta", "areaId": %d}""".formatted(area);
        postJson("/api/relatos", null, corpo).andExpect(status().isUnauthorized());
        postJson("/api/relatos", tokenCidadao(), corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("descricao"));
    }

    private org.springframework.test.web.servlet.ResultActions mudarStatus(String url, String token, String status) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}"));
    }
}
