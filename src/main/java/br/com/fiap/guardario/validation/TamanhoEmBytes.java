package br.com.fiap.guardario.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita o texto em bytes UTF-8, e não em caracteres. Serve para a senha: o BCrypt só considera os
 * 72 primeiros bytes, e uma letra com acento ocupa 2 bytes, então o @Size(max = 72) deixava passar
 * uma senha de 40 letras "á" (80 bytes), que o BCrypt recusa.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TamanhoEmBytesValidator.class)
public @interface TamanhoEmBytes {

    int max();

    String message() default "deve ter no máximo {max} bytes (letra com acento ocupa 2)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
