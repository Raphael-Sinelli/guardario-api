package br.com.fiap.guardario;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com uma chave conhecida no teste, dá para assinar um token igual ao da API, só que vencido,
 * e conferir que o filtro JWT recusa pela expiração (e não por assinatura errada).
 */
@TestPropertySource(properties = "app.jwt.secret=" + TokenExpiradoIntegrationTest.SEGREDO)
class TokenExpiradoIntegrationTest extends IntegracaoBase {

    static final String SEGREDO = "chave-de-teste-com-mais-de-32-caracteres";

    private String tokenDaCida(Instant emitidoEm, Instant expiraEm) {
        return Jwts.builder()
                .subject(CIDADAO)
                .issuedAt(Date.from(emitidoEm))
                .expiration(Date.from(expiraEm))
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void tokenVencidoDevolve401ETokenValidoComAMesmaChaveDevolve200() throws Exception {
        Instant agora = Instant.now();

        String vencido = tokenDaCida(agora.minusSeconds(7200), agora.minusSeconds(3600));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(vencido)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // prova de que o 401 veio da expiração: o mesmo token, dentro do prazo, entra
        String valido = tokenDaCida(agora, agora.plusSeconds(3600));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(valido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CIDADAO));
    }
}
