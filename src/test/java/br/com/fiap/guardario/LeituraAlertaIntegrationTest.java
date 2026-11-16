package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    // RN02 com leituras simultâneas: dez leituras em ALERTA ao mesmo tempo abrem um alerta só
    @Test
    void leiturasSimultaneasNaoDuplicamOAlertaAutomatico() throws Exception {
        long area = criarArea("Área Simultânea " + sufixoUnico());
        long estacao = criarEstacao(area);
        String agente = tokenAgente();

        emParalelo(10, () -> postJson("/api/estacoes/" + estacao + "/leituras", agente,
                "{\"nivelCm\": 240, \"chuvaMmH\": 5}").andReturn().getResponse().getStatus());

        mockMvc.perform(get("/api/alertas/ativos"))
                .andExpect(jsonPath("$._embedded.alertas[?(@.areaId == %d)]".formatted(area), hasSize(1)));
    }

    // RN03 com leituras simultâneas: uma estação baixa e a outra sobe ao mesmo tempo; a área fica no maior risco
    @Test
    void leiturasSimultaneasDeDuasEstacoesNaoPerdemONivelDaArea() throws Exception {
        long area = criarArea("Área Duas Estações " + sufixoUnico());
        long primeira = criarEstacao(area);
        long segunda = criarEstacao(area);
        String agente = tokenAgente();

        for (int rodada = 0; rodada < 5; rodada++) {
            registrarLeitura(primeira, 320, 0.0);
            registrarLeitura(segunda, 10, 0.0);
            long sobe = segunda;
            long desce = primeira;
            emParalelo(2,
                    () -> postJson("/api/estacoes/" + desce + "/leituras", agente, "{\"nivelCm\": 10, \"chuvaMmH\": 0}")
                            .andReturn().getResponse().getStatus(),
                    () -> postJson("/api/estacoes/" + sobe + "/leituras", agente, "{\"nivelCm\": 320, \"chuvaMmH\": 0}")
                            .andReturn().getResponse().getStatus());
            mockMvc.perform(get("/api/areas/" + area)).andExpect(jsonPath("$.nivelAtual").value("EMERGENCIA"));
        }
    }

    // roda as tarefas ao mesmo tempo (a última se repete até completar o total) e confere que todas responderam 201
    @SafeVarargs
    private void emParalelo(int total, java.util.concurrent.Callable<Integer>... tarefas) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(total);
        java.util.List<java.util.concurrent.Future<Integer>> respostas = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            respostas.add(pool.submit(tarefas[Math.min(i, tarefas.length - 1)]));
        }
        pool.shutdown();
        for (java.util.concurrent.Future<Integer> resposta : respostas) {
            org.assertj.core.api.Assertions.assertThat(resposta.get(60, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        }
    }

    @Test
    void leituraComDataAntigaNaoAbreAlertaNemMudaONivelDaArea() throws Exception {
        long area = criarArea("Área Data Antiga " + sufixoUnico());
        long estacao = criarEstacao(area);

        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(),
                "{\"nivelCm\": 290, \"chuvaMmH\": 0, \"medidaEm\": \"" + java.time.Instant.now().minus(java.time.Duration.ofDays(2)) + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leitura.nivelRisco").value("ALERTA"))
                .andExpect(jsonPath("$.alertaGeradoId").value(nullValue()))
                .andExpect(jsonPath("$.nivelArea").value("NORMAL"));

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
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

    // a estação guarda poucos dias sem internet: data de anos atrás é relógio errado, não leitura
    @Test
    void leituraComDataForaDaJanelaDevolve400() throws Exception {
        long estacao = criarEstacao(criarArea("Área Janela " + sufixoUnico()));
        for (String data : java.util.List.of("-1000000-01-01T00:00:00Z", "1900-01-01T00:00:00Z",
                java.time.Instant.now().minus(java.time.Duration.ofDays(31)).toString())) {
            postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(),
                    "{\"nivelCm\": 120, \"chuvaMmH\": 0, \"medidaEm\": \"" + data + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("medidaEm"));
        }
    }

    // data no futuro é 400 antes de qualquer conferência de repetição: na leitura avulsa (mandada duas vezes, a
    // repetida também é 400, e nada é gravado) e no lote, com o índice do item
    @Test
    void leituraComMedidaEmNoFuturoDevolve400MesmoRepetida() throws Exception {
        long estacao = criarEstacao(criarArea("Área Futuro " + sufixoUnico()));
        String corpo = "{\"nivelCm\": 60, \"chuvaMmH\": 2.0, \"medidaEm\": \"" + java.time.Instant.now().plusSeconds(30) + "\"}";
        for (int vez = 0; vez < 2; vez++) {
            postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(), corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("medidaEm"));
        }
        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), "{\"leituras\": [" + corpo + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("leituras[0].medidaEm"));
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // o Java mede o horário em frações de 100 ns e o banco guarda microssegundos, arredondando: sem cortar a fração,
    // a leitura gravada voltava do banco 1 µs mais tarde, e a seguinte, no mesmo instante, parecia atrasada e não valia
    @Test
    void duasLeiturasNoMesmoInstanteContamAMaisNovaComoAtual() throws Exception {
        long estacao = criarEstacao(criarArea("Área Mesmo Instante " + sufixoUnico()));
        String medidaEm = java.time.Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .plusNanos(999_999_900).toString(); // fração ,9999999 s: arredondada, iria para o segundo seguinte
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(),
                "{\"nivelCm\": 10, \"chuvaMmH\": 0, \"medidaEm\": \"" + medidaEm + "\"}")
                .andExpect(status().isCreated());

        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(),
                "{\"nivelCm\": 400, \"chuvaMmH\": 0, \"medidaEm\": \"" + medidaEm + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivelArea").value("EMERGENCIA"))
                .andExpect(jsonPath("$.alertaGeradoId").isNumber());
        mockMvc.perform(get("/api/estacoes/" + estacao))
                .andExpect(jsonPath("$.ultimoNivelCm").value(400))
                .andExpect(jsonPath("$.ultimoRisco").value("EMERGENCIA"));
    }

    // o alerta é histórico: o automático registra uma leitura real e nunca sai; o manual só sai depois de encerrado.
    // Sem isso, apagar o alerta abria caminho para apagar a estação e todo o histórico de leituras junto
    @Test
    void alertaSoEhExcluidoSeForManualEJaEncerrado() throws Exception {
        String admin = tokenAdmin();
        String agente = tokenAgente();
        long automatico = id(registrarLeitura(criarEstacao(criarArea("Área Excluir Automático " + sufixoUnico())), 250, 0.0)
                .andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        mockMvc.perform(patch("/api/alertas/" + automatico + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/alertas/" + automatico).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$._links.excluir").doesNotExist());
        mockMvc.perform(delete("/api/alertas/" + automatico).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("histórico")));

        long manual = id(postJson("/api/alertas", agente, """
                {"areaId": %d, "nivel": "ATENCAO", "titulo": "Alerta para excluir", "mensagem": "Emitido por engano no teste"}"""
                .formatted(criarArea("Área Excluir Manual " + sufixoUnico()))));
        mockMvc.perform(get("/api/alertas/" + manual).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$._links.excluir").doesNotExist());
        mockMvc.perform(delete("/api/alertas/" + manual).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Encerre o alerta")));
        mockMvc.perform(patch("/api/alertas/" + manual + "/encerrar").header(HttpHeaders.AUTHORIZATION, bearer(agente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/alertas/" + manual).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$._links.excluir.href").exists());
        mockMvc.perform(delete("/api/alertas/" + manual).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }

    // a estatística também é HATEOAS: self com a janela pedida, a estação e o histórico de leituras
    @Test
    void estatisticaTrazOsLinksDaEstacao() throws Exception {
        mockMvc.perform(get("/api/estacoes/1/leituras/estatisticas").param("horas", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeLeituras").isNumber())
                .andExpect(jsonPath("$._links.self.href", endsWith("/api/estacoes/1/leituras/estatisticas?horas=24")))
                .andExpect(jsonPath("$._links.estacao.href", endsWith("/api/estacoes/1")))
                .andExpect(jsonPath("$._links.leituras.href", endsWith("/api/estacoes/1/leituras")));
    }

    @Test
    void leituraComDataNoFuturoDevolve400() throws Exception {
        long estacao = criarEstacao(criarArea("Área Futuro " + sufixoUnico()));
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(), """
                {"nivelCm": 120, "chuvaMmH": 0, "medidaEm": "2999-01-01T00:00:00Z"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("medidaEm"));
    }

    @Test
    void leituraRepetidaDaMesmaEstacaoDevolve409ENaoGrava() throws Exception {
        long estacao = criarEstacao(criarArea("Área Repetida " + sufixoUnico()));
        String medidaEm = java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.MINUTES).toString();
        String corpo = "{\"nivelCm\": 250, \"chuvaMmH\": 0, \"medidaEm\": \"" + medidaEm + "\"}";

        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(), corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertaGeradoId").isNumber());
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(), corpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", startsWith("Leitura já registrada para esta estação em ")));

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void mesmoMedidaEmEmOutraEstacaoEhAceito() throws Exception {
        long area = criarArea("Área Duas Estações Repetida " + sufixoUnico());
        long primeira = criarEstacao(area);
        long segunda = criarEstacao(area);
        String corpo = "{\"nivelCm\": 50, \"chuvaMmH\": 0, \"medidaEm\": \""
                + java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.MINUTES) + "\"}";

        postJson("/api/estacoes/" + primeira + "/leituras", tokenAgente(), corpo).andExpect(status().isCreated());
        postJson("/api/estacoes/" + segunda + "/leituras", tokenAgente(), corpo).andExpect(status().isCreated());
    }

    @Test
    void leiturasSemMedidaEmNuncaSaoRepetidas() throws Exception {
        long estacao = criarEstacao(criarArea("Área Sem Horário " + sufixoUnico()));
        registrarLeitura(estacao, 50, 0.0).andExpect(status().isCreated());
        registrarLeitura(estacao, 50, 0.0).andExpect(status().isCreated());
    }

    @Test
    void loteForaDeOrdemEhProcessadoEmOrdemCronologica() throws Exception {
        long area = criarArea("Área Lote " + sufixoUnico());
        long estacao = criarEstacao(area);
        java.time.Instant agora = java.time.Instant.now();
        String corpo = """
                {"leituras": [
                  {"nivelCm": 120, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": 250, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": 150, "chuvaMmH": 0, "medidaEm": "%s"}
                ]}""".formatted(agora.minusSeconds(30 * 60), agora.minusSeconds(10 * 60), agora.minusSeconds(20 * 60));

        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), corpo)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/estacoes/" + estacao + "/leituras")))
                .andExpect(jsonPath("$.quantidade").value(3))
                .andExpect(jsonPath("$.areaId").value((int) area))
                .andExpect(jsonPath("$.nivelArea").value("ALERTA"))
                .andExpect(jsonPath("$.alertasGerados.length()").value(1))
                .andExpect(jsonPath("$._links.leituras.href", endsWith("/api/estacoes/" + estacao + "/leituras")));

        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        mockMvc.perform(get("/api/areas/" + area))
                .andExpect(jsonPath("$.nivelAtual").value("ALERTA"));
    }

    @Test
    void loteComLeituraInvalidaNaoGravaNenhuma() throws Exception {
        long estacao = criarEstacao(criarArea("Área Lote Inválido " + sufixoUnico()));
        String corpo = """
                {"leituras": [
                  {"nivelCm": 120, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": -5, "chuvaMmH": 0, "medidaEm": "%s"}
                ]}""".formatted(java.time.Instant.now().minusSeconds(600), java.time.Instant.now().minusSeconds(300));

        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), corpo)
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void loteVazioOuSemMedidaEmDevolve400() throws Exception {
        long estacao = criarEstacao(criarArea("Área Lote Vazio " + sufixoUnico()));
        String agente = tokenAgente();

        postJson("/api/estacoes/" + estacao + "/leituras/lote", agente, "{\"leituras\": []}")
                .andExpect(status().isBadRequest());
        postJson("/api/estacoes/" + estacao + "/leituras/lote", agente,
                "{\"leituras\": [{\"nivelCm\": 120, \"chuvaMmH\": 0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("leituras[0].medidaEm"));
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // o 409 do lote diz qual item repetiu (o índice que o cliente mandou) e o medidaEm, tanto para a leitura que a
    // estação já tem quanto para a que aparece duas vezes no próprio lote; e nada do lote é gravado
    @Test
    void loteComLeituraRepetidaDizOIndiceEOMedidaEm() throws Exception {
        long estacao = criarEstacao(criarArea("Área Lote Repetido " + sufixoUnico()));
        java.time.Instant agora = java.time.Instant.now();
        String jaGravada = agora.minusSeconds(20 * 60).toString();
        postJson("/api/estacoes/" + estacao + "/leituras", tokenAgente(),
                "{\"nivelCm\": 70, \"chuvaMmH\": 0, \"medidaEm\": \"" + jaGravada + "\"}")
                .andExpect(status().isCreated());

        String nova = agora.minusSeconds(10 * 60).toString();
        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), """
                {"leituras": [
                  {"nivelCm": 90, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": 70, "chuvaMmH": 0, "medidaEm": "%s"}
                ]}""".formatted(nova, jaGravada))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("leituras[1]")))
                .andExpect(jsonPath("$.message", containsString(jaGravada)));

        String outra = agora.minusSeconds(5 * 60).toString();
        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), """
                {"leituras": [
                  {"nivelCm": 80, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": 90, "chuvaMmH": 0, "medidaEm": "%s"},
                  {"nivelCm": 80, "chuvaMmH": 0, "medidaEm": "%s"}
                ]}""".formatted(outra, nova, outra))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("leituras[2]")))
                .andExpect(jsonPath("$.message", containsString(outra)));

        // tudo ou nada: só a leitura avulsa do começo ficou
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    // um item nulo na lista é erro do cliente: 400 no campo do item, e não 500 no service
    @Test
    void loteComLeituraNulaDevolve400() throws Exception {
        long estacao = criarEstacao(criarArea("Área Lote Nulo " + sufixoUnico()));
        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenAgente(), "{\"leituras\": [null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field", startsWith("leituras[0]")));
        mockMvc.perform(get("/api/estacoes/" + estacao + "/leituras"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void cidadaoNaoRegistraLote() throws Exception {
        long estacao = criarEstacao(criarArea("Área Lote Cidadão " + sufixoUnico()));
        postJson("/api/estacoes/" + estacao + "/leituras/lote", tokenCidadao(),
                "{\"leituras\": [{\"nivelCm\": 120, \"chuvaMmH\": 0, \"medidaEm\": \"" + java.time.Instant.now().minusSeconds(60) + "\"}]}")
                .andExpect(status().isForbidden());
    }

    // ---------- alertas da área do morador (GET /api/alertas/minha-area) ----------
    // cada teste cadastra o próprio morador: os dados dos usuários de demonstração não podem mudar

    @Test
    void minhaAreaDevolveSoOsAlertasAtivosDaAreaDoMorador() throws Exception {
        long area = criarArea("Área Minha " + sufixoUnico());
        String email = "minhaarea" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Morador da Área", "email": "%s", "senha": "Senha@123", "areaId": %d}""".formatted(email, area))
                .andExpect(status().isCreated());
        long estacao = criarEstacao(area);
        registrarLeitura(estacao, 250, 0.0).andExpect(status().isCreated());

        mockMvc.perform(get("/api/alertas/minha-area").header(HttpHeaders.AUTHORIZATION, bearer(token(email, "Senha@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas", hasSize(1)))
                .andExpect(jsonPath("$._embedded.alertas[0].areaId").value(area))
                .andExpect(jsonPath("$._embedded.alertas[0].status").value("ATIVO"))
                .andExpect(jsonPath("$._embedded.alertas[*].areaNome", not(hasItem("Sarandi"))))
                .andExpect(jsonPath("$._links.self.href", endsWith("/api/alertas/minha-area")));
    }

    @Test
    void minhaAreaDeMoradorSemAreaDevolveListaVazia() throws Exception {
        String email = "semarea" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Morador Sem Área", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/alertas/minha-area").header(HttpHeaders.AUTHORIZATION, bearer(token(email, "Senha@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas").isEmpty());
    }

    @Test
    void minhaAreaSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/alertas/minha-area"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // o link alertas-ativos de cada área leva só aos alertas ativos daquela área; sem o areaId, a lista continua com os
    // alertas de todas as áreas, como antes
    @Test
    void linkAlertasAtivosDaAreaLevaSoAosAlertasDaquelaArea() throws Exception {
        long area = criarArea("Área do Link " + sufixoUnico());
        long vizinha = criarArea("Área Vizinha " + sufixoUnico());
        long alerta = id(registrarLeitura(criarEstacao(area), 250, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        long alertaDaVizinha = id(registrarLeitura(criarEstacao(vizinha), 310, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");

        String href = JsonPath.read(mockMvc.perform(get("/api/areas/" + area)).andReturn().getResponse().getContentAsString(),
                "$._links['alertas-ativos'].href");
        assertThat(href).endsWith("/api/alertas/ativos?areaId=" + area);
        mockMvc.perform(get(href))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas[*].id", contains((int) alerta)))
                .andExpect(jsonPath("$._embedded.alertas[*].areaId", everyItem(is((int) area))))
                .andExpect(jsonPath("$._links.self.href").value(href));

        mockMvc.perform(get("/api/alertas/ativos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas[*].id", hasItems((int) alerta, (int) alertaDaVizinha)))
                .andExpect(jsonPath("$._links.self.href", endsWith("/api/alertas/ativos")));
    }

    // área sem alerta ativo devolve a lista vazia; área que não existe dá o 404 padrão, mesmo depois de a lista dela ter
    // ido para o cache e a área ter sido excluída; id que não é positivo, ou não é número, dá 400
    @Test
    void alertasAtivosDeAreaSemAlertaInexistenteOuInvalida() throws Exception {
        long area = criarArea("Área Sem Alerta " + sufixoUnico());
        mockMvc.perform(get("/api/alertas/ativos").param("areaId", String.valueOf(area)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.alertas").isEmpty());
        mockMvc.perform(delete("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/alertas/ativos").param("areaId", String.valueOf(area)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        for (String invalido : List.of("0", "-1", "abc")) {
            mockMvc.perform(get("/api/alertas/ativos").param("areaId", invalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors[0].field").value("areaId"));
        }
    }
}
