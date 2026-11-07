package br.com.fiap.guardario.assembler;

import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.core.EmbeddedWrappers;

import java.util.List;

/**
 * Duas correções do HAL padrão para todas as listas da API:
 * 1. Lista vazia: o HAL omite o _embedded, e o app teria de tratar dois formatos (com e sem "_embedded.abrigos").
 *    Aqui toda lista sai com o _embedded, mesmo vazia: {"_embedded": {"abrigos": []}}.
 * 2. Página pedida depois da última: o link prev apontaria para outra página vazia. Ele sai, e ficam self,
 *    first e last, que levam a páginas que existem.
 */
public final class RespostaHal {

    private RespostaHal() {
    }

    public static <T> PagedModel<EntityModel<T>> pagina(Page<T> pagina, PagedResourcesAssembler<T> paginador,
                                                        RepresentationModelAssembler<T, EntityModel<T>> assembler,
                                                        Class<T> tipo) {
        PagedModel<EntityModel<T>> modelo = pagina.hasContent()
                ? paginador.toModel(pagina, assembler)
                : comoPagina(paginador.toEmptyModel(pagina, tipo));
        if (pagina.getNumber() > 0 && pagina.getNumber() >= pagina.getTotalPages()) {
            Links links = modelo.getLinks().without(IanaLinkRelations.PREV);
            modelo.removeLinks();
            modelo.add(links);
        }
        return modelo;
    }

    public static <T> CollectionModel<EntityModel<T>> colecao(CollectionModel<EntityModel<T>> modelo, Class<T> tipo) {
        if (!modelo.getContent().isEmpty()) {
            return modelo;
        }
        return comoColecao(CollectionModel.of(List.of(new EmbeddedWrappers(false).emptyCollectionOf(tipo)), modelo.getLinks()));
    }

    // a lista vazia vem embrulhada para o HAL saber o nome da relação (do @Relation do DTO); o JSON é o mesmo das cheias
    @SuppressWarnings("unchecked")
    private static <T> PagedModel<EntityModel<T>> comoPagina(PagedModel<?> modelo) {
        return (PagedModel<EntityModel<T>>) modelo;
    }

    @SuppressWarnings("unchecked")
    private static <T> CollectionModel<EntityModel<T>> comoColecao(CollectionModel<?> modelo) {
        return (CollectionModel<EntityModel<T>>) modelo;
    }
}
