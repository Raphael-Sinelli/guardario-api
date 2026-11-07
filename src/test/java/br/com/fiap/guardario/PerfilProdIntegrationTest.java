package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o perfil prod de verdade, trocando só o PostgreSQL por um H2 em memória:
 * sem carga de demonstração, com o primeiro ADMIN vindo das variáveis de ambiente e sem Swagger.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:guardario-prod;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.admin.email=admin.prod@guardario.com",
        "app.admin.password=Senha-De-Producao-1"
})
class PerfilProdIntegrationTest extends IntegracaoBase {

    @Test
    void producaoSobeSemCargaDeDemonstracaoESoComOAdminDoAmbiente() throws Exception {
        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        String token = token("admin.prod@guardario.com", "Senha-De-Producao-1");
        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    // semana 9: em produção a documentação fica desligada; SWAGGER_ENABLED=true liga para uma demonstração
    @Test
    void swaggerFicaDesligadoEmProducao() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
