package br.com.fiap.guardario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * Dois agentes mexendo no mesmo registro ao mesmo tempo: só um consegue, o outro recebe 409 e vê o estado novo.
 * Sem o @Version, os dois recebiam 200 e o segundo gravava por cima do primeiro.
 */
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
