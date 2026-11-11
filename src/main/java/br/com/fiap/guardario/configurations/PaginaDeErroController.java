package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import br.com.fiap.guardario.security.JsonErrorWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

/**
 * O /error recebe o que acontece fora do Spring MVC: TRACE recusado pelo Tomcat, URL que o servidor nem consegue ler.
 * O controller padrão do Spring Boot responde com outro formato e em inglês; este responde com o ErrorCustomResponse
 * de toda a API. Chamado direto, o /error é tratado como rota que não existe.
 * Fica fora do Swagger (@Hidden): não é rota da API, é o destino interno dos erros do servidor.
 */
@Hidden
@RestController
public class PaginaDeErroController implements ErrorController {

    private final RotasDaApi rotas;

    public PaginaDeErroController(RotasDaApi rotas) {
        this.rotas = rotas;
    }

    @RequestMapping("/error")
    public ResponseEntity<ErrorCustomResponse> erro(HttpServletRequest request, HttpServletResponse response) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = codigo instanceof Integer numero ? HttpStatus.resolve(numero) : null;
        if (status == null) {
            status = HttpStatus.NOT_FOUND;
        }
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String caminho = uri != null ? uri.toString() : request.getRequestURI();

        // o TRACE chega aqui recusado pelo Tomcat, que pôs no Allow os métodos do servlet inteiro. No caminho do Spring
        // vale o Allow da rota; se nada responde no caminho, é 404, como seria num GET. No caminho de outro servlet (o
        // console do H2, no perfil dev), fica o Allow do Tomcat, que é o daquele servlet
        if (status == HttpStatus.METHOD_NOT_ALLOWED && caminhoDoSpring(request)) {
            Set<HttpMethod> aceitos = rotas.metodosAceitos(caminho.substring(request.getContextPath().length()));
            if (aceitos.isEmpty()) {
                status = HttpStatus.NOT_FOUND;
                response.setHeader(HttpHeaders.ALLOW, null);
            } else {
                HttpHeaders allow = new HttpHeaders();
                allow.setAllow(aceitos);
                response.setHeader(HttpHeaders.ALLOW, allow.getFirst(HttpHeaders.ALLOW));
            }
        }

        // o 404 diz o caminho, como o 404 de uma rota que não existe pedida com GET
        String mensagem = status == HttpStatus.NOT_FOUND
                ? "Rota não encontrada: " + caminho
                : GlobalExceptionHandler.mensagemPadrao(status);
        ErrorCustomResponse corpo = new ErrorCustomResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                mensagem, caminho);
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            resposta.header(HttpHeaders.WWW_AUTHENTICATE, JsonErrorWriter.desafioBearer(request));
        }
        return resposta.body(corpo);
    }

    // o erro aconteceu numa requisição do DispatcherServlet (e não de outro servlet, como o console do H2)
    private static boolean caminhoDoSpring(HttpServletRequest request) {
        Object servlet = request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
        return servlet == null || DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME.equals(servlet);
    }
}
