package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo principal do GuardaRio: leitura da estação -> classificação -> nível da área -> alerta automático.
 * Todas as estações de teste usam as cotas 100 / 200 / 300 cm.
 */
class LeituraAlertaIntegrationTest extends IntegracaoBase {

    @Test
    void leituraNormalNaoGeraAlerta() throws Exception {
        long estacao = criarEstacao(criarArea("Área Normal " + sufixoUnico()));
        registrarLeitura(estacao, 50, 2.0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leitura.nivelRisco").value("NORMAL"))
                .andExpect(jsonPath("$.alertaGeradoId").value(nullValue()));
    }

    @Test
    void leituraCriadaDevolveLocationELinksParaOAlertaGerado() throws Exception {
        long area = criarArea("Área Location " + sufixoUnico());
        long estacao = criarEstacao(area);

        var resposta = registrarLeitura(estacao, 250, 0.0)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/estacoes/" + estacao + "/leituras/")))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.estacao.href", endsWith("/api/estacoes/" + estacao)))
                .andExpect(jsonPath("$._links.area.href", endsWith("/api/areas/" + area)))
                .andExpect(jsonPath("$._links.alerta.href", containsString("/api/alertas/")))
                .andReturn().getResponse();
        long leitura = id(resposta.getContentAsString(), "$.leitura.id");

        mockMvc.perform(get(resposta.getHeader(HttpHeaders.LOCATION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leitura))
                .andExpect(jsonPath("$.nivelRisco").value("ALERTA"))
                .andExpect(jsonPath("$._links.self.href", endsWith("/api/estacoes/" + estacao + "/leituras/" + leitura)));

        // leitura sem alerta não tem o link alerta; e cada item da lista tem self
        registrarLeitura(estacao, 260, 0.0).andExpect(jsonPath("$._links.alerta").doesNotExist());
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$._embedded.leituras[0]._links.self.href").exists());

        // a leitura existe, mas não é desta estação
        long outraEstacao = criarEstacao(area);
        mockMvc.perform(get("/api/estacoes/" + outraEstacao + "/leituras/" + leitura))
                .andExpect(status().isNotFound());
    }

    @Test
    void leituraEmAlertaGeraAlertaAutomaticoQueApareceNosAtivos() throws Exception {
        long area = criarArea("Área Alerta " + sufixoUnico());
        long estacao = criarEstacao(area);

        String resposta = registrarLeitura(estacao, 210, 5.0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leitura.nivelRisco").value("ALERTA"))
                .andExpect(jsonPath("$.nivelArea").value("ALERTA"))
                .andExpect(jsonPath("$.nivelAreaAlterado").value(true))
                .andExpect(jsonPath("$.alertaGeradoId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long alerta = id(resposta, "$.alertaGeradoId");

        mockMvc.perform(get("/api/alertas/ativos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas[*].id", hasItem((int) alerta)));

        mockMvc.perform(get("/api/areas/" + area))
                .andExpect(jsonPath("$.nivelAtual").value("ALERTA"));

        // mesmo nível de novo: a área já está coberta, não duplica (RN02)
        registrarLeitura(estacao, 215, 5.0)
                .andExpect(jsonPath("$.alertaGeradoId").value(nullValue()));
    }

    @Test
    void emergenciaSubstituiOAlertaDeNivelMenor() throws Exception {
        long estacao = criarEstacao(criarArea("Área Emergência " + sufixoUnico()));
        long alertaAntigo = id(registrarLeitura(estacao, 220, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        long alertaNovo = id(registrarLeitura(estacao, 310, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");

        mockMvc.perform(get("/api/alertas/" + alertaAntigo))
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.encerradoPorId").value(nullValue()));
        mockMvc.perform(get("/api/alertas/" + alertaNovo))
                .andExpect(jsonPath("$.nivel").value("EMERGENCIA"))
                .andExpect(jsonPath("$.origem").value("AUTOMATICO"));
    }

    @Test
    void chuvaMuitoForteSobeUmNivel() throws Exception {
        long estacao = criarEstacao(criarArea("Área Chuva " + sufixoUnico()));
        registrarLeitura(estacao, 150, 55.0)
                .andExpect(jsonPath("$.leitura.nivelRisco").value("ALERTA"))
                .andExpect(jsonPath("$.alertaGeradoId").isNumber());
    }

    @Test
    void soAgenteOuAdminRegistramLeitura() throws Exception {
        long estacao = criarEstacao(criarArea("Área Permissão " + sufixoUnico()));
        String corpo = """
                {"nivelCm": 120, "chuvaMmH": 1}""";
        postJson("/api/estacoes/" + estacao + "/leituras", null, corpo).andExpect(status().isUnauthorized());
        postJson("/api/estacoes/" + estacao + "/leituras", tokenCidadao(), corpo).andExpect(status().isForbidden());
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAdmin(), corpo).andExpect(status().isCreated());
    }

    @Test
    void estacaoInativaNaoRecebeLeitura() throws Exception {
        long area = criarArea("Área Inativa " + sufixoUnico());
        long estacao = criarEstacao(area);
        mockMvc.perform(put("/api/estacoes/" + estacao).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "T-INATIVA-%s", "nome": "Desligada", "rio": "Rio", "cotaAtencaoCm": 100,
                                 "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "status": "INATIVA", "areaId": %d}"""
                                .formatted(sufixoUnico(), area)))
                .andExpect(status().isOk());

        registrarLeitura(estacao, 120, 0.0).andExpect(status().isConflict());
        mockMvc.perform(get("/api/estacoes/" + estacao))
                .andExpect(jsonPath("$._links['registrar-leitura']").doesNotExist());
    }

    @Test
    void encerramentoEhHumanoEDefinitivo() throws Exception {
        long estacao = criarEstacao(criarArea("Área Encerrar " + sufixoUnico()));
        long alerta = id(registrarLeitura(estacao, 250, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        String agente = tokenAgente();

        mockMvc.perform(get("/api/alertas/" + alerta).header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(jsonPath("$._links.encerrar.href").exists());

        mockMvc.perform(patch("/api/alertas/" + alerta + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(tokenCidadao())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/alertas/" + alerta + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.encerradoPorId").isNumber())
                .andExpect(jsonPath("$._links.encerrar").doesNotExist());

        mockMvc.perform(patch("/api/alertas/" + alerta + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(status().isConflict());
    }

    @Test
    void alertaManualRespeitaOsAlertasAtivosDaArea() throws Exception {
        long area = criarArea("Área Manual " + sufixoUnico());
        String agente = tokenAgente();
        String alerta = """
                {"areaId": %d, "nivel": "%s", "titulo": "Chuva forte prevista", "mensagem": "Preparem-se para sair."}""";

        postJson("/api/alertas", agente, alerta.formatted(area, "ALERTA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origem").value("MANUAL"))
                .andExpect(jsonPath("$.emitidoPorId").isNumber());
        postJson("/api/alertas", agente, alerta.formatted(area, "ATENCAO")).andExpect(status().isConflict());
        postJson("/api/alertas", agente, alerta.formatted(area, "NORMAL")).andExpect(status().isBadRequest());
        postJson("/api/alertas", tokenCidadao(), alerta.formatted(area, "EMERGENCIA")).andExpect(status().isForbidden());
    }

    @Test
    void historicoEstatisticaECriticas() throws Exception {
        long estacao = criarEstacao(criarArea("Área Histórico " + sufixoUnico()));
        registrarLeitura(estacao, 100, 0.0);
        registrarLeitura(estacao, 200, 0.0);
        registrarLeitura(estacao, 240, 30.0);

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.leituras.length()").value(3))
                .andExpect(jsonPath("$._embedded.leituras[0].nivelCm").value(240)); // mais recente primeiro

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras/estatisticas").param("horas", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeLeituras").value(3))
                .andExpect(jsonPath("$.nivelMaximoCm").value(240))
                .andExpect(jsonPath("$.nivelMinimoCm").value(100))
                .andExpect(jsonPath("$.chuvaMaximaMmH").value(30.0));

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras/estatisticas").param("horas", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/estacoes/criticas"))
                .andExpect(jsonPath("$._embedded.estacoes[*].id", hasItem((int) estacao)));
    }

    @Test
    void leituraComDataNoFuturoDevolve400() throws Exception {
        long estacao = criarEstacao(criarArea("Área Futuro " + sufixoUnico()));
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(), """
                {"nivelCm": 120, "chuvaMmH": 0, "medidaEm": "2999-01-01T00:00:00Z"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("medidaEm"));
    }
}
