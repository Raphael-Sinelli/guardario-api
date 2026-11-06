package br.com.fiap.guardario.exceptions;

// operação em conflito com o estado atual do recurso: vira 409
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
