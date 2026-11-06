package br.com.fiap.guardario.exceptions;

// vira 404 no GlobalExceptionHandler
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String recurso, Long id) {
        super("Não existe " + recurso + " com id " + id);
    }
}
