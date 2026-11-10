package br.com.fiap.guardario.assembler;

import org.springframework.core.ResolvableType;
import org.springframework.hateoas.AffordanceModel.PayloadMetadata;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.hateoas.mediatype.ConfigurableAffordance;
import org.springframework.hateoas.mediatype.PropertyUtils;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Link de ação com o template HAL-FORMS dele. O link vai para _links, como sempre; o template (método, alvo e campos
 * do corpo, com as regras da Bean Validation) fica pendurado no self e só aparece quando o cliente pede
 * application/prs.hal-forms+json. Cada template tem o nome do link: atender, encerrar, registrar-entrada.
 */
final class Acoes {

    private Acoes() {
    }

    static void adicionar(RepresentationModel<?> model, Link acao, HttpMethod metodo, Class<?> corpo) {
        // o linkTo(methodOn(...)) já cria um template com o nome do método Java e os campos como só leitura: ele sai,
        // e fica só o template montado aqui, com o nome do link
        Link semTemplateAutomatico = acao.withoutAffordances();
        ConfigurableAffordance template = Affordances.of(semTemplateAutomatico).afford(metodo)
                .withName(semTemplateAutomatico.getRel().value());
        if (corpo != null) {
            template = template.withInput(new CamposDoCorpo(corpo));
        }
        var templates = template.build().stream().toList();

        // o self ganha o template e continua na mesma posição em _links (o mapLink o mandaria para o fim)
        List<Link> links = model.getLinks().stream()
                .map(link -> link.hasRel(IanaLinkRelations.SELF) ? link.andAffordances(templates) : link)
                .toList();
        model.removeLinks();
        model.add(links);
        model.add(semTemplateAutomatico);
    }

    // os campos do record de entrada, como o Spring HATEOAS lê, com dois acertos: record não tem setter, e ele marcaria
    // todo campo como só leitura; e o @Size de um texto viraria min e max de um número, quando é o tamanho do texto
    private record CamposDoCorpo(Class<?> corpo) implements PayloadMetadata {

        @Override
        public Stream<PropertyMetadata> stream() {
            return PropertyUtils.getExposedProperties(corpo).stream().map(Campo::new);
        }

        @Override
        public Class<?> getType() {
            return corpo;
        }
    }

    private record Campo(PropertyMetadata original) implements PropertyMetadata {

        private boolean texto() {
            return CharSequence.class.isAssignableFrom(original.getType().toClass());
        }

        @Override
        public String getName() {
            return original.getName();
        }

        @Override
        public boolean isRequired() {
            return original.isRequired();
        }

        @Override
        public boolean isReadOnly() {
            return false; // é campo de entrada: o cliente preenche
        }

        @Override
        public Optional<String> getPattern() {
            return original.getPattern();
        }

        @Override
        public ResolvableType getType() {
            return original.getType();
        }

        @Override
        public Number getMin() {
            return texto() ? null : original.getMin();
        }

        @Override
        public Number getMax() {
            return texto() ? null : original.getMax();
        }

        @Override
        public Long getMinLength() {
            Number minimo = original.getMinLength() != null ? original.getMinLength() : original.getMin();
            return texto() && minimo != null && minimo.longValue() > 0 ? minimo.longValue() : null;
        }

        @Override
        public Long getMaxLength() {
            Number maximo = original.getMaxLength() != null ? original.getMaxLength() : original.getMax();
            return texto() && maximo != null ? maximo.longValue() : null;
        }

        @Override
        public String getInputType() {
            return texto() && "range".equals(original.getInputType()) ? "text" : original.getInputType();
        }
    }
}
