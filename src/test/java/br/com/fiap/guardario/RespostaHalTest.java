package br.com.fiap.guardario;

import br.com.fiap.guardario.assembler.RespostaHal;
import br.com.fiap.guardario.dto.AlertaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.core.EmbeddedWrapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de unidade do RespostaHal (sem Spring): a coleção vazia sai com o _embedded, com a lista vazia.
 */
class RespostaHalTest {

    @Test
    void colecaoVaziaLevaAListaVaziaComONomeDaRelacaoEMantemOsLinks() {
        CollectionModel<EntityModel<AlertaResponse>> vazia = CollectionModel.of(List.of(), Link.of("/api/alertas/ativos"));

        CollectionModel<EntityModel<AlertaResponse>> resposta = RespostaHal.colecao(vazia, AlertaResponse.class);

        assertThat(resposta.getLinks()).containsExactlyElementsOf(vazia.getLinks());
        assertThat(resposta.getContent()).hasSize(1);
        Object item = resposta.getContent().iterator().next();
        assertThat(item).isInstanceOf(EmbeddedWrapper.class);
        EmbeddedWrapper embrulho = (EmbeddedWrapper) item;
        assertThat(embrulho.isCollectionValue()).isTrue();
        assertThat(embrulho.getRelTargetType()).isEqualTo(AlertaResponse.class);
        assertThat((Iterable<?>) embrulho.getValue()).isEmpty();
    }

    @Test
    void colecaoComItensFicaComoEsta() {
        CollectionModel<EntityModel<AlertaResponse>> cheia = CollectionModel.of(List.of(EntityModel.of(
                new AlertaResponse(null, null, null, null, null, null, null, null, null, null, null, null, null, null))));

        assertThat(RespostaHal.colecao(cheia, AlertaResponse.class)).isSameAs(cheia);
    }
}
