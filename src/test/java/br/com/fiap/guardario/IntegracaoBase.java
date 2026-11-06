package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: sobe a aplicação inteira (com segurança, cache e H2 com os dados de demonstração)
 * e chama a API por HTTP com o MockMvc. Cada teste cria as próprias áreas e estações para não depender dos outros.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false"
})
@AutoConfigureMockMvc
public abstract class IntegracaoBase {

    protected static final String ADMIN = "admin@guardario.com";
    protected static final String AGENTE = "agente@guardario.com";
    protected static final String CIDADAO = "cidadao@guardario.com";
    protected static final String MARIA = "maria@guardario.com";

    @Autowired
    protected MockMvc mockMvc;

    protected String tokenAdmin() throws Exception {
        return token(ADMIN, "Admin@123");
    }

    protected String tokenAgente() throws Exception {
        return token(AGENTE, "Agente@123");
    }

    protected String tokenCidadao() throws Exception {
        return token(CIDADAO, "Cidadao@123");
    }

    protected String tokenMaria() throws Exception {
        return token(MARIA, "Maria@123");
    }

    protected String token(String email, String senha) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}""".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.token");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected ResultActions postJson(String url, String token, String json) throws Exception {
        var requisicao = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(requisicao);
    }

    protected static long id(ResultActions resultado) throws Exception {
        return id(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected static long id(String json, String caminho) {
        Number numero = JsonPath.read(json, caminho);
        return numero.longValue();
    }

    protected static String sufixoUnico() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ---------- fixtures criadas pela própria API ----------

    protected long criarArea(String nome) throws Exception {
        return id(postJson("/api/areas", tokenAdmin(), """
                {"nome": "%s", "cidade": "São Paulo", "uf": "SP", "populacaoEstimada": 1000}""".formatted(nome))
                .andExpect(status().isCreated()));
    }

    // cotas 100 / 200 / 300 cm
    protected long criarEstacao(long areaId) throws Exception {
        return id(postJson("/api/estacoes", tokenAdmin(), """
                {"codigo": "T-%s", "nome": "Estação de teste", "rio": "Rio Teste",
                 "cotaAtencaoCm": 100, "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "areaId": %d}"""
                .formatted(sufixoUnico(), areaId))
                .andExpect(status().isCreated()));
    }

    protected ResultActions registrarLeitura(long estacaoId, int nivelCm, double chuvaMmH) throws Exception {
        return postJson("/api/estacoes/" + estacaoId + "/leituras", tokenAgente(), """
                {"nivelCm": %d, "chuvaMmH": %s}""".formatted(nivelCm, chuvaMmH));
    }

    protected long criarAbrigo(long areaId, int capacidade, int ocupacao, boolean pets, boolean pcd) throws Exception {
        return id(postJson("/api/abrigos", tokenAgente(), """
                {"nome": "Abrigo %s", "endereco": "Rua de Teste, 1", "capacidade": %d, "ocupacao": %d,
                 "aceitaPets": %s, "acessivelPcd": %s, "areaId": %d}"""
                .formatted(sufixoUnico(), capacidade, ocupacao, pets, pcd, areaId))
                .andExpect(status().isCreated()));
    }

    protected long criarRelato(String token, long areaId, String tipo) throws Exception {
        return id(postJson("/api/relatos", token, """
                {"tipo": "%s", "descricao": "Relato de teste com descrição suficiente", "areaId": %d}"""
                .formatted(tipo, areaId))
                .andExpect(status().isCreated()));
    }
}
