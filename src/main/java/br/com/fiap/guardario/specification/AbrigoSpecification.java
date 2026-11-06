package br.com.fiap.guardario.specification;

import br.com.fiap.guardario.model.Abrigo;
import br.com.fiap.guardario.model.StatusAbrigo;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da busca de abrigos no app: aceita pets, acessível para PcD, com vagas, área e status.
 */
public final class AbrigoSpecification {

    private AbrigoSpecification() {
    }

    public static Specification<Abrigo> comFiltros(Long areaId, Boolean aceitaPets, Boolean acessivelPcd,
                                                   Boolean comVagas, StatusAbrigo status) {
        Specification<Abrigo> spec = Specification.unrestricted();
        if (areaId != null) {
            spec = spec.and(daArea(areaId));
        }
        if (aceitaPets != null) {
            spec = spec.and(aceitaPets(aceitaPets));
        }
        if (acessivelPcd != null) {
            spec = spec.and(acessivelPcd(acessivelPcd));
        }
        if (Boolean.TRUE.equals(comVagas)) {
            spec = spec.and(comVagas());
        }
        if (status != null) {
            spec = spec.and(comStatus(status));
        }
        return spec;
    }

    public static Specification<Abrigo> daArea(Long areaId) {
        return (root, query, cb) -> cb.equal(root.get("area").get("id"), areaId);
    }

    public static Specification<Abrigo> aceitaPets(boolean valor) {
        return (root, query, cb) -> cb.equal(root.get("aceitaPets"), valor);
    }

    public static Specification<Abrigo> acessivelPcd(boolean valor) {
        return (root, query, cb) -> cb.equal(root.get("acessivelPcd"), valor);
    }

    // com vaga = aberto e ocupação menor que a capacidade (comparação entre duas colunas)
    public static Specification<Abrigo> comVagas() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), StatusAbrigo.ABERTO),
                cb.lessThan(root.<Integer>get("ocupacao"), root.<Integer>get("capacidade")));
    }

    public static Specification<Abrigo> comStatus(StatusAbrigo status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
