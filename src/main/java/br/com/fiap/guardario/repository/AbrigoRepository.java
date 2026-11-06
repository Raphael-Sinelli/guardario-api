package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Abrigo;
import br.com.fiap.guardario.model.StatusAbrigo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

// JpaSpecificationExecutor habilita findAll(Specification, Pageable) para os filtros opcionais
public interface AbrigoRepository extends JpaRepository<Abrigo, Long>, JpaSpecificationExecutor<Abrigo> {

    // abrigo ABERTO tem vaga por definição: quando enche, o status vira LOTADO
    List<Abrigo> findByStatusOrderByNomeAsc(StatusAbrigo status);

    boolean existsByAreaId(Long areaId);
}
