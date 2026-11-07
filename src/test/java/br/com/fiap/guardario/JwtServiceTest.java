package br.com.fiap.guardario;

import br.com.fiap.guardario.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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

    // perfil prod: sem JWT_SECRET a aplicação não sobe, em vez de assinar com uma chave que some no próximo reinício
    @Test
    void semChaveNoPerfilProdNaoSobe() {
        assertThatThrownBy(() -> new JwtService("", 3600, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET é obrigatório no perfil prod");
    }
}
