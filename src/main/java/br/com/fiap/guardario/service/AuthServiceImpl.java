package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AlterarSenhaRequest;
import br.com.fiap.guardario.dto.AtualizarPerfilRequest;
import br.com.fiap.guardario.dto.AuthResponse;
import br.com.fiap.guardario.dto.LoginRequest;
import br.com.fiap.guardario.dto.RegisterRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.exceptions.EmailJaCadastradoException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.mapper.UsuarioMapper;
import br.com.fiap.guardario.model.AreaRisco;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.Usuario;
import br.com.fiap.guardario.repository.AreaRiscoRepository;
import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.JwtService;
import br.com.fiap.guardario.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int TAMANHO_MAXIMO_EMAIL = 150; // o mesmo da coluna usuario.email

    private final UsuarioRepository usuarioRepository;
    private final AreaRiscoRepository areaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegisterRequest request) {
        String email = normalizarEmail(request.email());
        // o @Size do DTO conta o email antes das minúsculas, e o toLowerCase pode crescer o texto ("İ" vira "i" e o
        // ponto): conferido de novo aqui, o email longo demais dá 400 no campo, e não erro do banco
        if (email.length() > TAMANHO_MAXIMO_EMAIL) {
            throw new CampoInvalidoException("email", "deve ter no máximo " + TAMANHO_MAXIMO_EMAIL + " caracteres em minúsculas");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException(email);
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(request.senha())); // BCrypt: salt aleatório, nunca a senha pura
        usuario.setTelefone(request.telefone());
        usuario.setPapel(Papel.CIDADAO); // RN08: só o ADMIN promove alguém a AGENTE

        if (request.areaId() != null) {
            AreaRisco area = areaRepository.findById(request.areaId())
                    .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));
            usuario.setArea(area);
        }

        // dois cadastros simultâneos com o mesmo email passam pelo existsByEmail juntos; o índice único do banco
        // barra o segundo, e o flush aqui dentro transforma isso no mesmo 409 da regra, com a mensagem certa. Só a
        // violação de unicidade é email repetido: outro motivo do banco segue para o GlobalExceptionHandler
        try {
            return UsuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException e) {
            if (violouUnicidade(e)) {
                throw new EmailJaCadastradoException(email);
            }
            throw e;
        }
    }

    // SQLState 23505 é a violação de índice único, no H2 e no PostgreSQL; o único índice único do usuário é o email
    private static boolean violouUnicidade(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return "23505".equals(sql.getSQLState());
            }
        }
        return false;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // o AuthenticationManager busca o usuário pelo UserDetailsService e compara a senha com BCrypt (matches);
        // senha errada lança BadCredentialsException, que o GlobalExceptionHandler transforma em 401
        Authentication autenticacao = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizarEmail(request.email()), request.senha()));

        Usuario usuario = (Usuario) autenticacao.getPrincipal();
        String token = jwtService.gerarToken(usuario);
        return new AuthResponse(token, "Bearer", jwtService.getExpiracaoSegundos(), usuario.getEmail(), usuario.getPapel());
    }

    @Override
    @Transactional
    public UsuarioResponse atualizarPerfil(AtualizarPerfilRequest request) {
        // recarrega pelo id do token: o usuário do filtro JWT foi carregado fora desta transação
        Long id = SecurityUtils.usuarioLogado().getId();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));

        // só nome, telefone e área: email, senha e papel não estão no DTO e não mudam aqui
        usuario.setNome(request.nome());
        usuario.setTelefone(request.telefone());
        if (request.areaId() != null) {
            AreaRisco area = areaRepository.findById(request.areaId())
                    .orElseThrow(() -> new ResourceNotFoundException("área", request.areaId()));
            usuario.setArea(area);
        } else {
            usuario.setArea(null);
        }
        return UsuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse usuarioAtual() {
        Long id = SecurityUtils.usuarioLogado().getId();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));
        return UsuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public void alterarSenha(AlterarSenhaRequest request) {
        // o usuário do SecurityUtils foi carregado pelo filtro JWT, fora desta transação; o recarregado é o que
        // o Hibernate grava no fim
        Long id = SecurityUtils.usuarioLogado().getId();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));

        // o banco guarda o hash: a comparação é pelo BCrypt, nunca com equals
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenha())) {
            throw new CampoInvalidoException("senhaAtual", "Senha atual incorreta");
        }
        // a senha atual acabou de ser conferida: a nova igual a ela não troca nada
        if (request.novaSenha().equals(request.senhaAtual())) {
            throw new CampoInvalidoException("novaSenha", "deve ser diferente da senha atual");
        }
        usuario.trocarSenha(passwordEncoder.encode(request.novaSenha())); // os tokens emitidos antes deixam de valer
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
