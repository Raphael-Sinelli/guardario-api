package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
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
        "app.admin.password=Senha-De-Producao-1",
        "app.jwt.secret=chave-so-deste-teste-com-mais-de-32-caracteres" // no prod a chave é obrigatória
})
class PerfilProdIntegrationTest extends IntegracaoBase {

    // num PostgreSQL vazio, o ddl-auto: update remove as restrições únicas antes de criá-las, e o banco responde com
    // o aviso "constraint ... does not exist, skipping" (SQLState 00000, que não é erro). Antes, o Hibernate registrava
    // esse aviso em WARN, e a primeira subida do prod saía com 4 linhas de WARN no log
    @Test
    void avisoDoPostgresAoCriarRestricaoNumBancoNovoNaoSaiComoWarn() {
        assertThat(LoggerFactory.getLogger("org.hibernate.orm.jdbc.warn").isWarnEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.hibernate.orm.jdbc.warn").isErrorEnabled()).isTrue();
    }

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
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
