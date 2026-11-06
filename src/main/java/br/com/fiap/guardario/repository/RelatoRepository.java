package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Relato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RelatoRepository extends JpaRepository<Relato, Long>, JpaSpecificationExecutor<Relato> {

    // "meus relatos": o id do autor vem do token, nunca de parâmetro
    Page<Relato> findByAutorId(Long autorId, Pageable pageable);

    boolean existsByAreaId(Long areaId);
}
