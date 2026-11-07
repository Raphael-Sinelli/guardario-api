package br.com.fiap.guardario.model;

import br.com.fiap.guardario.exceptions.RegraNegocioException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Escola, ginásio ou igreja que recebe famílias desalojadas.
 * A ocupação nunca passa da capacidade (RN05).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Abrigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 200)
    private String endereco;

    @Column(nullable = false)
    private Integer capacidade;

    @Column(nullable = false)
    private Integer ocupacao = 0;

    // bloqueio otimista: se dois agentes editam o mesmo abrigo ao mesmo tempo (PUT ou status), o segundo UPDATE
    // encontra a versão já mudada e falha (409), em vez de sobrescrever o que o primeiro gravou. As entradas e saídas
    // não chegam a esse 409: travam a linha do abrigo (AbrigoRepository.buscarComTrava) e entram uma de cada vez
    @Version
    private Long versao;

    @Column(nullable = false)
    private Boolean aceitaPets;

    @Column(nullable = false)
    private Boolean acessivelPcd;

    @Column(length = 20)
    private String telefone;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAbrigo status = StatusAbrigo.ABERTO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaRisco area;

    // abrigo fechado não recebe ninguém: para o app, ele tem 0 vaga, mesmo com espaço sobrando
    public int vagasDisponiveis() {
        if (status == StatusAbrigo.FECHADO) {
            return 0;
        }
        return Math.max(capacidade - ocupacao, 0);
    }

    public void registrarEntrada(int pessoas) {
        if (status == StatusAbrigo.FECHADO) {
            throw new RegraNegocioException("Abrigo fechado não recebe pessoas");
        }
        if (ocupacao + pessoas > capacidade) {
            throw new RegraNegocioException("A entrada de " + pessoas + " pessoa(s) passa da capacidade do abrigo. Vagas disponíveis: " + vagasDisponiveis());
        }
        ocupacao += pessoas;
        atualizarStatus();
    }

    public void registrarSaida(int pessoas) {
        if (pessoas > ocupacao) {
            throw new RegraNegocioException("A saída de " + pessoas + " pessoa(s) é maior que a ocupação atual (" + ocupacao + ")");
        }
        ocupacao -= pessoas;
        atualizarStatus();
    }

    public void fechar() {
        status = StatusAbrigo.FECHADO;
    }

    public void reabrir() {
        status = StatusAbrigo.ABERTO;
        atualizarStatus();
    }

    // LOTADO e ABERTO são calculados pela ocupação; FECHADO só muda por decisão de quem gerencia
    public void atualizarStatus() {
        if (status == StatusAbrigo.FECHADO) {
            return;
        }
        status = ocupacao >= capacidade ? StatusAbrigo.LOTADO : StatusAbrigo.ABERTO;
    }
}
