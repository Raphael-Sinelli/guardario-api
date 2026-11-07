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
                      @Value("${app.jwt.expiration-seconds:3600}") long expiracaoSegundos,
                      @Value("${app.jwt.chave-temporaria-esperada:false}") boolean chaveTemporariaEsperada,
                      @Value("${app.jwt.chave-obrigatoria:false}") boolean chaveObrigatoria) {
        this.expiracaoSegundos = expiracaoSegundos;

        if ((segredo == null || segredo.isBlank()) && chaveObrigatoria) {
            // perfil prod: uma chave aleatória invalidaria todos os tokens a cada reinício e não serviria para mais de
            // uma instância. Melhor não subir do que subir errado
            throw new IllegalStateException("JWT_SECRET é obrigatório no perfil prod: defina a variável com 32 caracteres ou mais");
        }
        if (segredo == null || segredo.isBlank()) {
            // nenhuma chave fica no repositório; sem JWT_SECRET, cada subida gera uma chave nova
            this.chave = Jwts.SIG.HS256.key().build();
            if (chaveTemporariaEsperada) {
                // perfil dev: é o comportamento esperado, por isso INFO e não WARN
                log.info("Perfil dev sem JWT_SECRET: gerada uma chave temporária para esta subida. Nenhuma chave fica no "
                        + "repositório; os tokens emitidos deixam de valer quando a aplicação reinicia.");
            } else {
                log.warn("JWT_SECRET não definido: usando uma chave aleatória. Os tokens deixam de valer quando a aplicação "
                        + "reinicia; defina JWT_SECRET, com 32 caracteres ou mais, fora do desenvolvimento.");
            }
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
