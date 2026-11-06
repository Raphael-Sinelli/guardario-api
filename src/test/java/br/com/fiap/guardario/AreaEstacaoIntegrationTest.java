package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AreaEstacaoIntegrationTest extends IntegracaoBase {

    @Test
    void listaDeAreasEhPublicaPaginadaEComLinks() throws Exception {
        mockMvc.perform(get("/api/areas").param("size", "2").param("sort", "nome,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.areas.length()").value(2))
                .andExpect(jsonPath("$._embedded.areas[0]._links.self.href").exists())
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$._links.next.href").exists());
    }

    @Test
    void crudCompletoDeAreaComoAdmin() throws Exception {
        String admin = tokenAdmin();
        var criada = postJson("/api/areas", admin, """
                {"nome": "Vila Teste", "cidade": "Guarulhos", "uf": "sp"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/areas/")))
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.nivelAtual").value("NORMAL"));
        long id = id(criada);

        mockMvc.perform(put("/api/areas/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Vila Teste Renomeada", "cidade": "Guarulhos", "uf": "SP"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vila Teste Renomeada"));

        mockMvc.perform(get("/api/areas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Vila Teste Renomeada"));

        mockMvc.perform(delete("/api/areas/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/areas/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Não existe área com id " + id));
    }

    @Test
    void escritaDeAreaExigeLoginEPapelAdmin() throws Exception {
        String corpo = """
                {"nome": "Sem Permissão", "cidade": "São Paulo", "uf": "SP"}""";
        postJson("/api/areas", null, corpo).andExpect(status().isUnauthorized());
        postJson("/api/areas", tokenCidadao(), corpo).andExpect(status().isForbidden());
        postJson("/api/areas", tokenAgente(), corpo).andExpect(status().isForbidden());
    }

    @Test
    void linksDeAcaoSoAparecemParaQuemPodeExecutar() throws Exception {
        long area = criarArea("Área Links " + sufixoUnico());

        mockMvc.perform(get("/api/areas/" + area))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.estacoes.href").exists())
                .andExpect(jsonPath("$._links.excluir").doesNotExist());

        mockMvc.perform(get("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(jsonPath("$._links.excluir.href").exists())
                .andExpect(jsonPath("$._links.atualizar.href").exists());
    }

    @Test
    void erroDeCampoDevolve400ComListaDeErros() throws Exception {
        postJson("/api/areas", tokenAdmin(), """
                {"nome": " ", "cidade": "São Paulo", "uf": "SPX", "latitude": 200}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("nome", "uf", "latitude")));
    }

    @Test
    void cotasForaDeOrdemDevolvemErroDeClasse() throws Exception {
        long area = criarArea("Área Cotas " + sufixoUnico());
        postJson("/api/estacoes", tokenAdmin(), """
                {"codigo": "T-COTAS", "nome": "Cotas invertidas", "rio": "Rio",
                 "cotaAtencaoCm": 300, "cotaAlertaCm": 200, "cotaTransbordoCm": 100, "areaId": %d}""".formatted(area))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("crescentes")));
    }

    @Test
    void parametrosInvalidosDevolvem400() throws Exception {
        mockMvc.perform(get("/api/areas/0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        mockMvc.perform(get("/api/areas/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/areas").param("sort", "campoQueNaoExiste")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/alertas").param("status", "XYZ")).andExpect(status().isBadRequest());
    }

    @Test
    void areaComEstacaoNaoPodeSerExcluida() throws Exception {
        long area = criarArea("Área Vinculada " + sufixoUnico());
        criarEstacao(area);
        mockMvc.perform(delete("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isConflict());
    }

    @Test
    void areaComMoradorCadastradoDevolve409ENao500() throws Exception {
        long area = criarArea("Área Morador " + sufixoUnico());
        postJson("/api/auth/register", null, """
                {"nome": "Morador", "email": "morador.%s@email.com", "senha": "Senha@123", "areaId": %d}"""
                .formatted(sufixoUnico().toLowerCase(), area))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isConflict());
    }

    @Test
    void codigoDeEstacaoRepetidoDevolve409() throws Exception {
        long area = criarArea("Área Código " + sufixoUnico());
        postJson("/api/estacoes", tokenAdmin(), """
                {"codigo": "gr-sp-001", "nome": "Duplicada", "rio": "Rio",
                 "cotaAtencaoCm": 100, "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "areaId": %d}""".formatted(area))
                .andExpect(status().isConflict());
    }

    @Test
    void estacoesDaAreaPorDerivedQueryComNomeDaArea() throws Exception {
        long area = criarArea("Área Estações " + sufixoUnico());
        criarEstacao(area);
        criarEstacao(area);
        mockMvc.perform(get("/api/estacoes").param("areaId", String.valueOf(area)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._embedded.estacoes[*].areaNome", everyItem(startsWith("Área Estações"))));
    }

    @Test
    void rankingDeAreasPorJpql() throws Exception {
        mockMvc.perform(get("/api/areas/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relatosEmAberto").isNumber())
                .andExpect(jsonPath("$[*].nome", hasItem("Sarandi")));
    }
}
