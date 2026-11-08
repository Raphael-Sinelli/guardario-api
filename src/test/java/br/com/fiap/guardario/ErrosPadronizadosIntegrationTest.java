package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O app trata todo erro do mesmo jeito: error é o nome do status HTTP, message explica, e erro de um campo
 * do corpo vem em errors com o nome do campo, venha ele da Bean Validation ou de um valor que nem chegou a ser lido.
 */
class ErrosPadronizadosIntegrationTest extends IntegracaoBase {

    @Test
    void erroDeValidacaoTemErrorPadraoEMessage() throws Exception {
        postJson("/api/relatos", tokenCidadao(), """
                {"tipo": "OUTRO", "descricao": "curta", "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors[0].field").value("descricao"));
    }

    // o idioma não pode depender do Accept-Language nem do locale da JVM (VM Linux em inglês, por exemplo)
    @Test
    void mensagensDeValidacaoSaemEmPortuguesMesmoComNavegadorEmIngles() throws Exception {
        mockMvc.perform(post("/api/auth/register").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"\", \"email\": \"pessoa@email.com\", \"senha\": \"Senha@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nome"))
                .andExpect(jsonPath("$.errors[0].message").value("não deve estar em branco"));
    }

    @Test
    void valorForaDoEnumNoCorpoApontaOCampoEOsValoresAceitos() throws Exception {
        postJson("/api/relatos", tokenCidadao(), """
                {"tipo": "ENCHENTE_GIGANTE", "descricao": "Descrição com tamanho suficiente", "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tipo"))
                .andExpect(jsonPath("$.errors[0].message", containsString("PESSOA_ILHADA")));
    }

    @Test
    void textoNoLugarDeNumeroApontaOCampo() throws Exception {
        postJson("/api/estacoes/1/leituras", tokenAgente(), """
                {"nivelCm": "muito alto", "chuvaMmH": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nivelCm"));
    }

    // o JSON é válido; o número é que não cabe no campo inteiro
    @Test
    void numeroForaDoIntervaloApontaOCampo() throws Exception {
        postJson("/api/areas", tokenAdmin(), """
                {"nome": "Vila Grande", "cidade": "São Paulo", "uf": "SP", "populacaoEstimada": 99999999999}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("populacaoEstimada"))
                .andExpect(jsonPath("$.errors[0].message", containsString("fora do intervalo")));
    }

    // id "abc" e id 0 são o mesmo tipo de erro (parâmetro inválido): os dois saem com o campo em errors
    @Test
    void parametroDeRotaOuDeConsultaComTipoErradoApontaOCampo() throws Exception {
        mockMvc.perform(get("/api/areas/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("deve ser um número inteiro"));
        mockMvc.perform(get("/api/areas/0"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        mockMvc.perform(get("/api/alertas").param("status", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message", containsString("ATIVO")));
    }

    @Test
    void jsonMalFormadoContinuaComMensagemSobreOJson() throws Exception {
        postJson("/api/relatos", tokenCidadao(), "{\"tipo\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("JSON")));
    }

    // a lista de valores aceitos é a do campo: no alerta manual, NORMAL não é aceito e não aparece
    @Test
    void nivelInvalidoNoAlertaListaSoOsNiveisDeAlerta() throws Exception {
        postJson("/api/alertas", tokenAgente(), """
                {"areaId": 1, "nivel": "XYZ", "titulo": "Teste", "mensagem": "Mensagem com tamanho suficiente"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nivel"))
                .andExpect(jsonPath("$.errors[0].message", containsString("EMERGENCIA")))
                .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.not(containsString("NORMAL"))));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.AlertaRequest.properties.nivel.enum.length()").value(3));
    }

    @Test
    void alertaManualComNivelNormalApontaOCampoNivel() throws Exception {
        postJson("/api/alertas", tokenAgente(), """
                {"areaId": 1, "nivel": "NORMAL", "titulo": "Sem risco", "mensagem": "Mensagem com tamanho suficiente"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nivel"));
    }

    @Test
    void ufQueNaoExisteDevolve400() throws Exception {
        postJson("/api/areas", tokenAdmin(), """
                {"nome": "Vila", "cidade": "Cidade", "uf": "XX"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("uf"));
    }

    @Test
    void cadastroRecusaEmailSemDominioETelefoneComLetras() throws Exception {
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa", "email": "x@y", "senha": "Senha@123", "telefone": "abc!!"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("email", "telefone")));
    }

    // sem coerção: texto no lugar de número, número no lugar de booleano e número no lugar do enum são erros do cliente
    @Test
    void valorDeOutroTipoNaoEhConvertidoEmSilencio() throws Exception {
        postJson("/api/estacoes/1/leituras", tokenAgente(), """
                {"nivelCm": "100", "chuvaMmH": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nivelCm"));
        postJson("/api/abrigos", tokenAgente(), """
                {"nome": "Abrigo", "endereco": "Rua 1", "capacidade": 10, "aceitaPets": 1, "acessivelPcd": true, "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("aceitaPets"));
        postJson("/api/relatos", tokenCidadao(), """
                {"tipo": 0, "descricao": "Descrição com tamanho suficiente", "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tipo"));
    }

    // o caminho contrário: número ou booleano num campo de texto era gravado como "12345" ou "true"
    @Test
    void numeroOuBooleanoEmCampoDeTextoNaoViraTexto() throws Exception {
        postJson("/api/areas", tokenAdmin(), """
                {"nome": 12345, "cidade": "São Paulo", "uf": "SP"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nome"))
                .andExpect(jsonPath("$.errors[0].message").value("deve ser um texto entre aspas"));
        postJson("/api/abrigos", tokenAgente(), """
                {"nome": "Abrigo", "endereco": true, "capacidade": 10, "aceitaPets": true, "acessivelPcd": true, "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endereco"));
        postJson("/api/relatos", tokenCidadao(), """
                {"tipo": "OUTRO", "descricao": 12.5, "areaId": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("descricao"));
    }

    // o cliente pediu um formato que a API não produz: o HTTP permite ignorar o Accept na resposta de erro,
    // e o erro sai no JSON padrão em vez de um 406 com corpo vazio
    @Test
    void formatoNaoAceitoDevolve406ComOCorpoPadraoEmJson() throws Exception {
        mockMvc.perform(get("/api/areas").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.message").value("Formato de resposta não suportado: a API responde em JSON"))
                .andExpect(jsonPath("$.path").value("/api/areas"));
        // qualquer outro erro pedido com um Accept que a API não produz também sai em JSON
        mockMvc.perform(get("/api/areas/999999").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(delete("/api/areas/999999").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin()))
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    // escrita pedida com um Accept sem JSON: o 406 sai antes de gravar. Antes ele saía depois, e a leitura ou o relato
    // ficavam gravados; o cliente que tentasse de novo gravava em dobro
    @Test
    void escritaComAcceptSemJsonDevolve406AntesDeGravar() throws Exception {
        long estacao = criarEstacao(criarArea("Área Accept " + sufixoUnico()));
        mockMvc.perform(post("/api/estacoes/" + estacao + "/leituras").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente()))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_XML)
                        .content("{\"nivelCm\": 77, \"chuvaMmH\": 1}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406));
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // o Accept de navegador, que tem */*, e o application/json continuam valendo
        mockMvc.perform(post("/api/estacoes/" + estacao + "/leituras").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente()))
                        .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT, "text/html,application/xml;q=0.9,*/*;q=0.8")
                        .content("{\"nivelCm\": 77, \"chuvaMmH\": 1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/estacoes/" + estacao + "/leituras").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente()))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"nivelCm\": 78, \"chuvaMmH\": 1}"))
                .andExpect(status().isCreated());
    }

    // método que a rota não aceita: 405 no JSON padrão e o header Allow com os métodos que ela aceita
    @Test
    void metodoNaoAceitoDevolve405ComOHeaderAllow() throws Exception {
        mockMvc.perform(delete("/api/areas").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")));
    }

    // JSON válido, mas no formato errado: a API esperava um objeto e chegou uma lista ou um valor solto
    @Test
    void corpoQueNaoEhObjetoDizOQueChegou() throws Exception {
        postJson("/api/relatos", tokenCidadao(), "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O corpo precisa ser um objeto JSON, entre chaves, e chegou uma lista"));
        postJson("/api/relatos", tokenCidadao(), "\"texto\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O corpo precisa ser um objeto JSON, entre chaves, e chegou um valor solto"));
        // JSON quebrado continua com a mensagem dele
        postJson("/api/relatos", tokenCidadao(), "{\"tipo\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("JSON mal formado")));
    }

    // sem isto o Jackson arredondava 1.7 cm para 1 em silêncio
    @Test
    void numeroDecimalEmCampoInteiroDevolve400() throws Exception {
        postJson("/api/estacoes/1/leituras", tokenAgente(), """
                {"nivelCm": 1.7, "chuvaMmH": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nivelCm"));
    }

    // page * size passa do maior inteiro, e o banco não aceita esse deslocamento: é erro do cliente, não 500
    @Test
    void paginaForaDoIntervaloDevolve400() throws Exception {
        mockMvc.perform(get("/api/alertas").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        mockMvc.perform(get("/api/alertas").param("page", "3").param("size", "100")).andExpect(status().isOk());
    }

    // ordenar por campo de outra entidade deixaria um anônimo ordenar alertas pelo hash da senha de quem emitiu
    // o nível de risco é texto no banco: ordenar por ele daria ordem alfabética (NORMAL depois de EMERGENCIA)
    @Test
    void nivelDeRiscoNaoEntraNaOrdenacao() throws Exception {
        for (String rota : new String[]{"/api/areas?sort=nivelAtual,desc", "/api/alertas?sort=nivel",
                "/api/estacoes?sort=ultimoRisco", "/api/estacoes/1/leituras?sort=nivelRisco"}) {
            mockMvc.perform(get(rota))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("sort"));
        }
    }

    @Test
    void ordenacaoSoPorCamposDoProprioRecurso() throws Exception {
        mockMvc.perform(get("/api/alertas").param("sort", "emitidoPor.senha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"))
                .andExpect(jsonPath("$.errors[0].message", containsString("emitidoPor.senha")));
        mockMvc.perform(get("/api/usuarios").param("sort", "senha,desc").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/areas").param("sort", "nome,desc")).andExpect(status().isOk());

        // o Spring Data resolve emitidoPorSenha como emitidoPor.senha pelo camelCase: só vale a lista de cada rota
        mockMvc.perform(get("/api/alertas").param("sort", "emitidoPorSenha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message", containsString("emitidoEm")));
        mockMvc.perform(get("/api/relatos").param("sort", "autorSenha").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/relatos").param("sort", "prioridade,desc").header(HttpHeaders.AUTHORIZATION, bearer(tokenAgente())))
                .andExpect(status().isOk());

        // cada termo do sort que não é direção vira mais um campo para o Spring Data: todos passam pela lista
        mockMvc.perform(get("/api/alertas").param("sort", "emitidoEm,emitidoPor.senha")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/alertas").param("sort", "titulo,emitidoPorSenha")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/relatos/meus").param("sort", "criadoEm,autor.senha")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao()))).andExpect(status().isBadRequest());
        // vários campos válidos e as palavras de direção continuam aceitos
        mockMvc.perform(get("/api/areas").param("sort", "uf,nome,ASC")).andExpect(status().isOk());
        mockMvc.perform(get("/api/areas").param("sort", "nome,desc,ignorecase")).andExpect(status().isOk());
    }

    // erro de um campo ou parâmetro sai sempre em errors com o nome dele, mesmo quando a regra está no service
    @Test
    void erroDeCampoConferidoNoServiceTambemApontaOCampo() throws Exception {
        String agente = tokenAgente();
        long areaDoAlerta = criarArea("Área Alerta Campo " + sufixoUnico());
        long estacaoDeOutraArea = criarEstacao(criarArea("Área Outra " + sufixoUnico()));
        postJson("/api/alertas", agente, """
                {"areaId": %d, "estacaoId": %d, "nivel": "ALERTA", "titulo": "Teste", "mensagem": "Mensagem do alerta"}"""
                .formatted(areaDoAlerta, estacaoDeOutraArea))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("estacaoId"));

        mockMvc.perform(patch("/api/abrigos/1/status").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"LOTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        mockMvc.perform(get("/api/relatos").header(HttpHeaders.AUTHORIZATION, bearer(agente))
                        .param("de", "2026-11-05T00:00:00Z").param("ate", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("de"));
    }

    // id em branco na rota (/api/areas/%20): erro do parâmetro id, como o id 0 ou "abc"
    @Test
    void idEmBrancoNaRotaApontaOParametro() throws Exception {
        mockMvc.perform(get("/api/areas/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    // erro devolvido pelo servidor fora do Spring MVC (TRACE recusado pelo Tomcat, URL inválida) passa pelo /error,
    // que responde no formato padrão e em português; o /error chamado direto é rota que não existe
    @Test
    void paginaDeErroDoServidorSaiNoFormatoPadrao() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 405)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/api/areas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("Método HTTP não aceito nesta rota"))
                .andExpect(jsonPath("$.path").value("/api/areas"));
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Rota não encontrada"));
    }

    // o 403 do SecurityConfig (área de URL) e o do @PreAuthorize têm a mesma mensagem
    @Test
    void forbiddenTemAMesmaMensagemNaUrlENoPreAuthorize() throws Exception {
        String mensagem = "Você não tem permissão para esta operação";
        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(mensagem));
        postJson("/api/areas", tokenCidadao(), """
                {"nome": "Vila", "cidade": "São Paulo", "uf": "SP"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(mensagem));
    }

    // a regra de posse explica o motivo, em vez da mensagem genérica
    @Test
    void forbiddenDaRegraDePosseMantemAMensagemDoService() throws Exception {
        long relatoDaCida = criarRelato(tokenCidadao(), criarArea("Área Posse " + sufixoUnico()), "OUTRO");
        mockMvc.perform(get("/api/relatos/" + relatoDaCida).header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Você só pode ver os seus próprios relatos"));
    }
}
