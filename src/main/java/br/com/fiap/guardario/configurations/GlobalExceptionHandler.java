package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.ErrorCustomResponse;
import br.com.fiap.guardario.dto.ErrorFieldCustom;
import br.com.fiap.guardario.dto.ValidationErrorResponse;
import br.com.fiap.guardario.exceptions.CampoInvalidoException;
import br.com.fiap.guardario.exceptions.RegraNegocioException;
import br.com.fiap.guardario.exceptions.ResourceNotFoundException;
import br.com.fiap.guardario.security.RestAccessDeniedHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    // duas requisições alteraram o mesmo registro ao mesmo tempo (@Version no Abrigo): a segunda não sobrescreve a primeira
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorCustomResponse> alteracaoSimultanea(RuntimeException ex, HttpServletRequest request) {
        return erro(HttpStatus.CONFLICT, "O registro foi alterado por outra requisição ao mesmo tempo. Tente de novo", request);
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

    // regra de um campo ou parâmetro conferida no service ou no interceptor de paginação: mesmo formato da Bean Validation
    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<ValidationErrorResponse> campoInvalido(CampoInvalidoException ex, HttpServletRequest request) {
        return erroValidacao(List.of(new ErrorFieldCustom(ex.getCampo(), ex.getMessage())), request);
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

    // Valor de tipo errado num campo ("tipo": "XYZ" fora do enum, texto no lugar de número, número que não cabe no tipo):
    // sai como erro de campo, no mesmo formato da Bean Validation. JSON quebrado não tem campo, então sai no formato padrão.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> corpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // POST ou PUT sem corpo (ou com "null"): falta o corpo, não é JSON mal formado
        if (ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            return erro(HttpStatus.BAD_REQUEST, "O corpo da requisição é obrigatório: envie o JSON com os campos da rota", request);
        }
        if (ex.getCause() instanceof JacksonException erro && !erro.getPath().isEmpty()) {
            String motivo = erro instanceof MismatchedInputException desencontro
                    ? valoresDoCampo(erro).map(aceitos -> "valor inválido; os aceitos são " + String.join(", ", aceitos))
                            .orElseGet(() -> motivo(desencontro.getTargetType()))
                    : erro instanceof InputCoercionException ? "número fora do intervalo aceito pelo campo"
                    : "valor em formato inválido";
            return erroValidacao(List.of(new ErrorFieldCustom(caminhoDoCampo(erro), motivo)), request);
        }
        return erro(HttpStatus.BAD_REQUEST, "JSON mal formado: confira aspas, vírgulas e chaves", request);
    }

    private static String caminhoDoCampo(JacksonException erro) {
        return erro.getPath().stream()
                .map(parte -> parte.getPropertyName() != null ? parte.getPropertyName() : "[" + parte.getIndex() + "]")
                .collect(Collectors.joining(".")).replace(".[", "[");
    }

    // quando o campo restringe o enum (alerta manual não aceita NORMAL), a lista vem do @Schema(allowableValues) dele,
    // a mesma que o Swagger mostra; sem isso, a mensagem listaria valores que o campo recusa
    private static Optional<List<String>> valoresDoCampo(JacksonException erro) {
        JacksonException.Reference ultimo = erro.getPath().get(erro.getPath().size() - 1);
        Object dono = ultimo.from();
        Class<?> classe = dono instanceof Class<?> tipo ? tipo : dono != null ? dono.getClass() : null;
        if (classe == null || ultimo.getPropertyName() == null) {
            return Optional.empty();
        }
        try {
            Schema schema = classe.getDeclaredField(ultimo.getPropertyName()).getAnnotation(Schema.class);
            return schema != null && schema.allowableValues().length > 0
                    ? Optional.of(List.of(schema.allowableValues())) : Optional.empty();
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }

    private static String motivo(Class<?> tipo) {
        if (tipo != null && tipo.isEnum()) {
            String aceitos = Arrays.stream(tipo.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
            return "valor inválido; os aceitos são " + aceitos;
        }
        if (tipo == Integer.class || tipo == int.class || tipo == Long.class || tipo == long.class) {
            return "deve ser um número inteiro";
        }
        if (tipo == Double.class || tipo == double.class) {
            return "deve ser um número";
        }
        if (tipo == Boolean.class || tipo == boolean.class) {
            return "deve ser true ou false";
        }
        if (tipo == Instant.class) {
            return "deve ser uma data e hora em UTC, como 2026-11-18T14:30:00Z";
        }
        if (tipo == String.class) {
            return "deve ser um texto entre aspas";
        }
        return "valor em formato inválido";
    }

    // ?status=XYZ ou /api/areas/abc: erro de um parâmetro, no mesmo formato do /api/areas/0 (@Positive), com o campo em errors
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ValidationErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return erroValidacao(List.of(new ErrorFieldCustom(ex.getName(), motivo(ex.getRequiredType()))), request);
    }

    // /api/areas/%20: o id em branco vira nulo na conversão; é erro do parâmetro, como o id 0
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ValidationErrorResponse> parametroDeRotaAusente(MissingPathVariableException ex, HttpServletRequest request) {
        return erroValidacao(List.of(new ErrorFieldCustom(ex.getVariableName(), "é obrigatório e deve ser um número inteiro")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ValidationErrorResponse> parametroAusente(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return erroValidacao(List.of(new ErrorFieldCustom(ex.getParameterName(), "é obrigatório")), request);
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

    // @PreAuthorize negado ou regra de posse; sem este handler o catch-all abaixo devolveria 500.
    // A negação do @PreAuthorize vem com "Access Denied" em inglês: sai a mesma mensagem do 403 do SecurityConfig.
    // A regra de posse lança a exceção com o motivo em português, que é mantido.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorCustomResponse> acessoNegado(AccessDeniedException ex, HttpServletRequest request) {
        String mensagem = ex instanceof AuthorizationDeniedException ? RestAccessDeniedHandler.SEM_PERMISSAO : ex.getMessage();
        return erro(HttpStatus.FORBIDDEN, mensagem, request);
    }

    // ---------- outros ----------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorCustomResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return erro(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não é aceito nesta rota", request);
    }

    // corpo enviado como texto ou sem Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorCustomResponse> tipoDeMidiaNaoSuportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Envie o corpo em JSON, com o header Content-Type: application/json", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorCustomResponse> statusExplicito(ResponseStatusException ex, HttpServletRequest request) {
        return erro(ex.getStatusCode(), ex.getReason(), request);
    }

    // erro não previsto: loga o stack trace, mas não vaza detalhes para o cliente
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorCustomResponse> erroInesperado(Exception ex, HttpServletRequest request) {
        // exceções do Spring MVC que não têm handler acima já sabem o próprio status (406, por exemplo): não viram 500
        if (ex instanceof ErrorResponse erroDoSpring) {
            HttpStatusCode status = erroDoSpring.getStatusCode();
            return erro(status, mensagemPadrao(status), request);
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente em instantes", request);
    }

    // mensagem em português para os status que chegam sem texto próprio (exceções do Spring e erros do servidor)
    static String mensagemPadrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Token ausente, inválido ou expirado. Faça login em /api/auth/login";
            case 403 -> RestAccessDeniedHandler.SEM_PERMISSAO;
            case 404 -> "Rota não encontrada";
            case 405 -> "Método HTTP não aceito nesta rota";
            case 406 -> "Formato de resposta não suportado: a API responde em JSON";
            case 413 -> "Corpo da requisição grande demais";
            case 415 -> "Envie o corpo em JSON, com o header Content-Type: application/json";
            default -> status.is5xxServerError() ? "Erro interno. Tente novamente em instantes"
                    : "Não foi possível atender a requisição";
        };
    }

    private ResponseEntity<ErrorCustomResponse> erro(HttpStatusCode status, String mensagem, HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());
        ErrorCustomResponse corpo = new ErrorCustomResponse(
                Instant.now(), httpStatus.value(), httpStatus.getReasonPhrase(), mensagem, request.getRequestURI());
        return ResponseEntity.status(httpStatus).body(corpo);
    }

    private ResponseEntity<ValidationErrorResponse> erroValidacao(List<ErrorFieldCustom> erros, HttpServletRequest request) {
        ValidationErrorResponse corpo = new ValidationErrorResponse(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "Dados inválidos: confira os campos em errors", request.getRequestURI(), erros);
        return ResponseEntity.badRequest().body(corpo);
    }
}
