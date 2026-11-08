package br.com.fiap.guardario.model;

import br.com.fiap.guardario.exceptions.RegraNegocioException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bloqueio otimista: duas requisições alterando este registro ao mesmo tempo não gravam uma por cima da outra;
    // a segunda recebe 409 (OptimisticLockingFailureException no GlobalExceptionHandler)
    @Version
    private Long versao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelRisco nivel;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAlerta status = StatusAlerta.ATIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemAlerta origem;

    @Column(nullable = false, updatable = false)
    private Instant emitidoEm;

    private Instant encerradoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaRisco area;

    // estação cuja leitura gerou o alerta (nulo em alerta manual sem estação)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estacao_id")
    private Estacao estacao;

    // nulo quando o alerta é automático
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emitido_por_id")
    private Usuario emitidoPor;

    // nulo quando o alerta foi substituído por outro de nível maior
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encerrado_por_id")
    private Usuario encerradoPor;

    @PrePersist
    void aoEmitir() {
        if (emitidoEm == null) {
            emitidoEm = Horario.agora();
        }
    }

    public boolean isAtivo() {
        return status == StatusAlerta.ATIVO;
    }

    // RN04: alerta encerrado não reabre
    public void encerrar(Usuario responsavel) {
        if (!isAtivo()) {
            throw new RegraNegocioException("Este alerta já foi encerrado e não pode ser alterado");
        }
        status = StatusAlerta.ENCERRADO;
        encerradoEm = Horario.agora();
        encerradoPor = responsavel;
    }
}
