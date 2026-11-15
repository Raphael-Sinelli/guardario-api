package br.com.fiap.guardario.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;
import java.time.Instant;

public class JanelaDeMedicaoValidator implements ConstraintValidator<JanelaDeMedicao, Instant> {

    private Duration atras;

    @Override
    public void initialize(JanelaDeMedicao anotacao) {
        this.atras = Duration.ofDays(anotacao.diasAtras());
    }

    // nulo é válido: sem medidaEm, o servidor usa o horário atual
    @Override
    public boolean isValid(Instant medidaEm, ConstraintValidatorContext context) {
        if (medidaEm == null) {
            return true;
        }
        Instant agora = Instant.now();
        return !medidaEm.isBefore(agora.minus(atras)) && !medidaEm.isAfter(agora);
    }
}
