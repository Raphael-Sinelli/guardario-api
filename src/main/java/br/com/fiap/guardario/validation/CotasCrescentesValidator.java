package br.com.fiap.guardario.validation;

import br.com.fiap.guardario.dto.EstacaoRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CotasCrescentesValidator implements ConstraintValidator<CotasCrescentes, EstacaoRequest> {

    @Override
    public boolean isValid(EstacaoRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        Integer atencao = request.cotaAtencaoCm();
        Integer alerta = request.cotaAlertaCm();
        Integer transbordo = request.cotaTransbordoCm();

        // cota nula já é barrada pelo @NotNull do campo; aqui só comparamos
        if (atencao == null || alerta == null || transbordo == null) {
            return true;
        }
        return atencao < alerta && alerta < transbordo;
    }
}
