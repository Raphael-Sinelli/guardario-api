package br.com.fiap.guardario.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class TamanhoEmBytesValidator implements ConstraintValidator<TamanhoEmBytes, String> {

    private int max;

    @Override
    public void initialize(TamanhoEmBytes anotacao) {
        this.max = anotacao.max();
    }

    // nulo é válido: quem exige o campo é o @NotBlank
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
