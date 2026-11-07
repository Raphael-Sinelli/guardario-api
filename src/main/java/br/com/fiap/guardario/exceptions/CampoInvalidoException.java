package br.com.fiap.guardario.exceptions;

import lombok.Getter;

/**
 * Valor inválido num campo do corpo ou num parâmetro, conferido fora da Bean Validation (no service ou no
 * interceptor de paginação). Sai como 400 no mesmo formato da Bean Validation: ValidationErrorResponse com o campo.
 */
@Getter
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }
}
