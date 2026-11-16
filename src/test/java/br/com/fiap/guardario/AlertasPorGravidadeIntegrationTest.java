package br.com.fiap.guardario;

import br.com.fiap.guardario.model.NivelRisco;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A lista de alertas ativos sai do mais grave para o menos grave. O nível é gravado como texto (EnumType.STRING), e a
 * ordem alfabética não é a de gravidade: o ORDER BY CASE do repositório dá a cada nível o peso do NivelRisco, e o banco
 * entrega a lista já na ordem, sem o service reordenar em memória.
 */
class AlertasPorGravidadeIntegrationTest extends IntegracaoBase {

    // a ordem que o service fazia em memória: peso do nível, do maior para o menor, e depois o alerta mais recente
    private static final Comparator<Map<String, Object>> MAIS_GRAVE_PRIMEIRO = Comparator
            .comparingInt((Map<String, Object> alerta) -> NivelRisco.valueOf((String) alerta.get("nivel")).getPeso()).reversed()
            .thenComparing((Map<String, Object> alerta) -> Instant.parse((String) alerta.get("emitidoEm")), Comparator.reverseOrder());

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CacheManager cacheManager;

    // as consultas da lista geral, da lista de uma área e da área do morador ordenam no banco
    @Test
    void ordemPorGravidadeSaiDoBanco() throws Exception {
        long area = criarArea("Área Ordem no Banco " + sufixoUnico());
        registrarLeitura(criarEstacao(area), 250, 0.0).andExpect(status().isCreated());
        cache().clear();
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        mockMvc.perform(get("/api/alertas/ativos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alertas/ativos").param("areaId", String.valueOf(area))).andExpect(status().isOk());

        List<String> consultasDeAlerta = List.of(estatisticas.getQueries()).stream()
                .filter(consulta -> consulta.toLowerCase().contains("from alerta "))
                .toList();
        assertThat(consultasDeAlerta).hasSize(2)
                .allSatisfy(consulta -> assertThat(consulta.toLowerCase().replaceAll("\\s+", " ")).contains("order by case a.nivel"));
    }

    // a ordem é a mesma que o service fazia em memória, com os níveis fora de ordem na emissão: ATENCAO, EMERGENCIA,
    // ALERTA e de novo ATENCAO, cada um numa área, somados aos alertas que a carga de demonstração e os outros testes
    // deixaram ativos
    @Test
    void ordemDaListaEhAMesmaDaOrdenacaoEmMemoria() throws Exception {
        long atencaoAntigo = alertaManual("ATENCAO");
        long emergencia = alertaManual("EMERGENCIA");
        long alerta = alertaManual("ALERTA");
        long atencaoNovo = alertaManual("ATENCAO");

        List<Map<String, Object>> lista = JsonPath.read(mockMvc.perform(get("/api/alertas/ativos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$._embedded.alertas");

        assertThat(lista).isSortedAccordingTo(MAIS_GRAVE_PRIMEIRO);
        List<Long> ids = lista.stream().map(item -> ((Number) item.get("id")).longValue()).toList();
        assertThat(ids).containsSubsequence(emergencia, alerta, atencaoNovo, atencaoAntigo);
    }

    // a lista geral e a de cada área ficam em cache; um alerta novo limpa as duas, e a consulta seguinte volta ao banco
    // já com ele na posição certa
    @Test
    void alertaNovoLimpaOCacheDaListaGeralEDaListaDaArea() throws Exception {
        long area = criarArea("Área Cache por Gravidade " + sufixoUnico());
        long estacao = criarEstacao(area);
        long alerta = id(registrarLeitura(estacao, 250, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");

        mockMvc.perform(get("/api/alertas/ativos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/alertas/ativos").param("areaId", String.valueOf(area))).andExpect(status().isOk());
        assertThat(cache().get(SimpleKey.EMPTY)).isNotNull();
        assertThat(cache().get(area)).isNotNull();

        long emergencia = id(registrarLeitura(estacao, 310, 0.0).andReturn().getResponse().getContentAsString(), "$.alertaGeradoId");
        assertThat(cache().get(SimpleKey.EMPTY)).isNull();
        assertThat(cache().get(area)).isNull();

        List<Number> daArea = JsonPath.read(mockMvc.perform(get("/api/alertas/ativos").param("areaId", String.valueOf(area)))
                .andReturn().getResponse().getContentAsString(), "$._embedded.alertas[*].id");
        assertThat(daArea).extracting(Number::longValue).containsExactly(emergencia);
        List<Map<String, Object>> todos = JsonPath.read(mockMvc.perform(get("/api/alertas/ativos"))
                .andReturn().getResponse().getContentAsString(), "$._embedded.alertas");
        assertThat(todos).isSortedAccordingTo(MAIS_GRAVE_PRIMEIRO);
        assertThat(todos).extracting(item -> ((Number) item.get("id")).longValue()).contains(emergencia).doesNotContain(alerta);
    }

    // alerta manual do AGENTE, cada um numa área nova: a leitura só abre alerta automático a partir de ALERTA
    private long alertaManual(String nivel) throws Exception {
        long area = criarArea("Área Gravidade " + sufixoUnico());
        return id(postJson("/api/alertas", tokenAgente(), """
                {"areaId": %d, "nivel": "%s", "titulo": "Alerta %s de teste",
                 "mensagem": "Mensagem do alerta de teste para conferir a ordem por gravidade."}"""
                .formatted(area, nivel, nivel))
                .andExpect(status().isCreated()));
    }

    private Cache cache() {
        return cacheManager.getCache("alertasAtivos");
    }
}
