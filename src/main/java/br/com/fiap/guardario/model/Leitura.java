package br.com.fiap.guardario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(indexes = @Index(name = "idx_leitura_estacao_medida", columnList = "estacao_id, medida_em"))
@Getter
@Setter
@NoArgsConstructor
public class Leitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer nivelCm;

    @Column(nullable = false)
    private Double chuvaMmH;

    private Double temperaturaC;

    private Double umidadePct;

    // calculado pela régua da estação no momento do registro
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelRisco nivelRisco;

    // sempre em UTC
    @Column(nullable = false)
    private Instant medidaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estacao_id", nullable = false)
    private Estacao estacao;

    // o Java mede o horário em frações de 100 ns e o banco guarda microssegundos, arredondando. Sem o corte, a leitura
    // voltava do banco até 1 µs mais tarde, e a próxima, no mesmo instante, parecia atrasada e não virava a leitura atual
    public void setMedidaEm(Instant medidaEm) {
        this.medidaEm = medidaEm == null ? null : medidaEm.truncatedTo(ChronoUnit.MICROS);
    }
}
