package br.com.fiap.guardario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Estação IoT presa numa ponte ou poste, que mede o nível do rio e a chuva.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Estacao {

    // 50 mm/h ou mais é chuva muito forte: o risco sobe um nível (RN01)
    public static final double CHUVA_MUITO_FORTE_MM_H = 50.0;

    // leitura medida há mais tempo que isso entra só no histórico (ver registrarLeitura)
    public static final Duration JANELA_LEITURA_ATUAL = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bloqueio otimista: duas requisições alterando este registro ao mesmo tempo não gravam uma por cima da outra;
    // a segunda recebe 409 (OptimisticLockingFailureException no GlobalExceptionHandler)
    @Version
    private Long versao;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 80)
    private String rio;

    @Column(nullable = false)
    private Integer cotaAtencaoCm;

    @Column(nullable = false)
    private Integer cotaAlertaCm;

    @Column(nullable = false)
    private Integer cotaTransbordoCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEstacao status = StatusEstacao.ATIVA;

    // lado dono da relação: a FK area_id fica aqui
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaRisco area;

    private Integer ultimoNivelCm;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NivelRisco ultimoRisco;

    private Instant ultimaLeituraEm;

    // a leitura não existe sem a estação: cascade ALL e orphanRemoval
    @OneToMany(mappedBy = "estacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Leitura> leituras = new ArrayList<>();

    // RN01: compara o nível com as cotas deste ponto do rio e aplica o agravante de chuva
    public NivelRisco classificar(int nivelCm, double chuvaMmH) {
        NivelRisco risco;
        if (nivelCm >= cotaTransbordoCm) {
            risco = NivelRisco.EMERGENCIA;
        } else if (nivelCm >= cotaAlertaCm) {
            risco = NivelRisco.ALERTA;
        } else if (nivelCm >= cotaAtencaoCm) {
            risco = NivelRisco.ATENCAO;
        } else {
            risco = NivelRisco.NORMAL;
        }

        if (chuvaMmH >= CHUVA_MUITO_FORTE_MM_H) {
            risco = risco.elevar();
        }
        return risco;
    }

    /**
     * Liga a leitura à estação e, se ela descreve a situação de agora, atualiza o último nível.
     * Devolve true quando a leitura passou a ser a leitura atual da estação: só ela pode gerar alerta.
     */
    public boolean registrarLeitura(Leitura leitura) {
        leitura.setEstacao(this);
        leituras.add(leitura);

        // leitura atrasada (mais antiga que a última) ou medida há mais de 1 hora entra no histórico,
        // mas não muda o estado atual: o alerta precisa olhar para o rio de agora, não para uma medição velha
        boolean atrasada = ultimaLeituraEm != null && leitura.getMedidaEm().isBefore(ultimaLeituraEm);
        boolean antiga = leitura.getMedidaEm().isBefore(Instant.now().minus(JANELA_LEITURA_ATUAL));
        if (atrasada || antiga) {
            return false;
        }
        ultimoNivelCm = leitura.getNivelCm();
        ultimoRisco = leitura.getNivelRisco();
        ultimaLeituraEm = leitura.getMedidaEm();
        return true;
    }

    // a régua mudou: o último nível é classificado de novo com as cotas novas e a chuva daquela leitura (RN01)
    public void reclassificarUltimaLeitura(double chuvaMmH) {
        if (ultimoNivelCm != null) {
            ultimoRisco = classificar(ultimoNivelCm, chuvaMmH);
        }
    }

    // a leitura atual ainda descreve o rio de agora (é da última 1 hora)?
    public boolean leituraAtualEhRecente() {
        return ultimaLeituraEm != null && !ultimaLeituraEm.isBefore(Instant.now().minus(JANELA_LEITURA_ATUAL));
    }

    // estação que volta a funcionar depois de muito tempo parada: o último risco é velho e não descreve o rio de agora;
    // fica desconhecido até a próxima leitura (e a estação aparece como sem leitura)
    public void esquecerUltimaLeitura() {
        ultimoNivelCm = null;
        ultimoRisco = null;
        ultimaLeituraEm = null;
    }

    public boolean isAtiva() {
        return status == StatusEstacao.ATIVA;
    }
}
