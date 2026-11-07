package br.com.fiap.guardario.validation;

import br.com.fiap.guardario.model.NivelRisco;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NivelDeAlertaValidator implements ConstraintValidator<NivelDeAlerta, NivelRisco> {

    // nulo é válido: quem exige o campo é o @NotNull
    @Override
    public boolean isValid(NivelRisco nivel, ConstraintValidatorContext context) {
        return nivel != NivelRisco.NORMAL;
    }
}
