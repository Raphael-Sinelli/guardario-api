package br.com.fiap.guardario.validation;

import br.com.fiap.guardario.dto.AbrigoRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OcupacaoDentroDaCapacidadeValidator implements ConstraintValidator<OcupacaoDentroDaCapacidade, AbrigoRequest> {

    @Override
    public boolean isValid(AbrigoRequest request, ConstraintValidatorContext context) {
        if (request == null || request.capacidade() == null || request.ocupacao() == null) {
            return true;
        }
        return request.ocupacao() <= request.capacidade();
    }
}
