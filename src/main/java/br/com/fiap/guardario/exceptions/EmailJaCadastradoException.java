package br.com.fiap.guardario.exceptions;

public class EmailJaCadastradoException extends RegraNegocioException {

    public EmailJaCadastradoException(String email) {
        super("Já existe um cadastro com o email " + email);
    }
}
