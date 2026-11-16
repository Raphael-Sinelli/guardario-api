package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegracaoBase {

    @Test
    void cadastroPublicoSempreCriaCidadaoMesmoSeOClientePedirOutroPapel() throws Exception {
        String email = "novo" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa Nova", "email": "%s", "senha": "Senha@123", "papel": "ADMIN"}""".formatted(email))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/usuarios/")))
                .andExpect(jsonPath("$.papel").value("CIDADAO"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    // o BCrypt só considera 72 bytes, e letra com acento ocupa 2 bytes em UTF-8: 40 letras "á" são 80 bytes
    @Test
    void senhaComMaisDe72BytesDevolve400EmPortugues() throws Exception {
        String senhaComAcento = "á".repeat(40);
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa Acentuada", "email": "acento%s@email.com", "senha": "%s"}"""
                .formatted(sufixoUnico().toLowerCase(), senhaComAcento))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senha"))
                .andExpect(jsonPath("$.errors[0].message", containsString("72 bytes")));

        postJson("/api/auth/login", null, """
                {"email": "cidadao@guardario.com", "senha": "%s"}""".formatted(senhaComAcento))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senha"));
    }

    // a mensagem e o Swagger mostram o limite real da senha, e não o maior inteiro do Java
    @Test
    void senhaCurtaMostraOLimiteDe8A72() throws Exception {
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa", "email": "curta%s@email.com", "senha": "123"}""".formatted(sufixoUnico().toLowerCase()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("tamanho deve ser entre 8 e 72"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.senha.maxLength").value(72));
    }

    // o Location do cadastro aponta para o recurso criado, e o próprio usuário consegue segui-lo depois do login
    @Test
    void locationDoCadastroEhAcessivelPeloProprioUsuario() throws Exception {
        String email = "location" + sufixoUnico().toLowerCase() + "@email.com";
        String location = postJson("/api/auth/register", null, """
                {"nome": "Pessoa Nova", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(token(email, "Senha@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$._links.self.href").value(location))
                .andExpect(jsonPath("$._links.usuarios").doesNotExist())
                .andExpect(jsonPath("$._links['alterar-papel']").doesNotExist());

        // regra de posse: outro cidadão recebe 403; id que não existe, 404 (404 antes de 403)
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuarios/999999").header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links['alterar-papel'].href").exists());
        // a lista continua só do ADMIN
        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(tokenMaria())))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailRepetidoDevolve409() throws Exception {
        postJson("/api/auth/register", null, """
                {"nome": "Outra Cida", "email": "CIDADAO@guardario.com", "senha": "Senha@123"}""")
                .andExpect(status().isConflict());
    }

    // em minúsculas, cada "İ" vira dois caracteres (i e o ponto): o email de 150 cresce para 214 e não cabe na
    // coluna. Antes, o banco recusava o INSERT e a resposta era 409 dizendo que esse email já tinha cadastro
    @Test
    void emailQueCresceEmMinusculasDevolve400ENao409() throws Exception {
        String email = "İ".repeat(64) + "@" + "d".repeat(40) + "." + "d".repeat(40) + ".com";
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa do Email Longo", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void cadastroInvalidoDevolve400ComTodosOsCampos() throws Exception {
        postJson("/api/auth/register", null, """
                {"nome": "", "email": "nao-e-email", "senha": "123"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("nome", "email", "senha")));
    }

    @Test
    void loginDevolveTokenQueAbreRotaProtegida() throws Exception {
        String token = tokenCidadao();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CIDADAO))
                .andExpect(jsonPath("$.papel").value("CIDADAO"));
    }

    // o /me também é HATEOAS: os campos de sempre, mais o self do cadastro e os recursos ligados ao usuário
    @Test
    void meTrazOsLinksDoUsuario() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CIDADAO))
                .andExpect(jsonPath("$._links.self.href", containsString("/api/usuarios/")))
                .andExpect(jsonPath("$._links.meus-relatos.href", containsString("/api/relatos/meus")))
                .andExpect(jsonPath("$._links.area.href", containsString("/api/areas/")))
                .andExpect(jsonPath("$._links.usuarios").doesNotExist());
    }

    // o app pode mandar o email com o espaço que o teclado do celular coloca depois do autocompletar
    @Test
    void loginAceitaEmailComEspacoEmVolta() throws Exception {
        postJson("/api/auth/login", null, """
                {"email": " cidadao@guardario.com ", "senha": "Cidadao@123"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CIDADAO));
    }

    @Test
    void senhaErradaDevolve401ComMensagemGenerica() throws Exception {
        postJson("/api/auth/login", null, """
                {"email": "admin@guardario.com", "senha": "errada123"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    // RFC 6750: todo 401 diz como se autenticar; com um token que não valeu, o desafio diz que ele é inválido
    @Test
    void respostaDe401TrazODesafioBearer() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao() + "x")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    void semTokenOuComTokenAdulteradoDevolve401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String adulterado = tokenCidadao() + "x";
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adulterado)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- troca de senha (PUT /api/auth/senha) ----------
    // cada teste cadastra o próprio usuário: a senha dos usuários de demonstração não pode mudar

    private String cadastrarUsuarioParaTrocaDeSenha() throws Exception {
        String email = "senha" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa da Senha", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isCreated());
        return email;
    }

    @Test
    void trocaDeSenhaDevolve204EOLoginPassaAUsarASenhaNova() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String token = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "NovaSenha@456"}"""))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        postJson("/api/auth/login", null, """
                {"email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, """
                {"email": "%s", "senha": "NovaSenha@456"}""".formatted(email))
                .andExpect(status().isOk());
    }

    @Test
    void trocaDeSenhaComSenhaAtualErradaDevolve400NoCampoSenhaAtual() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String token = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Errada@999", "novaSenha": "NovaSenha@456"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senhaAtual"))
                .andExpect(jsonPath("$.errors[0].message").value("Senha atual incorreta"));

        // a senha continua a mesma
        token(email, "Senha@123");
    }

    @Test
    void trocaDeSenhaComNovaSenhaCurtaDevolve400NoCampoNovaSenha() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String token = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "12345"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"));
    }

    // 40 letras "á" passam no @Size (40 caracteres), mas são 80 bytes: só o limite de 72 bytes do BCrypt barra
    @Test
    void trocaDeSenhaComNovaSenhaDeMaisDe72BytesDevolve400() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String token = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "%s"}""".formatted("á".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"))
                .andExpect(jsonPath("$.errors[0].message", containsString("72 bytes")));

        // a senha continua a mesma
        token(email, "Senha@123");
    }

    @Test
    void trocaDeSenhaSemTokenDevolve401() throws Exception {
        mockMvc.perform(put("/api/auth/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "NovaSenha@456"}"""))
                .andExpect(status().isUnauthorized());
    }

    // a nova senha igual à atual não troca nada: 400 no campo novaSenha, e a senha continua valendo
    @Test
    void trocaDeSenhaComNovaSenhaIgualAAtualDevolve400NoCampoNovaSenha() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String token = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "Senha@123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    // trocar a senha derruba todo token emitido antes, inclusive o da própria chamada e o de outra sessão; o login
    // com a senha nova dá um token que vale
    @Test
    void trocaDeSenhaRevogaOsTokensEmitidosAntes() throws Exception {
        String email = cadastrarUsuarioParaTrocaDeSenha();
        String daChamada = token(email, "Senha@123");
        String deOutraSessao = token(email, "Senha@123");

        mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(daChamada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "Senha@123", "novaSenha": "NovaSenha@456"}"""))
                .andExpect(status().isNoContent());

        for (String antigo : new String[]{daChamada, deOutraSessao}) {
            mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(antigo)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                    .andExpect(jsonPath("$.status").value(401));
        }
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token(email, "NovaSenha@456"))))
                .andExpect(status().isOk());
    }

    // ---------- edição do próprio perfil (PUT /api/auth/me) ----------
    // cada teste cadastra o próprio usuário: os dados dos usuários de demonstração não podem mudar

    private String cadastrarUsuarioParaEdicaoDePerfil() throws Exception {
        String email = "perfil" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa do Perfil", "email": "%s", "senha": "Senha@123", "telefone": "11911112222"}"""
                .formatted(email))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions atualizarPerfil(String token, String json) throws Exception {
        var requisicao = put("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(requisicao);
    }

    @Test
    void edicaoDePerfilMudaNomeEAreaEOMeMostraOsDadosNovos() throws Exception {
        long area = criarArea("Área Perfil " + sufixoUnico());
        String email = cadastrarUsuarioParaEdicaoDePerfil();
        String token = token(email, "Senha@123");

        atualizarPerfil(token, """
                {"nome": "  Nome Novo  ", "telefone": "11933334444", "areaId": %d}""".formatted(area))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"))
                .andExpect(jsonPath("$.telefone").value("11933334444"))
                .andExpect(jsonPath("$.areaId").value(area))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$._links.self.href", containsString("/api/usuarios/")))
                .andExpect(jsonPath("$._links.meus-relatos.href", containsString("/api/relatos/meus")));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"))
                .andExpect(jsonPath("$.telefone").value("11933334444"))
                .andExpect(jsonPath("$.areaId").value(area));

        // areaId nulo: a pessoa fica sem área
        atualizarPerfil(token, """
                {"nome": "Nome Novo", "telefone": "11933334444", "areaId": null}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(nullValue()));
    }

    @Test
    void edicaoDePerfilIgnoraPapelEEmailNoCorpo() throws Exception {
        String email = cadastrarUsuarioParaEdicaoDePerfil();
        String token = token(email, "Senha@123");

        atualizarPerfil(token, """
                {"nome": "Quer Ser Admin", "telefone": "11911112222", "papel": "ADMIN", "email": "outro@email.com"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("CIDADAO"))
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.papel").value("CIDADAO"));
    }

    @Test
    void edicaoDePerfilComCamposInvalidosDevolve400ComOsCampos() throws Exception {
        String email = cadastrarUsuarioParaEdicaoDePerfil();
        String token = token(email, "Senha@123");

        atualizarPerfil(token, """
                {"nome": "", "telefone": "abc123", "areaId": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("nome", "telefone", "areaId")));

        // nada mudou: o nome continua o do cadastro
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.nome").value("Pessoa do Perfil"))
                .andExpect(jsonPath("$.telefone").value("11911112222"));
    }

    @Test
    void edicaoDePerfilComAreaQueNaoExisteDevolve404() throws Exception {
        String email = cadastrarUsuarioParaEdicaoDePerfil();
        String token = token(email, "Senha@123");

        atualizarPerfil(token, """
                {"nome": "Pessoa do Perfil", "telefone": "11911112222", "areaId": 999999}""")
                .andExpect(status().isNotFound());
    }

    @Test
    void edicaoDePerfilSemTokenDevolve401() throws Exception {
        atualizarPerfil(null, """
                {"nome": "Sem Token", "telefone": "11911112222"}""")
                .andExpect(status().isUnauthorized());
    }
}
