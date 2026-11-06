package br.com.fiap.guardario.security;

import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.Usuario;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    // usuário dono do token da requisição atual; o "dono" de um recurso sai daqui, nunca de parâmetro
    public static Usuario usuarioLogado() {
        return usuarioOpcional()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Faça login para continuar"));
    }

    public static Optional<Usuario> usuarioOpcional() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Usuario usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static boolean temPapel(Papel... papeis) {
        return usuarioOpcional()
                .map(usuario -> Arrays.asList(papeis).contains(usuario.getPapel()))
                .orElse(false);
    }

    public static boolean isAgenteOuAdmin() {
        return temPapel(Papel.AGENTE, Papel.ADMIN);
    }
}
