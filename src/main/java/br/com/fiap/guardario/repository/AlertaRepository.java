package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Alerta;
import br.com.fiap.guardario.model.StatusAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    // a resposta do alerta leva o nome da área, então ela vem no mesmo SELECT
    @Override
    @EntityGraph(attributePaths = "area")
    Page<Alerta> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "area")
    Page<Alerta> findByStatus(StatusAlerta status, Pageable pageable);

    @EntityGraph(attributePaths = "area")
    List<Alerta> findByStatusOrderByEmitidoEmDesc(StatusAlerta status);

    // usada na RN02 para saber se a área já tem alerta ativo
    List<Alerta> findByAreaIdAndStatus(Long areaId, StatusAlerta status);

    boolean existsByAreaId(Long areaId);

    boolean existsByEstacaoId(Long estacaoId);
}
