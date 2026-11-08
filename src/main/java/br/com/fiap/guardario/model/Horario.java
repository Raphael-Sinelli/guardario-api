package br.com.fiap.guardario.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * O Java mede o horário em frações de 100 ns, e o banco guarda microssegundos, arredondando. Sem o corte, a resposta
 * do POST mostrava um instante e a busca seguinte, outro. As entidades usam este horário, já cortado em
 * microssegundos, para o valor da resposta ser o mesmo que fica gravado (a Leitura faz o mesmo corte no medidaEm).
 */
public final class Horario {

    private Horario() {
    }

    public static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
