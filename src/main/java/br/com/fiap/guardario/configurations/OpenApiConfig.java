package br.com.fiap.guardario.configurations;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/*
 * Todas as rotas ganham o cadeado do bearerAuth; as públicas tiram o cadeado com @SecurityRequirements vazio.
 * No botão Authorize do Swagger UI cole só o token, sem a palavra Bearer.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GuardaRio API",
                version = "1.0.0",
                description = """
                        API do GuardaRio, alerta comunitário de enchentes (Global Solution 2026/2, FIAP).
                        Estações IoT medem o nível do rio, a API classifica o risco, gera alertas automáticos,
                        mostra abrigos com vaga e recebe relatos dos moradores com prioridade calculada.

                        Usuários de teste (perfil dev): admin@guardario.com / Admin@123,
                        agente@guardario.com / Agente@123 e cidadao@guardario.com / Cidadao@123.
                        """,
                contact = @Contact(name = "Equipe GuardaRio")),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token devolvido por POST /api/auth/login (vale 1 hora)")
public class OpenApiConfig {
}
