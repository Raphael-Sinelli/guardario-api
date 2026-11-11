package br.com.fiap.guardario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o Tomcat de verdade, numa porta livre, e manda a requisição crua por socket: o MockMvc não passa pelo
 * Tomcat, e estes erros acontecem nele, antes do Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServidorRealIntegrationTest {

    @LocalServerPort
    private int porta;

    // URL que o Tomcat recusa antes do Spring: o erro sai no JSON padrão, e não na página HTML do Tomcat
    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/api/relatos%2F", "/api/areas/%zz", "/api/areas/%00", "/api/areas/%5C", "/api/areas/x|y"})
    void urlRecusadaPeloTomcatSaiEmJson(String caminho) throws IOException {
        String resposta = enviar("GET " + caminho + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(resposta).startsWith("HTTP/1.1 400");
        assertThat(resposta).containsIgnoringCase("Content-Type: application/json");
        assertThat(resposta).contains("\"status\":400").contains("\"error\":\"Bad Request\"")
                .contains("Requisição recusada pelo servidor").doesNotContain("<html");
    }

    @Test
    void headerGrandeDemaisSaiEmJson() throws IOException {
        String resposta = enviar("GET /api/areas HTTP/1.1\r\nHost: localhost\r\nX-Grande: " + "a".repeat(20_000)
                + "\r\nConnection: close\r\n\r\n");

        assertThat(resposta).startsWith("HTTP/1.1 400");
        assertThat(resposta).contains("\"status\":400").doesNotContain("<html");
    }

    // o TRACE é recusado pelo Tomcat, que punha no Allow os métodos do servlet inteiro; o Allow lista os da rota
    @Test
    void traceDevolve405ComOAllowDaRota() throws IOException {
        String resposta = enviar("TRACE /api/areas HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(resposta).startsWith("HTTP/1.1 405");
        assertThat(resposta).containsIgnoringCase("Allow: GET,HEAD,POST,OPTIONS");
        assertThat(resposta).contains("\"status\":405");
    }

    // TRACE fora das rotas da API: arquivo estático que existe tem o Allow dele; caminho em que nada responde é 404,
    // como num GET com token; o /error, que é a página de erro e não uma rota, também
    @Test
    void traceForaDasRotasDaApi() throws IOException {
        String swagger = enviar("TRACE /swagger-ui/index.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertThat(swagger).startsWith("HTTP/1.1 405").containsIgnoringCase("Allow: GET,HEAD,OPTIONS");

        String inexistente = enviar("TRACE /api/rota-que-nao-existe HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertThat(inexistente).startsWith("HTTP/1.1 404").contains("\"status\":404")
                .contains("Rota não encontrada: /api/rota-que-nao-existe").doesNotContainIgnoringCase("Allow:");

        String erro = enviar("TRACE /error HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertThat(erro).startsWith("HTTP/1.1 404");
    }

    // no servidor de verdade, o 404 do OPTIONS onde nada responde sai no JSON padrão, sem Allow
    @Test
    void optionsOndeNadaRespondeDa404NoJsonPadrao() throws IOException {
        String erro = enviar("OPTIONS /error HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertThat(erro).startsWith("HTTP/1.1 404").contains("Rota não encontrada: /error").doesNotContainIgnoringCase("Allow:");
    }

    // o caminho normal continua igual: o erro tratado pelo Spring não passa pela válvula
    @Test
    void erroTratadoPeloSpringContinuaComAMensagemDele() throws IOException {
        String resposta = enviar("GET /api/areas/999999 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(resposta).startsWith("HTTP/1.1 404");
        assertThat(resposta).contains("\"status\":404").contains("999999");
    }

    private String enviar(String requisicao) throws IOException {
        try (Socket socket = new Socket("localhost", porta)) {
            socket.setSoTimeout(10_000);
            OutputStream saida = socket.getOutputStream();
            saida.write(requisicao.getBytes(StandardCharsets.UTF_8));
            saida.flush();
            ByteArrayOutputStream lido = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(lido);
            return lido.toString(StandardCharsets.UTF_8);
        }
    }
}
