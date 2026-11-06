package br.com.fiap.guardario.specification;

import br.com.fiap.guardario.model.Relato;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.TipoRelato;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Filtros do painel de relatos da Defesa Civil. Todos são opcionais e combináveis:
 * cada um vira um pedaço do WHERE e só entra na consulta se o parâmetro veio preenchido.
 */
public final class RelatoSpecification {

    private RelatoSpecification() {
    }

    public static Specification<Relato> comFiltros(TipoRelato tipo, StatusRelato status, Long areaId,
                                                   Integer prioridadeMinima, Instant de, Instant ate) {
        Specification<Relato> spec = Specification.unrestricted();
        if (tipo != null) {
            spec = spec.and(doTipo(tipo));
        }
        if (status != null) {
            spec = spec.and(comStatus(status));
        }
        if (areaId != null) {
            spec = spec.and(daArea(areaId));
        }
        if (prioridadeMinima != null) {
            spec = spec.and(comPrioridadeMinima(prioridadeMinima));
        }
        if (de != null) {
            spec = spec.and(criadoAPartirDe(de));
        }
        if (ate != null) {
            spec = spec.and(criadoAte(ate));
        }
        return spec;
    }

    public static Specification<Relato> doTipo(TipoRelato tipo) {
        return (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
    }

    public static Specification<Relato> comStatus(StatusRelato status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Relato> daArea(Long areaId) {
        return (root, query, cb) -> cb.equal(root.get("area").get("id"), areaId);
    }

    public static Specification<Relato> comPrioridadeMinima(Integer prioridade) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("prioridade"), prioridade);
    }

    public static Specification<Relato> criadoAPartirDe(Instant de) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("criadoEm"), de);
    }

    public static Specification<Relato> criadoAte(Instant ate) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("criadoEm"), ate);
    }
}
