package br.com.fiap.guardario;

import br.com.fiap.guardario.security.JwtService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de unidade da chave do JWT (sem Spring): o que acontece quando JWT_SECRET falta ou é curto demais.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtServiceTest {

    // no dev a chave temporária é o comportamento esperado e sai como INFO; fora do dev continua sendo um aviso
    @Test
    void chaveTemporariaSaiComoInfoNoDevEComoWarnForaDele(CapturedOutput saida) {
        new JwtService("", 3600, true, false);
        String linhaDoDev = saida.getOut().lines().filter(linha -> linha.contains("chave temporária")).findFirst().orElseThrow();
        assertThat(linhaDoDev).contains("INFO").doesNotContain("WARN");

        new JwtService("", 3600, false, false);
        String linhaForaDoDev = saida.getOut().lines().filter(linha -> linha.contains("defina JWT_SECRET")).findFirst().orElseThrow();
        assertThat(linhaForaDoDev).contains("WARN");
    }

    // HS256 pede 256 bits: uma chave curta não sobe, em vez de assinar com uma chave fraca
    @Test
    void chaveComMenosDe32CaracteresNaoSobe() {
        assertThatThrownBy(() -> new JwtService("curta-demais", 3600, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }

    // um token assinado com a chave certa, mas sem exp, valia para sempre: agora a validade é obrigatória
    @Test
    void tokenSemExpiracaoNaoVale() {
        String segredo = "chave-so-deste-teste-com-mais-de-32-caracteres";
        JwtService servico = new JwtService(segredo, 3600, false, false);
        SecretKey chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        Instant agora = Instant.now();

        String semExp = Jwts.builder().subject("cidadao@guardario.com").issuedAt(Date.from(agora))
                .signWith(chave, Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> servico.ler(semExp)).isInstanceOf(JwtException.class);

        String comExp = Jwts.builder().subject("cidadao@guardario.com").issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(60))).signWith(chave, Jwts.SIG.HS256).compact();
        assertThat(servico.ler(comExp).email()).isEqualTo("cidadao@guardario.com");
    }

    // perfil prod: sem JWT_SECRET a aplicação não sobe, em vez de assinar com uma chave que some no próximo reinício
    @Test
    void semChaveNoPerfilProdNaoSobe() {
        assertThatThrownBy(() -> new JwtService("", 3600, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET é obrigatório no perfil prod");
    }
}
