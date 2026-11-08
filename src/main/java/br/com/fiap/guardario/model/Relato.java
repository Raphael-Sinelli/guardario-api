package br.com.fiap.guardario.model;

import br.com.fiap.guardario.exceptions.RegraNegocioException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Relato enviado pelo morador: rio subindo, rua alagada, pessoa ilhada...
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Relato {

    public static final int PRIORIDADE_MAXIMA = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bloqueio otimista: duas requisições alterando este registro ao mesmo tempo não gravam uma por cima da outra;
    // a segunda recebe 409 (OptimisticLockingFailureException no GlobalExceptionHandler)
    @Version
    private Long versao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoRelato tipo;

    @Column(nullable = false, length = 500)
    private String descricao;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private Integer prioridade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRelato status = StatusRelato.ABERTO;

    @Column(length = 500)
    private String observacaoAgente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaRisco area;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Horario.agora();
        atualizadoEm = criadoEm;
    }

    // RN06: prioridade base do tipo, +1 se a área estiver em ALERTA ou EMERGENCIA, com teto 5
    public void definirPrioridade(NivelRisco nivelDaArea) {
        int valor = tipo.getPrioridadeBase();
        if (nivelDaArea.maiorOuIgualA(NivelRisco.ALERTA)) {
            valor++;
        }
        prioridade = Math.min(valor, PRIORIDADE_MAXIMA);
    }

    // RN07: só as transições previstas em StatusRelato são aceitas
    public void alterarStatus(StatusRelato novo, String observacao) {
        if (!status.podeMudarPara(novo)) {
            throw new RegraNegocioException("Transição de status inválida: " + status + " não pode ir para " + novo);
        }
        status = novo;
        if (observacao != null && !observacao.isBlank()) {
            observacaoAgente = observacao.trim();
        }
        atualizadoEm = Horario.agora();
    }

    public void marcarAtualizado() {
        atualizadoEm = Horario.agora();
    }

    public boolean isAberto() {
        return status == StatusRelato.ABERTO;
    }

    public boolean pertenceA(Long usuarioId) {
        return autor.getId().equals(usuarioId);
    }
}
