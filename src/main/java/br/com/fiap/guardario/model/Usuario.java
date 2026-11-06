package br.com.fiap.guardario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // hash BCrypt, sempre 60 caracteres
    @Column(nullable = false, length = 60)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(length = 20)
    private String telefone;

    // bairro onde a pessoa mora (opcional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private AreaRisco area;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    // hasRole('ADMIN') procura a authority ROLE_ADMIN
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    // para o Spring Security o "username" é o email
    @Override
    public String getUsername() {
        return email;
    }
}
