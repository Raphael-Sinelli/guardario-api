package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Momento da medição dentro de uma janela plausível: no máximo diasAtras no passado (a estação guarda poucos dias
 * de leitura sem internet; ano 1900 é relógio errado) e no máximo segundosAdiante no futuro (o relógio da estação
 * pode estar uns segundos adiantado em relação ao servidor; com o @PastOrPresent, 5 segundos já davam 400).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JanelaDeMedicaoValidator.class)
public @interface JanelaDeMedicao {

    int diasAtras();

    int segundosAdiante();

    String message() default "deve estar entre {diasAtras} dias atrás e agora";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
