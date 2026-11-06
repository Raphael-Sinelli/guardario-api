package br.com.fiap.guardario.repository;

import br.com.fiap.guardario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // derived query: usada no login e no filtro JWT
    Optional<Usuario> findByEmail(String email);

    // derived query com exists: para no primeiro registro, mais barato que count
    boolean existsByEmail(String email);
}
