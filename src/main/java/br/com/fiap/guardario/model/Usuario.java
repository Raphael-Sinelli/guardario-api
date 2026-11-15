package br.com.fiap.guardario.model;

import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OptimisticLock;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // áreas que a pessoa acompanha além da área do cadastro (a do trabalho, a da escola dos filhos): N:M unidirecional,
    // como Task e Tag no curso. Set, porque a mesma área não entra duas vezes e a chave da tabela de junção é o par
    // (usuario_id, area_id). Sem cascade: a área existe sem o morador, e deixar de seguir não pode apagar a área.
    // @OptimisticLock(excluded = true): seguir uma área mexe só na tabela de junção e não sobe o @Version, para não
    // derrubar com 409 uma edição de perfil feita ao mesmo tempo
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "usuario_area_seguida",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "area_id"))
    @OptimisticLock(excluded = true)
    private Set<AreaRisco> areasSeguidas = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    // duas requisições mudando o mesmo cadastro ao mesmo tempo (troca de senha, de papel ou do perfil): a segunda
    // recebe 409, em vez de gravar o cadastro inteiro por cima da primeira. O perfil prod cria o schema com
    // ddl-auto: update, então o default 0 e o not null preenchem as linhas que já existem no banco quando a coluna entra
    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private Long versao;

    // sobe a cada troca de senha. O token leva esse número na claim versaoToken, e o filtro JWT recusa o token de
    // número antigo: trocar a senha derruba todo token emitido antes. O default 0 vale para as linhas que já existem
    @ColumnDefault("0")
    @Column(nullable = false)
    private int versaoToken;

    public static final int LIMITE_DE_AREAS_SEGUIDAS = 20;

    // seguir de novo uma área que já segue não muda nada (idempotente); a área nova esbarra no teto de 20
    public void seguir(AreaRisco area) {
        if (areasSeguidas.contains(area)) {
            return;
        }
        if (areasSeguidas.size() >= LIMITE_DE_AREAS_SEGUIDAS) {
            throw new CampoInvalidoException("areasSeguidas", "o limite é de " + LIMITE_DE_AREAS_SEGUIDAS
                    + " áreas seguidas por usuário: deixe de seguir uma antes de seguir outra");
        }
        areasSeguidas.add(area);
    }

    // deixar de seguir uma área que não segue também não muda nada
    public void deixarDeSeguir(AreaRisco area) {
        areasSeguidas.remove(area);
    }

    // troca o hash e invalida os tokens emitidos antes da troca
    public void trocarSenha(String novoHash) {
        this.senha = novoHash;
        this.versaoToken++;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Horario.agora();
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
