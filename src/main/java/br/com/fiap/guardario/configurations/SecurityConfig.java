package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.repository.UsuarioRepository;
import br.com.fiap.guardario.security.CorsComErroEmJson;
import br.com.fiap.guardario.security.JsonErrorWriter;
import br.com.fiap.guardario.security.JwtAuthenticationFilter;
import br.com.fiap.guardario.security.JwtService;
import br.com.fiap.guardario.security.RestAccessDeniedHandler;
import br.com.fiap.guardario.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Área de URL fica aqui; a ação de cada papel fica no @PreAuthorize dos controllers;
 * a regra de posse (o relato é seu?) fica no service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] LEITURA_PUBLICA = {"/api/areas/**", "/api/estacoes/**", "/api/alertas/**", "/api/abrigos/**"};

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
                                           JsonErrorWriter jsonErrorWriter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless com token no header: não há cookie de sessão para o CSRF explorar
                // o CORS roda no CorsComErroEmJson, adicionado abaixo, para a origem recusada receber o JSON padrão
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/h2-console/**").permitAll()
                        // área de URL de cada papel: quem não tem o papel recebe 403 aqui, antes da validação do corpo
                        // (o @Valid roda antes do @PreAuthorize, que continua em cada método como segunda camada)
                        .requestMatchers(HttpMethod.POST, "/api/areas", "/api/estacoes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/areas/*", "/api/estacoes/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/areas/*", "/api/estacoes/*", "/api/alertas/*", "/api/abrigos/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/estacoes/*/leituras", "/api/alertas", "/api/abrigos",
                                "/api/abrigos/*/entradas", "/api/abrigos/*/saidas").hasAnyRole("AGENTE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/alertas/*", "/api/abrigos/*").hasAnyRole("AGENTE", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/alertas/*/encerrar", "/api/abrigos/*/status",
                                "/api/relatos/*/status").hasAnyRole("AGENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/relatos").hasAnyRole("AGENTE", "ADMIN")
                        .requestMatchers(HttpMethod.HEAD, "/api/relatos").hasAnyRole("AGENTE", "ADMIN")
                        // informação de segurança é pública: nível do rio, alertas e abrigos não exigem login.
                        // O HEAD é o GET sem corpo (o Spring MVC atende pelo mesmo método), então segue a mesma regra
                        .requestMatchers(HttpMethod.GET, LEITURA_PUBLICA).permitAll()
                        .requestMatchers(HttpMethod.HEAD, LEITURA_PUBLICA).permitAll()
                        // ver um cadastro pelo id: qualquer logado, com a posse conferida no service; o resto da área é do ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/*").authenticated()
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint) // 401
                        .accessDeniedHandler(accessDeniedHandler)           // 403
                )
                // só por causa do console do H2 (perfil dev), que roda dentro de um frame
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(new CorsComErroEmJson(corsConfigurationSource, jsonErrorWriter), LogoutFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS para o app Mobile (Flutter web e emulador); as origens vêm da variável CORS_ALLOWED_ORIGINS
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String[] origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(origens).map(String::trim).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Location")); // o app lê o Location do 201
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
