package br.com.fiap.guardario.configurations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lista branca dos campos pelos quais a rota deixa ordenar (?sort=). Fica no método do controller que recebe o Pageable.
 * O PaginacaoConfig recusa com 400 qualquer outro campo, e rota sem esta anotação não aceita ?sort=.
 * O OpenApiConfig mostra a lista na documentação do parâmetro sort.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CamposDeOrdenacao {

    String[] value();
}
