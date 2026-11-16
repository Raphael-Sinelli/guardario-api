package br.com.fiap.guardario;

import br.com.fiap.guardario.configurations.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de unidade do GlobalExceptionHandler (sem Spring), na rede de segurança do que o banco recusa.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(null);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/relatos");

    // valor que o banco não aceita (SQLState da classe 22: texto longo demais, caractere inválido) é erro do pedido,
    // 400; antes saía 409 dizendo "registro duplicado ou ainda em uso". Unique ou FK violada continua sendo 409
    @Test
    void valorRecusadoPeloBancoDa400EViolacaoDeUnicidadeContinua409() {
        assertThat(handler.integridade(recusa("22001"), request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.integridade(recusa("22021"), request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.integridade(recusa("23505"), request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.integridade(recusa("23503"), request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private static DataIntegrityViolationException recusa(String sqlState) {
        return new DataIntegrityViolationException("recusado", new SQLException("recusado pelo banco", sqlState));
    }
}
