package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.LeituraRequest;
import br.com.fiap.guardario.model.*;
import br.com.fiap.guardario.repository.*;
import br.com.fiap.guardario.service.LeituraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Dados de demonstração. Os bairros são reais e têm histórico de alagamento; nomes, números e relatos são fictícios.
 * Liga com app.seed.enabled=true (padrão no perfil dev) e só roda com o banco vazio.
 * As senhas abaixo são de teste, documentadas no README; em produção nada disso é criado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final AreaRiscoRepository areaRepository;
    private final EstacaoRepository estacaoRepository;
    private final AbrigoRepository abrigoRepository;
    private final RelatoRepository relatoRepository;
    private final LeituraService leituraService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        AreaRisco pantanal = area("Jardim Pantanal", "São Paulo", "SP", 48000, -23.4985, -46.4290);
        AreaRisco romano = area("Jardim Romano", "São Paulo", "SP", 35000, -23.4995, -46.3990);
        AreaRisco itaim = area("Vila Itaim", "São Paulo", "SP", 22000, -23.4970, -46.4135);
        AreaRisco sarandi = area("Sarandi", "Porto Alegre", "RS", 91000, -29.9890, -51.1270);

        usuario("Administrador GuardaRio", "admin@guardario.com", "Admin@123", Papel.ADMIN, null);
        usuario("Carlos Souza (Defesa Civil)", "agente@guardario.com", "Agente@123", Papel.AGENTE, null);
        Usuario cida = usuario("Aparecida Ferreira", "cidadao@guardario.com", "Cidadao@123", Papel.CIDADAO, pantanal);
        Usuario maria = usuario("Maria Lima", "maria@guardario.com", "Maria@123", Papel.CIDADAO, sarandi);

        Estacao sp001 = estacao("GR-SP-001", "Ponte da Rua Capachós", "Rio Tietê", 150, 220, 300, pantanal);
        Estacao sp002 = estacao("GR-SP-002", "Travessia do Córrego Três Pontes", "Córrego Três Pontes", 120, 180, 240, romano);
        Estacao sp003 = estacao("GR-SP-003", "Margem da Vila Itaim", "Rio Tietê", 160, 230, 310, itaim);
        Estacao rs001 = estacao("GR-RS-001", "Ponte da Avenida Assis Brasil", "Rio Gravataí", 200, 300, 400, sarandi);

        // as leituras passam pelo service: classificam o risco, atualizam a área e geram alerta quando precisa
        leituras(sp001, 120, 138, 152, 171);
        leituras(sp002, 70, 85, 92);
        leituras(sp003, 140, 150);
        leituras(rs001, 250, 290, 318); // passa da cota de alerta: nasce um alerta automático em Sarandi

        abrigo("EMEF Jardim Pantanal", "Rua Capachós, 120", 120, 45, true, true, pantanal);
        abrigo("Igreja São Miguel Arcanjo", "Rua Erva Imperial, 300", 60, 60, true, false, pantanal);
        abrigo("CEU Três Pontes", "Rua Capão do Embira, 1000", 200, 80, false, true, romano);
        abrigo("Ginásio Municipal do Sarandi", "Rua Paulino Chaves, 500", 300, 180, true, true, sarandi);
        Abrigo itaimEscola = abrigo("E.E. Vila Itaim", "Rua Pedro Gonçalves, 80", 90, 0, false, true, itaim);
        itaimEscola.fechar();

        relato(cida, pantanal, TipoRelato.RIO_SUBINDO,
                "A água já passou da segunda marca do pilar da ponte da Rua Capachós.");
        relato(cida, romano, TipoRelato.BUEIRO_ENTUPIDO,
                "Bueiro da esquina da Rua Três Pontes entupido, a água já cobre a calçada.");
        relato(maria, sarandi, TipoRelato.PESSOA_ILHADA,
                "Família com uma criança e um idoso no segundo andar, a escada está tomada pela água.");
        Relato emAtendimento = relato(maria, sarandi, TipoRelato.ALAGAMENTO_VIA,
                "Avenida Assis Brasil alagada perto do viaduto, carros parados na pista.");
        emAtendimento.alterarStatus(StatusRelato.EM_ATENDIMENTO, "Equipe de trânsito enviada para desviar o fluxo.");
        relato(cida, itaim, TipoRelato.OUTRO,
                "Fio de poste caído perto do campo de futebol da Vila Itaim.");

        log.info("Dados de demonstração criados: 4 áreas, 4 estações, 5 abrigos, 5 relatos e 4 usuários de teste");
    }

    private AreaRisco area(String nome, String cidade, String uf, int populacao, double lat, double lon) {
        AreaRisco area = new AreaRisco();
        area.setNome(nome);
        area.setCidade(cidade);
        area.setUf(uf);
        area.setPopulacaoEstimada(populacao);
        area.setLatitude(lat);
        area.setLongitude(lon);
        return areaRepository.save(area);
    }

    private Usuario usuario(String nome, String email, String senha, Papel papel, AreaRisco area) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setPapel(papel);
        usuario.setArea(area);
        return usuarioRepository.save(usuario);
    }

    private Estacao estacao(String codigo, String nome, String rio, int atencao, int alerta, int transbordo, AreaRisco area) {
        Estacao estacao = new Estacao();
        estacao.setCodigo(codigo);
        estacao.setNome(nome);
        estacao.setRio(rio);
        estacao.setCotaAtencaoCm(atencao);
        estacao.setCotaAlertaCm(alerta);
        estacao.setCotaTransbordoCm(transbordo);
        estacao.setArea(area);
        Estacao salva = estacaoRepository.save(estacao);
        area.getEstacoes().add(salva); // mantém os dois lados da relação em memória
        return salva;
    }

    // uma leitura a cada hora, terminando há 10 minutos
    private void leituras(Estacao estacao, int... niveis) {
        Instant inicio = Instant.now().minus(10, ChronoUnit.MINUTES).minus(niveis.length - 1, ChronoUnit.HOURS);
        for (int i = 0; i < niveis.length; i++) {
            Instant medidaEm = inicio.plus(i, ChronoUnit.HOURS);
            leituraService.registrar(estacao.getId(), new LeituraRequest(niveis[i], 8.0 + i * 4, 23.5, 88.0, medidaEm));
        }
    }

    private Abrigo abrigo(String nome, String endereco, int capacidade, int ocupacao, boolean pets, boolean pcd, AreaRisco area) {
        Abrigo abrigo = new Abrigo();
        abrigo.setNome(nome);
        abrigo.setEndereco(endereco);
        abrigo.setCapacidade(capacidade);
        abrigo.setOcupacao(ocupacao);
        abrigo.setAceitaPets(pets);
        abrigo.setAcessivelPcd(pcd);
        abrigo.setArea(area);
        abrigo.atualizarStatus();
        return abrigoRepository.save(abrigo);
    }

    private Relato relato(Usuario autor, AreaRisco area, TipoRelato tipo, String descricao) {
        Relato relato = new Relato();
        relato.setAutor(autor);
        relato.setArea(area);
        relato.setTipo(tipo);
        relato.setDescricao(descricao);
        relato.definirPrioridade(area.getNivelAtual());
        return relatoRepository.save(relato);
    }
}
