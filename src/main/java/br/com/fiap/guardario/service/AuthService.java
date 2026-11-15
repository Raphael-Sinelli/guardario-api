package br.com.fiap.guardario.service;

import br.com.fiap.guardario.dto.AlterarSenhaRequest;
import br.com.fiap.guardario.dto.AtualizarPerfilRequest;
import br.com.fiap.guardario.dto.AuthResponse;
import br.com.fiap.guardario.dto.LoginRequest;
import br.com.fiap.guardario.dto.RegisterRequest;
import br.com.fiap.guardario.dto.UsuarioResponse;

public interface AuthService {

    UsuarioResponse registrar(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UsuarioResponse atualizarPerfil(AtualizarPerfilRequest request);

    UsuarioResponse usuarioAtual();

    void alterarSenha(AlterarSenhaRequest request);
}
