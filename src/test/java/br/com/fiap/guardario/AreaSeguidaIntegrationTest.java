package br.com.fiap.guardario;

import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
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
