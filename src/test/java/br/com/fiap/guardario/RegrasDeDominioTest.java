package br.com.fiap.guardario;

import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de unidade das regras que moram nas entidades e nos enums (sem Spring, sem banco).
 */
class RegrasDeDominioTest {

    private Estacao estacaoComCotas(int atencao, int alerta, int transbordo) {
        Estacao estacao = new Estacao();
        estacao.setCotaAtencaoCm(atencao);
        estacao.setCotaAlertaCm(alerta);
        estacao.setCotaTransbordoCm(transbordo);
        return estacao;
    }

    // RN01: régua de risco nas fronteiras de cada cota, com e sem o agravante de chuva
    @ParameterizedTest(name = "nível {0} cm e chuva {1} mm/h -> {2}")
    @CsvSource({
            "99, 0, NORMAL",
            "100, 0, ATENCAO",
            "199, 49.9, ATENCAO",
            "200, 0, ALERTA",
            "300, 0, EMERGENCIA",
            "50, 50, ATENCAO",
            "150, 50, ALERTA",
            "250, 80, EMERGENCIA",
            "350, 120, EMERGENCIA"
    })
    void classificaPelaReguaDaEstacao(int nivel, double chuva, NivelRisco esperado) {
        assertThat(estacaoComCotas(100, 200, 300).classificar(nivel, chuva)).isEqualTo(esperado);
    }

    // datas relativas a agora: com datas fixas, o teste quebraria quando elas ficassem mais velhas que a janela de 1 hora
    @Test
    void leituraAtrasadaNaoMudaOUltimoNivelDaEstacao() {
        Estacao estacao = estacaoComCotas(100, 200, 300);
        java.time.Instant agora = java.time.Instant.now();
        Leitura recente = leitura(250, NivelRisco.ALERTA, agora.minusSeconds(60));
        Leitura atrasada = leitura(80, NivelRisco.NORMAL, agora.minusSeconds(120));

        assertThat(estacao.registrarLeitura(recente)).isTrue();
        assertThat(estacao.registrarLeitura(atrasada)).isFalse();
        assertThat(estacao.getUltimoNivelCm()).isEqualTo(250);
        assertThat(estacao.getUltimoRisco()).isEqualTo(NivelRisco.ALERTA);
        assertThat(estacao.getLeituras()).hasSize(2);
    }

    // leitura medida há mais de 1 hora (estação que ficou sem internet, ou relógio errado) não descreve a situação de agora
    @Test
    void leituraMedidaHaMaisDeUmaHoraSoEntraNoHistorico() {
        Estacao estacao = estacaoComCotas(100, 200, 300);
        Leitura antiga = leitura(290, NivelRisco.ALERTA, java.time.Instant.now().minusSeconds(2 * 3600));

        assertThat(estacao.registrarLeitura(antiga)).isFalse();
        assertThat(estacao.getUltimoRisco()).isNull();
        assertThat(estacao.getUltimaLeituraEm()).isNull();
        assertThat(estacao.getLeituras()).hasSize(1);
    }

    // RN03: estação INATIVA ou em MANUTENCAO não mede mais, então o último risco dela não segura o nível da área
    @Test
    void nivelDaAreaConsideraSoEstacoesAtivas() {
        Estacao ativa = estacaoComCotas(100, 200, 300);
        ativa.setUltimoRisco(NivelRisco.ATENCAO);
        Estacao inativa = estacaoComCotas(100, 200, 300);
        inativa.setStatus(StatusEstacao.INATIVA);
        inativa.setUltimoRisco(NivelRisco.EMERGENCIA);
        AreaRisco area = new AreaRisco();
        area.getEstacoes().add(ativa);
        area.getEstacoes().add(inativa);

        assertThat(area.recalcularNivel()).isEqualTo(NivelRisco.ATENCAO);
    }

    // RN05
    @Test
    void abrigoViraLotadoQuandoEncheEVoltaAAbertoQuandoLiberaVaga() {
        Abrigo abrigo = new Abrigo();
        abrigo.setCapacidade(10);
        abrigo.setOcupacao(0);

        abrigo.registrarEntrada(10);
        assertThat(abrigo.getStatus()).isEqualTo(StatusAbrigo.LOTADO);
        assertThatThrownBy(() -> abrigo.registrarEntrada(1)).isInstanceOf(RegraNegocioException.class);

        abrigo.registrarSaida(3);
        assertThat(abrigo.getStatus()).isEqualTo(StatusAbrigo.ABERTO);
        assertThat(abrigo.vagasDisponiveis()).isEqualTo(3);
        assertThatThrownBy(() -> abrigo.registrarSaida(8)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void abrigoFechadoNaoRecebeNinguem() {
        Abrigo abrigo = new Abrigo();
        abrigo.setCapacidade(10);
        abrigo.fechar();
        assertThatThrownBy(() -> abrigo.registrarEntrada(1))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("fechado");
        // o app não pode mostrar vaga num abrigo que não recebe ninguém
        assertThat(abrigo.vagasDisponiveis()).isZero();
    }

    // RN06
    @Test
    void prioridadeDoRelatoSobeComAreaEmAlertaEParaNoTeto() {
        Relato bueiro = new Relato();
        bueiro.setTipo(TipoRelato.BUEIRO_ENTUPIDO);
        bueiro.definirPrioridade(NivelRisco.NORMAL);
        assertThat(bueiro.getPrioridade()).isEqualTo(2);
        bueiro.definirPrioridade(NivelRisco.ALERTA);
        assertThat(bueiro.getPrioridade()).isEqualTo(3);

        Relato ilhada = new Relato();
        ilhada.setTipo(TipoRelato.PESSOA_ILHADA);
        ilhada.definirPrioridade(NivelRisco.EMERGENCIA);
        assertThat(ilhada.getPrioridade()).isEqualTo(Relato.PRIORIDADE_MAXIMA);
    }

    // RN07
    @Test
    void maquinaDeEstadosDoRelato() {
        assertThat(StatusRelato.ABERTO.podeMudarPara(StatusRelato.EM_ATENDIMENTO)).isTrue();
        assertThat(StatusRelato.ABERTO.podeMudarPara(StatusRelato.DESCARTADO)).isTrue();
        assertThat(StatusRelato.ABERTO.podeMudarPara(StatusRelato.RESOLVIDO)).isFalse();
        assertThat(StatusRelato.EM_ATENDIMENTO.podeMudarPara(StatusRelato.RESOLVIDO)).isTrue();
        assertThat(StatusRelato.EM_ATENDIMENTO.podeMudarPara(StatusRelato.ABERTO)).isFalse();
        assertThat(StatusRelato.RESOLVIDO.podeMudarPara(StatusRelato.ABERTO)).isFalse();
        assertThat(StatusRelato.DESCARTADO.podeMudarPara(StatusRelato.EM_ATENDIMENTO)).isFalse();
    }

    // RN04
    @Test
    void alertaEncerradoNaoReabre() {
        Alerta alerta = new Alerta();
        alerta.encerrar(null);
        assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ENCERRADO);
        assertThatThrownBy(() -> alerta.encerrar(null)).isInstanceOf(RegraNegocioException.class);
    }

    private Leitura leitura(int nivel, NivelRisco risco, java.time.Instant medidaEm) {
        Leitura leitura = new Leitura();
        leitura.setNivelCm(nivel);
        leitura.setNivelRisco(risco);
        leitura.setMedidaEm(medidaEm);
        return leitura;
    }
}
