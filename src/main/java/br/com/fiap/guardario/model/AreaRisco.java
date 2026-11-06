package br.com.fiap.guardario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Bairro ou trecho de cidade monitorado (ex.: Jardim Pantanal, em São Paulo).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AreaRisco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 80)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    private Integer populacaoEstimada;

    private Double latitude;

    private Double longitude;

    // calculado pelo servidor a partir das leituras (RN03), nunca vem do cliente
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelRisco nivelAtual = NivelRisco.NORMAL;

    // lado inverso, sem cascade: a estação tem vida própria e não pode sumir junto com a área
    @OneToMany(mappedBy = "area", fetch = FetchType.LAZY)
    private List<Estacao> estacoes = new ArrayList<>();

    // RN03: o nível da área é o maior risco entre as últimas leituras das suas estações
    public NivelRisco recalcularNivel() {
        nivelAtual = estacoes.stream()
                .map(Estacao::getUltimoRisco)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(NivelRisco::getPeso))
                .orElse(NivelRisco.NORMAL);
        return nivelAtual;
    }
}
