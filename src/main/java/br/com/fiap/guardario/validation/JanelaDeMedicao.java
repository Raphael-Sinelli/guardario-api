package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Momento da medição dentro de uma janela plausível: no máximo diasAtras no passado (a estação guarda poucos dias
 * de leitura sem internet; ano 1900 é relógio errado) e nunca no futuro. Data no futuro é relógio adiantado: aceita,
 * ela deixava a leitura repetida escapar da conferência de repetição. O 400 sai antes do service.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JanelaDeMedicaoValidator.class)
public @interface JanelaDeMedicao {

    int diasAtras();

    String message() default "deve estar entre {diasAtras} dias atrás e agora";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
