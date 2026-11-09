package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Relato;
import br.com.fiap.guardario.model.StatusRelato;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RelatoRepository extends JpaRepository<Relato, Long>, JpaSpecificationExecutor<Relato> {

    // "meus relatos": o id do autor vem do token, nunca de parâmetro
    Page<Relato> findByAutorId(Long autorId, Pageable pageable);

    boolean existsByAreaId(Long areaId);

    // relatos em aberto da área, travados até o fim da transação (SELECT ... FOR UPDATE): a prioridade muda junto com o
    // nível da área, sem cruzar com um agente mudando o status do mesmo relato
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Relato r where r.area.id = :areaId and r.status in :status")
    List<Relato> buscarEmAbertoComTrava(@Param("areaId") Long areaId, @Param("status") Collection<StatusRelato> status);
}
