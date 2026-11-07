package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AuthResponse;
import br.com.fiap.guardario.dto.LoginRequest;
import br.com.fiap.guardario.dto.RegisterRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;
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

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final AreaRiscoRepository areaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegisterRequest request) {
        String email = normalizarEmail(request.email());
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
        // barra o segundo, e o flush aqui dentro transforma isso no mesmo 409 da regra, com a mensagem certa
        try {
            return UsuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException e) {
            throw new EmailJaCadastradoException(email);
        }
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
    @Transactional(readOnly = true)
    public UsuarioResponse usuarioAtual() {
        Long id = SecurityUtils.usuarioLogado().getId();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário", id));
        return UsuarioMapper.toResponse(usuario);
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
