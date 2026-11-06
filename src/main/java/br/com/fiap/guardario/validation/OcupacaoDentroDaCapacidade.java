package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RN05 no cadastro: a ocupação informada não pode passar da capacidade do abrigo.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OcupacaoDentroDaCapacidadeValidator.class)
public @interface OcupacaoDentroDaCapacidade {

    String message() default "A ocupação não pode ser maior que a capacidade do abrigo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
