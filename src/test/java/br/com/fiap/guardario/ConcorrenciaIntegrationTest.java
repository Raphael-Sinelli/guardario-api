package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dois agentes mexendo no mesmo registro ao mesmo tempo: só um consegue, o outro recebe 409 e vê o estado novo.
 * Sem o @Version, os dois recebiam 200 e o segundo gravava por cima do primeiro.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConcorrenciaIntegrationTest extends IntegracaoBase {

    @Test
    void doisAgentesEncerrandoOMesmoAlertaSoUmConsegue() throws Exception {
        long estacao = criarEstacao(criarArea("Área Encerrar " + sufixoUnico()));
        long alerta = id(registrarLeitura(estacao, 250, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        String agente = tokenAgente();

        List<Integer> codigos = emParalelo(5, () -> mockMvc.perform(patch("/api/alertas/" + alerta + "/encerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(agente))).andReturn().getResponse().getStatus());

        assertThat(codigos).containsOnly(200, 409);
        assertThat(codigos).filteredOn(codigo -> codigo == 200).hasSize(1);
    }

    @Test
    void atenderEDescartarOMesmoRelatoAoMesmoTempoNaoPassamOsDois() throws Exception {
        long area = criarArea("Área Relato Concorrente " + sufixoUnico());
        String agente = tokenAgente();
        for (int rodada = 0; rodada < 5; rodada++) {
            long relato = criarRelato(tokenCidadao(), area, "OUTRO");
            List<Integer> codigos = emParalelo(2,
                    () -> mudarStatus(relato, "EM_ATENDIMENTO", agente),
                    () -> mudarStatus(relato, "DESCARTADO", agente));

            assertThat(codigos).filteredOn(codigo -> codigo == 200).hasSize(1);
            String status = JsonPath.read(mockMvc.perform(get("/api/relatos/" + relato)
                    .header(HttpHeaders.AUTHORIZATION, bearer(agente))).andReturn().getResponse().getContentAsString(), "$.status");
            assertThat(status).isIn("EM_ATENDIMENTO", "DESCARTADO");
        }
    }

    // dez cadastros com o mesmo email ao mesmo tempo: um entra, os outros recebem 409 com a mensagem da regra
    @Test
    void cadastrosSimultaneosComOMesmoEmailDevolvemAMensagemDaRegra() throws Exception {
        String email = "simultaneo" + sufixoUnico().toLowerCase() + "@email.com";
        List<String> respostas = new ArrayList<>();
        List<Integer> codigos = emParalelo(10, () -> {
            var resposta = postJson("/api/auth/register", null, """
                    {"nome": "Pessoa", "email": "%s", "senha": "Senha@123"}""".formatted(email)).andReturn().getResponse();
            synchronized (respostas) {
                respostas.add(resposta.getContentAsString());
            }
            return resposta.getStatus();
        });

        assertThat(codigos).filteredOn(codigo -> codigo == 201).hasSize(1);
        assertThat(codigos).filteredOn(codigo -> codigo == 409).hasSize(9);
        assertThat(respostas).filteredOn(corpo -> corpo.contains("\"status\":409"))
                .allMatch(corpo -> corpo.contains("Já existe um cadastro com o email"));
    }

    // a violação de unicidade da corrida vira 409 na API; o log do servidor não pode ganhar um WARN por isso
    @Test
    void cadastrosSimultaneosNaoDeixamWarnNoLog(CapturedOutput saida) throws Exception {
        String email = "semaviso" + sufixoUnico().toLowerCase() + "@email.com";
        List<Integer> codigos = emParalelo(8, () -> postJson("/api/auth/register", null, """
                {"nome": "Pessoa", "email": "%s", "senha": "Senha@123"}""".formatted(email)).andReturn().getResponse().getStatus());

        assertThat(codigos).filteredOn(codigo -> codigo == 201).hasSize(1);
        assertThat(saida.getOut().lines().filter(linha -> linha.contains(" WARN ") || linha.contains(" ERROR ")))
                .noneMatch(linha -> linha.contains("org.hibernate.orm.jdbc.error"));
    }

    // o mesmo com o código da estação, o outro campo único: criar ou renomear várias estações ao mesmo tempo com o
    // mesmo código dá um sucesso e 409 com a mensagem da regra nos outros, sem WARN no log
    @Test
    void estacoesSimultaneasComOMesmoCodigoNaoDeixamWarnNoLog(CapturedOutput saida) throws Exception {
        long area = criarArea("Área Código Repetido " + sufixoUnico());
        String admin = tokenAdmin();
        String novo = "N-" + sufixoUnico();
        List<Integer> criacoes = emParalelo(8, () -> postJson("/api/estacoes", admin, """
                {"codigo": "%s", "nome": "Estação nova", "rio": "Rio Teste",
                 "cotaAtencaoCm": 100, "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "areaId": %d}"""
                .formatted(novo, area)).andReturn().getResponse().getStatus());
        assertThat(criacoes).containsOnly(201, 409).filteredOn(codigo -> codigo == 201).hasSize(1);

        List<Long> estacoes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            estacoes.add(criarEstacao(area));
        }
        String renomeado = "R-" + sufixoUnico();
        AtomicInteger proxima = new AtomicInteger();
        List<Integer> renomeacoes = emParalelo(8, () -> mockMvc.perform(put("/api/estacoes/" + estacoes.get(proxima.getAndIncrement()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo": "%s", "nome": "Estação renomeada", "rio": "Rio Teste",
                                 "cotaAtencaoCm": 100, "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "areaId": %d}"""
                                .formatted(renomeado, area)))
                .andReturn().getResponse().getStatus());
        assertThat(renomeacoes).containsOnly(200, 409).filteredOn(codigo -> codigo == 200).hasSize(1);

        assertThat(saida.getOut().lines().filter(linha -> linha.contains(" WARN ") || linha.contains(" ERROR ")))
                .noneMatch(linha -> linha.contains("Violação de integridade") || linha.contains("org.hibernate.orm.jdbc.error"));
    }

    // relatos enviados enquanto uma leitura muda o nível da área: no fim, todo relato em aberto tem a prioridade do
    // nível final. Sem a trava da área na criação, o relato lia o nível antigo e ficava com a prioridade errada
    @Test
    void relatoCriadoDuranteMudancaDeNivelFicaComAPrioridadeCerta() throws Exception {
        long area = criarArea("Área Corrida Relato " + sufixoUnico());
        long estacao = criarEstacao(area);
        String cidadao = tokenCidadao();
        String agente = tokenAgente();
        for (int rodada = 0; rodada < 6; rodada++) {
            int nivelCm = rodada % 2 == 0 ? 320 : 10;
            List<Integer> codigos = emParalelo(8,
                    () -> postJson("/api/estacoes/" + estacao + "/leituras", agente,
                            "{\"nivelCm\": " + nivelCm + ", \"chuvaMmH\": 0}").andReturn().getResponse().getStatus(),
                    () -> postJson("/api/relatos", cidadao, """
                            {"tipo": "RIO_SUBINDO", "descricao": "Relato enviado durante a cheia", "areaId": %d}"""
                            .formatted(area)).andReturn().getResponse().getStatus());
            assertThat(codigos).containsOnly(201);
        }

        // a última rodada levou a área para NORMAL: RIO_SUBINDO volta à base 3 em todos os relatos
        String painel = mockMvc.perform(get("/api/relatos").param("areaId", String.valueOf(area)).param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, bearer(agente))).andReturn().getResponse().getContentAsString();
        List<Integer> prioridades = JsonPath.read(painel, "$._embedded.relatos[*].prioridade");
        assertThat(prioridades).hasSize(42).containsOnly(3);
    }

    // o mesmo com a mudança de nível vindo do PUT da estação (desativar e reativar): a conferência é feita a cada
    // rodada, porque um relato que escapasse seria corrigido pela mudança de nível da rodada seguinte
    @Test
    void relatoCriadoDuranteMudancaDaEstacaoFicaComAPrioridadeCerta() throws Exception {
        long area = criarArea("Área Corrida Estação " + sufixoUnico());
        long estacao = criarEstacao(area);
        registrarLeitura(estacao, 320, 0.0).andExpect(status().isCreated());
        String cidadao = tokenCidadao();
        String agente = tokenAgente();
        String admin = tokenAdmin();
        for (int rodada = 0; rodada < 6; rodada++) {
            String novoStatus = rodada % 2 == 0 ? "INATIVA" : "ATIVA";
            String corpo = """
                    {"codigo": "C-%d", "nome": "Estação da corrida", "rio": "Rio Teste", "cotaAtencaoCm": 100,
                     "cotaAlertaCm": 200, "cotaTransbordoCm": 300, "areaId": %d, "status": "%s"}"""
                    .formatted(estacao, area, novoStatus);
            List<Integer> codigos = emParalelo(8,
                    () -> mockMvc.perform(put("/api/estacoes/" + estacao).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus(),
                    () -> postJson("/api/relatos", cidadao, """
                            {"tipo": "RIO_SUBINDO", "descricao": "Relato enviado durante a troca da estação", "areaId": %d}"""
                            .formatted(area)).andReturn().getResponse().getStatus());
            assertThat(codigos).containsOnly(200, 201);

            // INATIVA: a área fica NORMAL e o RIO_SUBINDO tem a base 3; ATIVA de novo: EMERGENCIA, base 3 + 1
            int esperada = novoStatus.equals("INATIVA") ? 3 : 4;
            String painel = mockMvc.perform(get("/api/relatos").param("areaId", String.valueOf(area)).param("size", "100")
                    .header(HttpHeaders.AUTHORIZATION, bearer(agente))).andReturn().getResponse().getContentAsString();
            List<Integer> prioridades = JsonPath.read(painel, "$._embedded.relatos[*].prioridade");
            assertThat(prioridades).as("rodada %d (%s)", rodada, novoStatus).hasSize(7 * (rodada + 1)).containsOnly(esperada);
        }
    }

    private int mudarStatus(long relato, String status, String token) throws Exception {
        return mockMvc.perform(patch("/api/relatos/" + relato + "/status").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // roda as tarefas ao mesmo tempo (a última se repete até completar o total) e devolve os status
    @SafeVarargs
    private List<Integer> emParalelo(int total, Callable<Integer>... tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Future<Integer>> respostas = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            respostas.add(pool.submit(tarefas[Math.min(i, tarefas.length - 1)]));
        }
        pool.shutdown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> resposta : respostas) {
            codigos.add(resposta.get(60, TimeUnit.SECONDS));
        }
        return codigos;
    }
}
