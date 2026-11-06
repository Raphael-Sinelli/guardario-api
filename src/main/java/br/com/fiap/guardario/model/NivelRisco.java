package br.com.fiap.guardario.model;

/**
 * Régua de risco do GuardaRio (RN01). É a mesma usada no ESP32 e no PL/SQL.
 * O peso é explícito para a regra não depender da ordem de declaração (ordinal).
 */
public enum NivelRisco {

    NORMAL(0, "Sem risco no momento."),
    ATENCAO(1, "Fique atento aos avisos e deixe o kit de emergência pronto."),
    ALERTA(2, "Separe documentos e remédios e esteja pronto para sair. Veja no app os abrigos com vaga."),
    EMERGENCIA(3, "Saia agora para o abrigo mais próximo ou para um ponto alto. Não atravesse áreas alagadas.");

    private final int peso;
    private final String orientacao;

    NivelRisco(int peso, String orientacao) {
        this.peso = peso;
        this.orientacao = orientacao;
    }

    public int getPeso() {
        return peso;
    }

    public String getOrientacao() {
        return orientacao;
    }

    public boolean maiorOuIgualA(NivelRisco outro) {
        return this.peso >= outro.peso;
    }

    // agravante de chuva: sobe um nível, com teto em EMERGENCIA
    public NivelRisco elevar() {
        return switch (this) {
            case NORMAL -> ATENCAO;
            case ATENCAO -> ALERTA;
            case ALERTA, EMERGENCIA -> EMERGENCIA;
        };
    }
}
