package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import br.com.fiap.guardario.dto.ErrorFieldCustom;
import br.com.fiap.guardario.dto.ValidationErrorResponse;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- 404 ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorCustomResponse> recursoNaoEncontrado(ResourceNotFoundException ex, HttpServletRequest request) {
        return erro(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorCustomResponse> rotaNaoEncontrada(NoResourceFoundException ex, HttpServletRequest request) {
        return erro(HttpStatus.NOT_FOUND, "Rota não encontrada: " + request.getRequestURI(), request);
    }

    // ---------- 409 ----------

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErrorCustomResponse> regraNegocio(RegraNegocioException ex, HttpServletRequest request) {
        return erro(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // rede de segurança para FK ou unique violada no banco (ex.: excluir registro ainda referenciado)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorCustomResponse> integridade(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violação de integridade em {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return erro(HttpStatus.CONFLICT, "A operação conflita com dados existentes (registro duplicado ou ainda em uso)", request);
    }

    // ---------- 400: Bean Validation ----------

    // @Valid no @RequestBody: junta os erros de campo e os de classe (@CotasCrescentes, @OcupacaoDentroDaCapacidade)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> corpoInvalido(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorFieldCustom> erros = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.add(new ErrorFieldCustom(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> erros.add(new ErrorFieldCustom(e.getObjectName(), e.getDefaultMessage())));
        return erroValidacao(erros, request);
    }

    // @Positive no @PathVariable com @Validated na classe
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> parametroInvalido(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorFieldCustom> erros = ex.getConstraintViolations().stream()
                .map(v -> {
                    String caminho = v.getPropertyPath().toString();
                    String campo = caminho.substring(caminho.lastIndexOf('.') + 1);
                    return new ErrorFieldCustom(campo, v.getMessage());
                })
                .toList();
        return erroValidacao(erros, request);
    }

    // validação de método feita pelo próprio Spring MVC (quando a classe não tem @Validated)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ValidationErrorResponse> metodoInvalido(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ErrorFieldCustom> erros = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors errosDoObjeto) {
                errosDoObjeto.getFieldErrors()
                        .forEach(e -> erros.add(new ErrorFieldCustom(e.getField(), e.getDefaultMessage())));
                errosDoObjeto.getGlobalErrors()
                        .forEach(e -> erros.add(new ErrorFieldCustom(e.getObjectName(), e.getDefaultMessage())));
            } else {
                String parametro = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors()
                        .forEach(e -> erros.add(new ErrorFieldCustom(parametro, e.getDefaultMessage())));
            }
        }
        return erroValidacao(erros, request);
    }

    // ---------- 400: requisição mal formada ----------

    // JSON quebrado ou valor fora do enum no corpo (ex.: "tipo": "XYZ")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorCustomResponse> corpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido: confira o JSON e os valores dos campos", request);
    }

    // ?status=XYZ ou /api/areas/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorCustomResponse> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "': " + ex.getValue(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorCustomResponse> parametroAusente(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName(), request);
    }

    // ?sort=campoQueNaoExiste
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorCustomResponse> ordenacaoInvalida(PropertyReferenceException ex, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, "Campo de ordenação inválido: " + ex.getPropertyName(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorCustomResponse> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ---------- 401 e 403 vindos do controller ou do service ----------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorCustomResponse> naoAutenticado(AuthenticationException ex, HttpServletRequest request) {
        // mensagem genérica no login para não revelar quais emails estão cadastrados
        String mensagem = ex instanceof BadCredentialsException ? "Email ou senha inválidos" : ex.getMessage();
        return erro(HttpStatus.UNAUTHORIZED, mensagem, request);
    }

    // @PreAuthorize negado ou regra de posse; sem este handler o catch-all abaixo devolveria 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorCustomResponse> acessoNegado(AccessDeniedException ex, HttpServletRequest request) {
        return erro(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação", request);
    }

    // ---------- outros ----------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorCustomResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return erro(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não é aceito nesta rota", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorCustomResponse> statusExplicito(ResponseStatusException ex, HttpServletRequest request) {
        return erro(ex.getStatusCode(), ex.getReason(), request);
    }

    // erro não previsto: loga o stack trace, mas não vaza detalhes para o cliente
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorCustomResponse> erroInesperado(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente em instantes", request);
    }

    private ResponseEntity<ErrorCustomResponse> erro(HttpStatusCode status, String mensagem, HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());
        ErrorCustomResponse corpo = new ErrorCustomResponse(
                Instant.now(), httpStatus.value(), httpStatus.getReasonPhrase(), mensagem, request.getRequestURI());
        return ResponseEntity.status(httpStatus).body(corpo);
    }

    private ResponseEntity<ValidationErrorResponse> erroValidacao(List<ErrorFieldCustom> erros, HttpServletRequest request) {
        ValidationErrorResponse corpo = new ValidationErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "Dados inválidos", request.getRequestURI(), erros);
        return ResponseEntity.badRequest().body(corpo);
    }
}
