package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.message", containsString("page")));
        mockMvc.perform(get("/api/alertas").param("page", "3").param("size", "100")).andExpect(status().isOk());
    }

    // ordenar por campo de outra entidade deixaria um anônimo ordenar alertas pelo hash da senha de quem emitiu
    @Test
    void ordenacaoSoPorCamposDoProprioRecurso() throws Exception {
        mockMvc.perform(get("/api/alertas").param("sort", "emitidoPor.senha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("emitidoPor.senha")));
        mockMvc.perform(get("/api/usuarios").param("sort", "senha,desc").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/areas").param("sort", "nome,desc")).andExpect(status().isOk());

        // o Spring Data resolve emitidoPorSenha como emitidoPor.senha pelo camelCase: só vale a lista de cada rota
        mockMvc.perform(get("/api/alertas").param("sort", "emitidoPorSenha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("emitidoEm")));
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
