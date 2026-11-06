package br.com.fiap.guardario.security;

import br.com.fiap.guardario.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Gera e valida o JWT (HS256). O token é assinado, não criptografado:
 * por isso só leva o email (sub) e o papel, nada sensível.
 */
@Slf4j
@Service
public class JwtService {

    private static final int TAMANHO_MINIMO_CHAVE_BYTES = 32; // HS256 exige 256 bits

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public JwtService(@Value("${app.jwt.secret:}") String segredo,
                      @Value("${app.jwt.expiration-seconds:3600}") long expiracaoSegundos) {
        this.expiracaoSegundos = expiracaoSegundos;

        if (segredo == null || segredo.isBlank()) {
            // nenhuma chave fica no repositório; sem JWT_SECRET, cada subida gera uma chave nova
            log.warn("JWT_SECRET não definido: usando uma chave aleatória. Os tokens deixam de valer quando a aplicação reinicia.");
            this.chave = Jwts.SIG.HS256.key().build();
            return;
        }

        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_CHAVE_BYTES) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres para o HS256");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("papel", usuario.getPapel().name()) // só informativo: o papel que vale é o do banco
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(expiracaoSegundos)))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida assinatura e expiração e devolve o email (subject).
     * Token adulterado, expirado ou de outra chave lança JwtException.
     */
    public String extrairEmail(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }
}
