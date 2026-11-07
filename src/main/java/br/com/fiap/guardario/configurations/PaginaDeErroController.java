package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * O /error recebe o que acontece fora do Spring MVC: TRACE recusado pelo Tomcat, URL que o servidor nem consegue ler.
 * O controller padrão do Spring Boot responde com outro formato e em inglês; este responde com o ErrorCustomResponse
 * de toda a API. Chamado direto, o /error é tratado como rota que não existe.
 * Fica fora do Swagger (@Hidden): não é rota da API, é o destino interno dos erros do servidor.
 */
@Hidden
@RestController
public class PaginaDeErroController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorCustomResponse> erro(HttpServletRequest request) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = codigo instanceof Integer numero ? HttpStatus.resolve(numero) : null;
        if (status == null) {
            status = HttpStatus.NOT_FOUND;
        }
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String caminho = uri != null ? uri.toString() : request.getRequestURI();
        ErrorCustomResponse corpo = new ErrorCustomResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                GlobalExceptionHandler.mensagemPadrao(status), caminho);
        return ResponseEntity.status(status).body(corpo);
    }
}
