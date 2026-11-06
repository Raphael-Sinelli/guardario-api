package br.com.fiap.guardario.model;

/**
 * Máquina de estados do relato (RN07).
 * ABERTO vai para EM_ATENDIMENTO ou DESCARTADO; EM_ATENDIMENTO vai para RESOLVIDO.
 * RESOLVIDO e DESCARTADO são finais.
 */
public enum StatusRelato {

    ABERTO,
    EM_ATENDIMENTO,
    RESOLVIDO,
    DESCARTADO;

    public boolean podeMudarPara(StatusRelato novo) {
        return switch (this) {
            case ABERTO -> novo == EM_ATENDIMENTO || novo == DESCARTADO;
            case EM_ATENDIMENTO -> novo == RESOLVIDO;
            case RESOLVIDO, DESCARTADO -> false;
        };
    }
}
