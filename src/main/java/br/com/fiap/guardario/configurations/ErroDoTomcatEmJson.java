package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Algumas requisições são recusadas pelo próprio Tomcat, antes de chegarem ao Spring: barra codificada (%2F),
 * escape inválido (%zz), byte nulo, caractere proibido na URL ou header grande demais. Nesses casos quem escreve a
 * resposta é a válvula de erro do Tomcat, que devolvia uma página HTML. Esta subclasse devolve o mesmo
 * ErrorCustomResponse em JSON do resto da API, com as mesmas condições da válvula original: só escreve quando
 * houve erro e nada foi escrito ainda.
 */
public class ErroDoTomcatEmJson extends ErrorReportValve {

    private final ObjectMapper objectMapper;

    public ErroDoTomcatEmJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void report(Request request, Response response, Throwable erro) {
        int codigo = response.getStatus();
        if (codigo < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        AtomicBoolean podeEscrever = new AtomicBoolean(true);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, podeEscrever);
        if (!podeEscrever.get()) {
            return; // a conexão já caiu
        }

        HttpStatus status = HttpStatus.resolve(codigo);
        if (status == null) {
            status = codigo >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        }
        String mensagem = status == HttpStatus.BAD_REQUEST
                ? "Requisição recusada pelo servidor: a URL ou os headers têm caractere inválido (como %2F, %00 ou |) "
                + "ou tamanho grande demais"
                : GlobalExceptionHandler.mensagemPadrao(status);
        String caminho = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorCustomResponse corpo = new ErrorCustomResponse(Instant.now(), codigo, status.getReasonPhrase(), mensagem, caminho);

        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Writer escritor = response.getReporter();
            if (escritor != null) {
                escritor.write(objectMapper.writeValueAsString(corpo));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            // a resposta não aceita mais escrita: fica só o status
        }
    }
}
