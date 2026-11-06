package br.com.fiap.guardario.model;

/**
 * Tipos de relato do morador com a prioridade base de cada um (RN06).
 */
public enum TipoRelato {

    PESSOA_ILHADA(5),
    DESLIZAMENTO(4),
    RIO_SUBINDO(3),
    ALAGAMENTO_VIA(3),
    BUEIRO_ENTUPIDO(2),
    OUTRO(1);

    private final int prioridadeBase;

    TipoRelato(int prioridadeBase) {
        this.prioridadeBase = prioridadeBase;
    }

    public int getPrioridadeBase() {
        return prioridadeBase;
    }
}
