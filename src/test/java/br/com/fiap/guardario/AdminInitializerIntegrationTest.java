package br.com.fiap.guardario;

import br.com.fiap.guardario.configurations.AdminInitializer;
import br.com.fiap.guardario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No perfil prod não há carga de demonstração: o primeiro ADMIN vem de ADMIN_EMAIL e ADMIN_PASSWORD.
 */
@TestPropertySource(properties = {
        "app.admin.email=Chefe.DefesaCivil@guardario.com",
        "app.admin.password=Senha-Do-Ambiente-123"
})
class AdminInitializerIntegrationTest extends IntegracaoBase {

    private static final String EMAIL = "chefe.defesacivil@guardario.com";

    @Autowired
    private AdminInitializer adminInitializer;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void adminDasVariaveisDeAmbienteEntraComPapelAdmin() throws Exception {
        String token = token(EMAIL, "Senha-Do-Ambiente-123");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.papel").value("ADMIN"));
    }

    @Test
    void rodarDeNovoNaoDuplicaNemTrocaASenha() throws Exception {
        String hashAntes = usuarioRepository.findByEmail(EMAIL).orElseThrow().getSenha();

        adminInitializer.run(null);

        assertThat(usuarioRepository.findAll().stream().filter(u -> u.getEmail().equals(EMAIL))).hasSize(1);
        assertThat(usuarioRepository.findByEmail(EMAIL).orElseThrow().getSenha()).isEqualTo(hashAntes);
    }
}
