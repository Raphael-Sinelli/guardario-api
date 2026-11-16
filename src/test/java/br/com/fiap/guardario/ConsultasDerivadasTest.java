package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra de bolso da aula de derived query: até duas ou três condições no nome. Passou disso, o nome vira parágrafo e
 * a consulta vai para um @Query com JPQL, que tem nome curto e deixa a regra legível. O teste lê os repositórios
 * compilados e conta as condições de cada derived (o que vem depois do By, separado por And ou Or, sem o OrderBy).
 */
class ConsultasDerivadasTest {

    private static final Pattern DERIVED = Pattern.compile("^(find|read|get|query|search|stream|count|exists|delete)\\w*?By(\\w+)$");
    private static final Pattern SEPARADOR = Pattern.compile("(?<=[a-z0-9])(And|Or)(?=[A-Z])");

    @Test
    void nenhumDerivedQueryPassaDeTresCondicoes() throws Exception {
        List<String> derived = derivedDosRepositorios();
        List<String> longos = derived.stream().filter(metodo -> condicoes(metodo) > 3).toList();

        assertThat(longos).isEmpty();
        // a rubrica pede derived query: elas continuam nas consultas simples (login, filtros, verificações)
        assertThat(derived).contains("UsuarioRepository.findByEmail", "LeituraRepository.findByEstacaoId");
    }

    @Test
    void contagemDeCondicoesSegueONomeDoMetodo() {
        assertThat(condicoes("X.findByEmail")).isEqualTo(1);
        assertThat(condicoes("X.findByAreaIdAndStatus")).isEqualTo(2);
        assertThat(condicoes("X.findByStatusOrderByNomeAsc")).isEqualTo(1);
        assertThat(condicoes("X.findByOrigemOrStatus")).isEqualTo(2);
        assertThat(condicoes("X.existsByEstacaoIdAndMedidaEmAndNivelCmAndChuvaMmH")).isEqualTo(4);
    }

    private static int condicoes(String metodo) {
        Matcher m = DERIVED.matcher(metodo.substring(metodo.indexOf('.') + 1));
        if (!m.matches()) {
            return 0;
        }
        String criterio = m.group(2).split("OrderBy")[0];
        return criterio.isEmpty() ? 0 : SEPARADOR.split(criterio).length;
    }

    // os métodos sem @Query das interfaces do pacote repository, como "LeituraRepository.findByEstacaoId"
    private static List<String> derivedDosRepositorios() throws ClassNotFoundException, URISyntaxException {
        String pacote = "br.com.fiap.guardario.repository";
        File pasta = new File(Objects.requireNonNull(
                ConsultasDerivadasTest.class.getClassLoader().getResource(pacote.replace('.', '/'))).toURI());
        List<String> derived = new ArrayList<>();
        for (File arquivo : Objects.requireNonNull(pasta.listFiles((dir, nome) -> nome.endsWith(".class") && !nome.contains("$")))) {
            Class<?> tipo = Class.forName(pacote + "." + arquivo.getName().replace(".class", ""));
            if (!tipo.isInterface() || !Repository.class.isAssignableFrom(tipo)) {
                continue;
            }
            for (Method metodo : tipo.getDeclaredMethods()) {
                if (!metodo.isAnnotationPresent(Query.class) && DERIVED.matcher(metodo.getName()).matches()) {
                    derived.add(tipo.getSimpleName() + "." + metodo.getName());
                }
            }
        }
        assertThat(derived).isNotEmpty();
        return derived;
    }
}
