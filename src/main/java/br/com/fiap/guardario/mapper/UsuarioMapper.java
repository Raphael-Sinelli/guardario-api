package br.com.fiap.guardario.mapper;

import br.com.fiap.guardario.dto.UsuarioResponse;
import br.com.fiap.guardario.model.Usuario;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        // getArea().getId() não dispara SELECT: o proxy LAZY já conhece o id
        Long areaId = usuario.getArea() != null ? usuario.getArea().getId() : null;
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapel(),
                usuario.getTelefone(),
                areaId,
                usuario.getCriadoEm());
    }
}
