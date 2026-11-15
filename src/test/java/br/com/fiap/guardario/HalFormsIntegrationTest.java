package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

    // a ação que manda um valor fixo oferece só esse valor, já preenchido (value) e obrigatório (minItems 1): o atender não
    // oferece RESOLVIDO, e o fechar do abrigo não oferece ABERTO
    @Test
    void acaoComValorFixoOfereceSoOValorDela() throws Exception {
        long relato = criarRelato(tokenCidadao(), criarArea("Área HAL-FORMS Valor " + sufixoUnico()), "OUTRO");
        mockMvc.perform(get("/api/relatos/" + relato).header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(HAL_FORMS))
                .andExpect(jsonPath("$._templates.atender.properties[?(@.name == 'status')].options.inline[*]").value(contains("EM_ATENDIMENTO")))
                .andExpect(jsonPath("$._templates.atender.properties[?(@.name == 'status')].value").value(contains("EM_ATENDIMENTO")))
                .andExpect(jsonPath("$._templates.atender.properties[?(@.name == 'status')].options.minItems").value(hasItem(1)))
                .andExpect(jsonPath("$._templates.descartar.properties[?(@.name == 'status')].options.inline[*]").value(contains("DESCARTADO")));
        mockMvc.perform(get("/api/abrigos/1").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())).accept(HAL_FORMS))
                .andExpect(jsonPath("$._templates.fechar.properties[?(@.name == 'status')].options.inline[*]").value(contains("FECHADO")));
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

    // o /me anuncia as ações do próprio cadastro, como os outros recursos: editar o perfil e trocar a senha, com title
    // e template HAL-FORMS, e o link dos alertas da área. O ADMIN vendo o cadastro de outra pessoa não recebe essas ações
    @Test
    void meAnunciaAsAcoesDoProprioCadastro() throws Exception {
        String cidadao = tokenCidadao();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.editar-perfil.href", endsWith("/api/auth/me")))
                .andExpect(jsonPath("$._links.editar-perfil.title").isNotEmpty())
                .andExpect(jsonPath("$._links.trocar-senha.href", endsWith("/api/auth/senha")))
                .andExpect(jsonPath("$._links.trocar-senha.title").isNotEmpty())
                .andExpect(jsonPath("$._links.alertas-da-minha-area.href", endsWith("/api/alertas/minha-area")))
                .andExpect(jsonPath("$._templates").doesNotExist());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(cidadao)).accept(HAL_FORMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._templates.editar-perfil.method").value("PUT"))
                .andExpect(jsonPath("$._templates.editar-perfil.target", endsWith("/api/auth/me")))
                .andExpect(jsonPath("$._templates.editar-perfil.properties[*].name", containsInAnyOrder("nome", "telefone", "areaId")))
                .andExpect(jsonPath("$._templates.trocar-senha.method").value("PUT"))
                .andExpect(jsonPath("$._templates.trocar-senha.target", endsWith("/api/auth/senha")))
                .andExpect(jsonPath("$._templates.trocar-senha.properties[*].name", containsInAnyOrder("senhaAtual", "novaSenha")))
                .andExpect(jsonPath("$..properties[?(@.readOnly == true)]").isEmpty());

        mockMvc.perform(get("/api/usuarios/3").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.editar-perfil").doesNotExist())
                .andExpect(jsonPath("$._links.trocar-senha").doesNotExist());
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
