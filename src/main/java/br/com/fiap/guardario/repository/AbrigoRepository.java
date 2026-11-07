package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Abrigo;
import br.com.fiap.guardario.model.StatusAbrigo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// JpaSpecificationExecutor habilita findAll(Specification, Pageable) para os filtros opcionais
public interface AbrigoRepository extends JpaRepository<Abrigo, Long>, JpaSpecificationExecutor<Abrigo> {

    // abrigo ABERTO tem vaga por definição: quando enche, o status vira LOTADO
    List<Abrigo> findByStatusOrderByNomeAsc(StatusAbrigo status);

    boolean existsByAreaId(Long areaId);

    // entradas e saídas simultâneas no mesmo abrigo entram uma de cada vez: com vaga, nenhuma é recusada por conflito
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Abrigo a where a.id = :id")
    Optional<Abrigo> buscarComTrava(@Param("id") Long id);
}
