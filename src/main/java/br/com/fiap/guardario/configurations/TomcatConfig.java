package br.com.fiap.guardario.configurations;

import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Troca a válvula de erro do Tomcat (página HTML) pela ErroDoTomcatEmJson. O Spring Boot põe a válvula padrão no
 * host; este customizer roda depois do dele, tira a padrão e põe a nossa, e informa ao host o nome da classe para
 * ele não acrescentar outra na subida.
 */
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> erroDoTomcatEmJson(ObjectMapper objectMapper) {
        return fabrica -> fabrica.addContextCustomizers(contexto -> {
            Pipeline pipeline = contexto.getParent().getPipeline();
            for (Valve valvula : pipeline.getValves()) {
                if (valvula instanceof ErrorReportValve) {
                    pipeline.removeValve(valvula);
                }
            }
            pipeline.addValve(new ErroDoTomcatEmJson(objectMapper));
            if (contexto.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ErroDoTomcatEmJson.class.getName());
            }
        });
    }
}
