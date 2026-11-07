package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void emailRepetidoDevolve409() throws Exception {
        postJson("/api/auth/register", null, """
                {"nome": "Outra Cida", "email": "CIDADAO@guardario.com", "senha": "Senha@123"}""")
                .andExpect(status().isConflict());
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
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos"));
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
}
