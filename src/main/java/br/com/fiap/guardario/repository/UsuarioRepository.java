package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // derived query: usada no login e no filtro JWT
    Optional<Usuario> findByEmail(String email);

    // derived query com exists: para no primeiro registro, mais barato que count
    boolean existsByEmail(String email);

    // área com morador cadastrado não pode ser excluída
    boolean existsByAreaId(Long areaId);

    // o usuário e as áreas que ele segue num SELECT só (join na tabela de junção), sem N+1 na listagem
    @EntityGraph(attributePaths = "areasSeguidas")
    Optional<Usuario> findComAreasSeguidasById(Long id);

    // trava o cadastro para seguir ou deixar de seguir: duas chamadas ao mesmo tempo entram uma de cada vez, sem
    // repetir o par na tabela de junção nem passar do teto. A trava não sobe o @Version
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarComTrava(@Param("id") Long id);

    // os ids das áreas que o usuário segue, para o link de cada área dizer se ele já segue: uma consulta por resposta
    @Query("select a.id from Usuario u join u.areasSeguidas a where u.id = :id")
    Set<Long> buscarIdsDasAreasSeguidas(@Param("id") Long id);

    // quem segue a área: ao excluir a área, ela sai da lista dessas pessoas
    @Query("select u from Usuario u join u.areasSeguidas a where a.id = :areaId")
    List<Usuario> buscarSeguidoresDaArea(@Param("areaId") Long areaId);
}
