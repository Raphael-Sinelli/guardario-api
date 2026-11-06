package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RN10: as cotas da estação precisam ser crescentes (atenção < alerta < transbordo).
 * Fica na classe porque compara três campos entre si.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CotasCrescentesValidator.class)
public @interface CotasCrescentes {

    String message() default "As cotas precisam ser crescentes: atenção < alerta < transbordo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
