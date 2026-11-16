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

    // a troca de senha (lenta, por causa do BCrypt) ao mesmo tempo que o ADMIN troca o papel e o próprio usuário edita
    // o perfil: cada mudança que respondeu 2xx tem que estar no estado final. Sem o @Version no Usuario, a troca de
    // senha gravava o cadastro inteiro por cima e desfazia as outras duas
    @Test
    void trocaDeSenhaAoMesmoTempoQueTrocaDePapelEEdicaoDePerfilNaoDesfazNenhuma(CapturedOutput saida) throws Exception {
        String admin = tokenAdmin();
        for (int atraso : new int[]{10, 25, 40, 60, 80, 100, 130, 160}) {
            String email = "corrida" + sufixoUnico().toLowerCase() + "@email.com";
            String location = postJson("/api/auth/register", null, """
                    {"nome": "Pessoa da Corrida", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                    .andExpect(status().isCreated()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
            String usuario = location.substring(location.lastIndexOf('/') + 1);
            mockMvc.perform(put("/api/usuarios/" + usuario + "/papel").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"papel\": \"AGENTE\"}")).andExpect(status().isOk());
            String token = token(email, "Senha@123");

            ExecutorService pool = Executors.newFixedThreadPool(3);
            Future<Integer> senha = pool.submit(() -> mockMvc.perform(put("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\": \"Senha@123\", \"novaSenha\": \"NovaSenha@456\"}"))
                    .andReturn().getResponse().getStatus());
            Future<Integer> papel = pool.submit(() -> {
                Thread.sleep(atraso);
                return mockMvc.perform(put("/api/usuarios/" + usuario + "/papel").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"papel\": \"CIDADAO\"}")).andReturn().getResponse().getStatus();
            });
            Future<Integer> perfil = pool.submit(() -> {
                Thread.sleep(atraso + 5);
                return mockMvc.perform(put("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"Nome Novo\"}")).andReturn().getResponse().getStatus();
            });
            pool.shutdown();
            int codigoSenha = senha.get(60, TimeUnit.SECONDS);
            int codigoPapel = papel.get(60, TimeUnit.SECONDS);
            int codigoPerfil = perfil.get(60, TimeUnit.SECONDS);
            assertThat(codigoSenha).as("senha, atraso %d", atraso).isIn(204, 409);
            assertThat(codigoPapel).as("papel, atraso %d", atraso).isIn(200, 409);
            assertThat(codigoPerfil).as("perfil, atraso %d", atraso).isIn(200, 401, 409);

            String cadastro = mockMvc.perform(get("/api/usuarios/" + usuario).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andReturn().getResponse().getContentAsString();
            if (codigoPapel == 200) {
                assertThat((String) JsonPath.read(cadastro, "$.papel")).as("papel perdido, atraso %d", atraso).isEqualTo("CIDADAO");
            }
            if (codigoPerfil == 200) {
                assertThat((String) JsonPath.read(cadastro, "$.nome")).as("perfil perdido, atraso %d", atraso).isEqualTo("Nome Novo");
            }
            String senhaQueVale = codigoSenha == 204 ? "NovaSenha@456" : "Senha@123";
            postJson("/api/auth/login", null, """
                    {"email": "%s", "senha": "%s"}""".formatted(email, senhaQueVale)).andExpect(status().isOk());
        }
        assertThat(saida.getOut().lines().filter(linha -> linha.contains(" WARN ") || linha.contains(" ERROR "))).isEmpty();
    }

    // áreas seguidas (N:M) ao mesmo tempo que a edição do perfil: seguir mexe só na tabela de junção e não sobe o
    // @Version, então as duas valem (sem 409); oito pedidos para seguir a mesma área deixam um par só na junção; e,
    // perto do teto, pedidos em paralelo nunca passam de 20 áreas
    @Test
    void areasSeguidasAoMesmoTempoNaoPerdemNadaNemPassamDoTeto() throws Exception {
        String admin = tokenAdmin();
        String email = "segue" + sufixoUnico().toLowerCase() + "@email.com";
        postJson("/api/auth/register", null, """
                {"nome": "Pessoa que Segue", "email": "%s", "senha": "Senha@123"}""".formatted(email))
                .andExpect(status().isCreated());
        String token = token(email, "Senha@123");
        String rota = "/api/auth/me/areas-seguidas/";

        long primeira = criarArea("Área Corrida Seguir " + sufixoUnico());
        List<Integer> comPerfil = emParalelo(2,
                () -> mockMvc.perform(put(rota + primeira).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(put("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"Nome Novo\"}"))
                        .andReturn().getResponse().getStatus());
        assertThat(comPerfil).containsExactlyInAnyOrder(204, 200);
        String me = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(me, "$.nome")).isEqualTo("Nome Novo");

        long mesma = criarArea("Área Corrida Mesma " + sufixoUnico());
        assertThat(emParalelo(8, () -> mockMvc.perform(put(rota + mesma).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getStatus())).containsOnly(204);

        List<Long> areas = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            areas.add(criarArea("Área Corrida Teto " + i + " " + sufixoUnico()));
        }
        for (int i = 0; i < 16; i++) {
            mockMvc.perform(put(rota + areas.get(i)).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNoContent());
        }
        AtomicInteger proxima = new AtomicInteger(16);
        List<Integer> perto = emParalelo(10, () -> mockMvc.perform(put(rota + areas.get(proxima.getAndIncrement()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn().getResponse().getStatus());
        assertThat(perto).containsOnly(204, 400).filteredOn(codigo -> codigo == 204).hasSize(2);

        String lista = mockMvc.perform(get("/api/auth/me/areas-seguidas").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(lista, "$._embedded.areas[*].id");
        assertThat(ids).hasSize(20).doesNotHaveDuplicates().contains((int) primeira, (int) mesma);
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
