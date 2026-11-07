package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Alerta manual precisa ter risco de verdade: NORMAL não é nível de alerta.
 * Fica no campo para o erro sair com o nome dele em errors, como as outras validações.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NivelDeAlertaValidator.class)
public @interface NivelDeAlerta {

    String message() default "um alerta precisa ter nível ATENCAO, ALERTA ou EMERGENCIA";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
