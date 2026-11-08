package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Cria o primeiro ADMIN a partir das variáveis de ambiente ADMIN_EMAIL e ADMIN_PASSWORD.
 * O cadastro público só cria CIDADAO (RN08) e o perfil prod não tem carga de demonstração: sem isto,
 * ninguém conseguiria promover o primeiro AGENTE. A senha nunca fica no código nem no Git.
 * Roda depois do DataInitializer, para não impedir a carga de demonstração, que só roda com o banco vazio.
 */
@Slf4j
@Component
@Order(2)
public class AdminInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public AdminInitializer(UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email:}") String email,
                            @Value("${app.admin.password:}") String senha) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || senha.isEmpty()) {
            return;
        }
        // mesmas regras do cadastro: no mínimo 8 caracteres e no máximo 72 bytes, que é o que o BCrypt considera
        if (senha.length() < 8 || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD precisa ter pelo menos 8 caracteres e no máximo 72 bytes");
        }
        // já existe: o ADMIN do ambiente é criado uma vez só, e as subidas seguintes não sobrescrevem o cadastro do banco
        if (usuarioRepository.existsByEmail(email)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail(email);
        admin.setSenha(passwordEncoder.encode(senha));
        admin.setPapel(Papel.ADMIN);
        usuarioRepository.save(admin);
        log.info("ADMIN inicial criado a partir de ADMIN_EMAIL: {}", email);
    }
}
