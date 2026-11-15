package br.com.fiap.guardario;

import br.com.fiap.guardario.dto.AreaResponse;
import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.service.AreaSeguidaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
