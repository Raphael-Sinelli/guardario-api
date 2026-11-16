package br.com.fiap.guardario;

import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.service.AreaSeguidaService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Áreas seguidas: o morador acompanha várias áreas além da área do cadastro, numa relação N:M entre Usuario e
 * AreaRisco, guardada na tabela de junção usuario_area_seguida.
 */
class AreaSeguidaIntegrationTest extends IntegracaoBase {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AreaRiscoRepository areaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AreaSeguidaService areaSeguidaService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // seguir uma área mexe só na tabela de junção: a versão do Usuario não sobe, para não derrubar com 409 uma edição
    // de perfil feita ao mesmo tempo (@OptimisticLock(excluded = true) na coleção)
    @Test
    void seguirAreaNaoMudaAVersaoDoUsuario() throws Exception {
        long usuario = cadastrar("versao");
        long area = criarArea("Área Seguida Versão " + sufixoUnico());
        Long versaoAntes = versao(usuario);

        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            Usuario cadastro = usuarioRepository.findById(usuario).orElseThrow();
            cadastro.getAreasSeguidas().add(areaRepository.getReferenceById(area));
        });

        assertThat(versao(usuario)).isEqualTo(versaoAntes);
        assertThat(jdbc.queryForObject("select count(*) from usuario_area_seguida where usuario_id = ? and area_id = ?",
                Integer.class, usuario, area)).isEqualTo(1);
    }

    // ---------- rotas: /api/auth/me/areas-seguidas ----------

    private static final String ROTA = "/api/auth/me/areas-seguidas";

    // seguir e deixar de seguir são idempotentes (204 nas duas vezes); a lista sai em HAL, ordenada por nome, com os
    // mesmos links do recurso de área, e vazia (com o _embedded) quando o usuário não segue nenhuma
    @Test
    void donoSegueListaEDeixaDeSeguirPelasRotas() throws Exception {
        String token = tokenDe(cadastrar("rotas"));
        String sufixo = sufixoUnico();
        long beta = criarArea("Beta Rota " + sufixo);
        long alfa = criarArea("Alfa Rota " + sufixo);

        for (long area : new long[]{beta, alfa, beta}) {
            mockMvc.perform(put(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.areas[*].id", contains((int) alfa, (int) beta)))
                .andExpect(jsonPath("$._embedded.areas[0]._links.self.href", endsWith("/api/areas/" + alfa)))
                .andExpect(jsonPath("$._embedded.areas[0]._links.alertas-ativos.href").exists())
                .andExpect(jsonPath("$._links.self.href", endsWith(ROTA)));

        for (int vez = 0; vez < 2; vez++) {
            for (long area : new long[]{alfa, beta}) {
                mockMvc.perform(delete(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isNoContent());
            }
        }
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.areas").isEmpty());
    }

    @Test
    void erros404400E401NoJsonPadrao() throws Exception {
        String token = tokenDe(cadastrar("erros"));
        for (var requisicao : List.of(put(ROTA + "/999999"), delete(ROTA + "/999999"))) {
            mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
        mockMvc.perform(put(ROTA + "/0").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        for (var requisicao : List.of(put(ROTA + "/1"), delete(ROTA + "/1"), get(ROTA))) {
            mockMvc.perform(requisicao)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void tetoPelaRotaDa400NoCampoAreasSeguidas() throws Exception {
        String token = tokenDe(cadastrar("tetorota"));
        for (int i = 0; i < Usuario.LIMITE_DE_AREAS_SEGUIDAS; i++) {
            mockMvc.perform(put(ROTA + "/" + criarArea("Área Teto Rota " + i + " " + sufixoUnico()))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNoContent());
        }
        mockMvc.perform(put(ROTA + "/" + criarArea("Área Teto Rota Extra " + sufixoUnico()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("areasSeguidas"));
    }

    // cada um só enxerga e só mexe nas próprias áreas seguidas: o DELETE de outra pessoa não tira a área de quem segue
    @Test
    void isolamentoEntreUsuarios() throws Exception {
        String maria = tokenDe(cadastrar("isolamentoa"));
        String joao = tokenDe(cadastrar("isolamentob"));
        long area = criarArea("Área Isolamento " + sufixoUnico());
        mockMvc.perform(put(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(maria))).andExpect(status().isNoContent());

        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(joao)))
                .andExpect(jsonPath("$._embedded.areas").isEmpty());
        mockMvc.perform(delete(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(joao))).andExpect(status().isNoContent());
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$._embedded.areas[*].id", contains((int) area)));
    }

    // a listagem carrega o usuário e as áreas num SELECT só: seguir 1 ou 5 áreas faz o mesmo número de consultas,
    // inclusive para o ADMIN, que tem o link excluir (uma consulta para a lista toda)
    @Test
    void listagemNaoFazUmaConsultaPorArea() throws Exception {
        List<Long> areas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            areas.add(criarArea("Área Contagem " + i + " " + sufixoUnico()));
        }
        for (String token : List.of(tokenDe(cadastrar("contagem")), tokenAdmin())) {
            mockMvc.perform(put(ROTA + "/" + areas.get(0)).header(HttpHeaders.AUTHORIZATION, bearer(token)));
            long comUma = comandosSql(token);
            for (long area : areas) {
                mockMvc.perform(put(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)));
            }
            assertThat(comandosSql(token)).isEqualTo(comUma);
            for (long area : areas) {
                mockMvc.perform(delete(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)));
            }
        }
    }

    // o /me anuncia a lista das áreas seguidas, com title. A ação fica na própria área, com URL concreta (nenhum link
    // sai com template, porque o app segue o href como está): quem não segue vê seguir-area, quem segue vê
    // deixar-de-seguir-area, cada uma com title e template HAL-FORMS. Sem login, nenhuma das duas
    @Test
    void meAnunciaAListaEAAreaAnunciaSeguirOuDeixarDeSeguir() throws Exception {
        String token = tokenDe(cadastrar("links"));
        long area = criarArea("Área Links " + sufixoUnico());
        MediaType halForms = MediaType.parseMediaType("application/prs.hal-forms+json");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$._links.areas-seguidas.href", endsWith(ROTA)))
                .andExpect(jsonPath("$._links.areas-seguidas.title").isNotEmpty())
                .andExpect(jsonPath("$._links.seguir-area").doesNotExist());

        mockMvc.perform(get("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$._links.seguir-area.href", endsWith(ROTA + "/" + area)))
                .andExpect(jsonPath("$._links.seguir-area.title").isNotEmpty())
                .andExpect(jsonPath("$._links.deixar-de-seguir-area").doesNotExist());
        mockMvc.perform(get("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(halForms))
                .andExpect(jsonPath("$._templates.seguir-area.method").value("PUT"))
                .andExpect(jsonPath("$._templates.seguir-area.target", endsWith(ROTA + "/" + area)));

        mockMvc.perform(put(ROTA + "/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(halForms))
                .andExpect(jsonPath("$._links.deixar-de-seguir-area.href", endsWith(ROTA + "/" + area)))
                .andExpect(jsonPath("$._links.deixar-de-seguir-area.title").isNotEmpty())
                .andExpect(jsonPath("$._links.seguir-area").doesNotExist())
                .andExpect(jsonPath("$._templates.deixar-de-seguir-area.method").value("DELETE"));
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$._embedded.areas[0]._links.deixar-de-seguir-area.href", endsWith(ROTA + "/" + area)));

        mockMvc.perform(get("/api/areas/" + area))
                .andExpect(jsonPath("$._links.seguir-area").doesNotExist())
                .andExpect(jsonPath("$._links.deixar-de-seguir-area").doesNotExist());
    }

    // a lista de áreas paginada também diz, item por item, se o usuário segue: uma consulta por página, não por item
    @Test
    void listaDeAreasNaoFazUmaConsultaPorItemParaSaberQuemSegue() throws Exception {
        String token = tokenDe(cadastrar("pagina"));
        assertThat(comandosSqlEm("/api/areas?size=3", token)).isEqualTo(comandosSqlEm("/api/areas?size=1", token));
    }

    private long comandosSqlEm(String rota, String token) throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    private long comandosSql(String token) throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    private String tokenDe(long usuario) throws Exception {
        String email = jdbc.queryForObject("select email from usuario where id = ?", String.class, usuario);
        return token(email, "Senha@123");
    }

    // ---------- serviço (chamado como o usuário logado, como faz o filtro JWT) ----------

    @Test
    void seguirEDeixarDeSeguirSaoIdempotentes() throws Exception {
        long usuario = cadastrar("idempotente");
        long area = criarArea("Área Idempotente " + sufixoUnico());

        como(usuario, () -> areaSeguidaService.seguir(area));
        como(usuario, () -> areaSeguidaService.seguir(area));
        assertThat(seguidas(usuario)).isEqualTo(1);

        como(usuario, () -> areaSeguidaService.deixarDeSeguir(area));
        como(usuario, () -> areaSeguidaService.deixarDeSeguir(area));
        assertThat(seguidas(usuario)).isZero();
    }

    @Test
    void areaQueNaoExisteDa404AoSeguirEAoDeixarDeSeguir() throws Exception {
        long usuario = cadastrar("inexistente");
        assertThatThrownBy(() -> como(usuario, () -> areaSeguidaService.seguir(999999L)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> como(usuario, () -> areaSeguidaService.deixarDeSeguir(999999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // o teto é de 20 áreas: a 21ª dá 400 no campo areasSeguidas, mas seguir de novo uma que já segue continua valendo
    @Test
    void tetoDeAreasSeguidas() throws Exception {
        long usuario = cadastrar("teto");
        List<Long> areas = new ArrayList<>();
        for (int i = 0; i <= Usuario.LIMITE_DE_AREAS_SEGUIDAS; i++) {
            areas.add(criarArea("Área Teto " + i + " " + sufixoUnico()));
        }
        for (int i = 0; i < Usuario.LIMITE_DE_AREAS_SEGUIDAS; i++) {
            long area = areas.get(i);
            como(usuario, () -> areaSeguidaService.seguir(area));
        }
        long excedente = areas.get(Usuario.LIMITE_DE_AREAS_SEGUIDAS);
        assertThatThrownBy(() -> como(usuario, () -> areaSeguidaService.seguir(excedente)))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessageContaining(String.valueOf(Usuario.LIMITE_DE_AREAS_SEGUIDAS));
        como(usuario, () -> areaSeguidaService.seguir(areas.get(0)));
        assertThat(seguidas(usuario)).isEqualTo(Usuario.LIMITE_DE_AREAS_SEGUIDAS);
    }

    @Test
    void listaOrdenadaPorNomeEVaziaQuemNaoSegue() throws Exception {
        long usuario = cadastrar("lista");
        String sufixo = sufixoUnico();
        long zeta = criarArea("Zeta Seguida " + sufixo);
        long alfa = criarArea("Alfa Seguida " + sufixo);
        long beta = criarArea("Beta Seguida " + sufixo);
        assertThat(como(usuario, () -> areaSeguidaService.listar())).isEmpty();

        for (long area : new long[]{zeta, alfa, beta}) {
            como(usuario, () -> areaSeguidaService.seguir(area));
        }
        assertThat(como(usuario, () -> areaSeguidaService.listar())).extracting(AreaResponse::id).containsExactly(alfa, beta, zeta);
    }

    @Test
    void seguirPeloServicoNaoMudaAVersaoDoUsuario() throws Exception {
        long usuario = cadastrar("versaoservico");
        long area = criarArea("Área Versão Serviço " + sufixoUnico());
        Long versaoAntes = versao(usuario);
        como(usuario, () -> areaSeguidaService.seguir(area));
        assertThat(versao(usuario)).isEqualTo(versaoAntes);
    }

    // seguir não é vínculo como estação ou morador: o ADMIN exclui a área seguida, e ela some da lista de quem seguia
    @Test
    void areaSeguidaPodeSerExcluidaESaiDaListaDeQuemSeguia() throws Exception {
        long usuario = cadastrar("excluida");
        long area = criarArea("Área Seguida Excluída " + sufixoUnico());
        como(usuario, () -> areaSeguidaService.seguir(area));

        mockMvc.perform(delete("/api/areas/" + area).header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin())))
                .andExpect(status().isNoContent());
        assertThat(seguidas(usuario)).isZero();
        assertThat(como(usuario, () -> areaSeguidaService.listar())).isEmpty();
    }

    private int seguidas(long usuario) {
        return jdbc.queryForObject("select count(*) from usuario_area_seguida where usuario_id = ?", Integer.class, usuario);
    }

    private void como(long usuario, Runnable acao) {
        como(usuario, () -> {
            acao.run();
            return null;
        });
    }

    private <T> T como(long usuario, Supplier<T> acao) {
        Usuario cadastro = usuarioRepository.findById(usuario).orElseThrow();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(cadastro, null, cadastro.getAuthorities()));
        try {
            return acao.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long versao(long usuario) {
        return jdbc.queryForObject("select versao from usuario where id = ?", Long.class, usuario);
    }

    private long cadastrar(String prefixo) throws Exception {
        String location = postJson("/api/auth/register", null, """
                {"nome": "Pessoa que Segue", "email": "%s%s@email.com", "senha": "Senha@123"}"""
                .formatted(prefixo, sufixoUnico().toLowerCase()))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
