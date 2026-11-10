package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HAL-FORMS sob pedido: com Accept: application/prs.hal-forms+json, cada ação vem com o template dela (método, alvo e
 * campos do corpo). Sem pedir, a resposta continua em HAL, sem templates, como o app lê hoje.
 */
class HalFormsIntegrationTest extends IntegracaoBase {

    private static final MediaType HAL_FORMS = MediaType.parseMediaType("application/prs.hal-forms+json");

    @Test
    void alertaAtivoEmHalFormsTrazOsTemplatesDasAcoes() throws Exception {
        long alerta = alertaAtivoNovo();
        mockMvc.perform(get("/api/alertas/" + alerta).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(HAL_FORMS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(HAL_FORMS))
                .andExpect(jsonPath("$._links.encerrar.href", endsWith("/api/alertas/" + alerta + "/encerrar")))
                .andExpect(jsonPath("$._templates.encerrar.method").value("PATCH"))
                .andExpect(jsonPath("$._templates.encerrar.target", endsWith("/api/alertas/" + alerta + "/encerrar")))
                .andExpect(jsonPath("$._templates.editar.method").value("PUT"))
                .andExpect(jsonPath("$._templates.editar.properties[*].name", containsInAnyOrder("titulo", "mensagem")))
                // campo de entrada: editável, obrigatório e com o tamanho do texto, e não um intervalo de números
                .andExpect(jsonPath("$._templates.editar.properties[?(@.readOnly == true)]").isEmpty())
                .andExpect(jsonPath("$._templates.editar.properties[?(@.name == 'titulo')].required").value(hasItem(true)))
                .andExpect(jsonPath("$._templates.editar.properties[?(@.name == 'titulo')].maxLength").value(hasItem(150)))
                .andExpect(jsonPath("$._templates.editar.properties[?(@.name == 'titulo')].type").value(hasItem("text")));
    }

    @Test
    void relatoAbertoEmHalFormsTemUmTemplatePorAcao() throws Exception {
        long relato = criarRelato(tokenCidadao(), criarArea("Área HAL-FORMS Relato " + sufixoUnico()), "OUTRO");
        mockMvc.perform(get("/api/relatos/" + relato).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(HAL_FORMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._templates.atender.method").value("PATCH"))
                .andExpect(jsonPath("$._templates.descartar.method").value("PATCH"))
                .andExpect(jsonPath("$._templates.descartar.properties[?(@.name == 'status')].options.inline[*]").value(hasItem("DESCARTADO")))
                .andExpect(jsonPath("$._templates.descartar.target", endsWith("/api/relatos/" + relato + "/status")))
                .andExpect(jsonPath("$._templates.descartar.properties[*].name", hasItem("status")))
                // só os templates das ações, com o nome do link: nenhum template automático com o nome do método Java
                .andExpect(jsonPath("$._templates.keys()", containsInAnyOrder("atender", "descartar")));
    }

    // em todo recurso com ações, os templates são só os das ações, e nenhum campo de entrada sai como só leitura ou
    // como intervalo de números quando é texto. O self continua o primeiro link
    @Test
    void templatesSaoSoOsDasAcoesECamposEditaveis() throws Exception {
        String admin = tokenAdmin();
        for (String rota : new String[]{"/api/abrigos/1", "/api/estacoes/1", "/api/usuarios/3", "/api/areas/1"}) {
            mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(admin)).accept(HAL_FORMS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._templates").isNotEmpty())
                    .andExpect(jsonPath("$._templates.keys()", everyItem(matchesPattern("[a-z-]+"))))
                    .andExpect(jsonPath("$..properties[?(@.readOnly == true)]").isEmpty())
                    .andExpect(jsonPath("$..properties[?(@.type == 'range')]").isEmpty())
                    .andExpect(jsonPath("$._links.keys()", hasItem("self")));
        }
        String abrigo = mockMvc.perform(get("/api/abrigos/1").header(HttpHeaders.AUTHORIZATION, bearer(admin)).accept(HAL_FORMS))
                .andReturn().getResponse().getContentAsString();
        assertThat(abrigo.indexOf("\"self\"")).isLessThan(abrigo.indexOf("\"atualizar\""));
    }

    // o formato padrão não muda: HAL, sem _templates, com ou sem Accept de JSON
    @Test
    void halContinuaOPadraoESemTemplates() throws Exception {
        long alerta = alertaAtivoNovo();
        mockMvc.perform(get("/api/alertas/" + alerta).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/hal+json")))
                .andExpect(jsonPath("$._links.encerrar.href").exists())
                .andExpect(jsonPath("$._templates").doesNotExist());
        mockMvc.perform(get("/api/alertas/" + alerta).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$._links.encerrar.href").exists())
                .andExpect(jsonPath("$._templates").doesNotExist());
    }

    // a escrita pedindo HAL-FORMS é aceita: o Accept é conferido antes de gravar e o HAL-FORMS está na lista
    @Test
    void escritaPedindoHalFormsNaoDa406() throws Exception {
        long alerta = alertaAtivoNovo();
        mockMvc.perform(patch("/api/alertas/" + alerta + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(HAL_FORMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"));
    }

    private long alertaAtivoNovo() throws Exception {
        String leitura = registrarLeitura(criarEstacao(criarArea("Área HAL-FORMS " + sufixoUnico())), 250, 0.0)
                .andReturn().getResponse().getContentAsString();
        return id(leitura, "$.alertaGeradoId");
    }
}
