package br.com.fiap.guardario.configurations;

import br.com.fiap.guardario.dto.AlterarPapelRequest;
import br.com.fiap.guardario.dto.AlterarStatusAbrigoRequest;
import br.com.fiap.guardario.dto.AlterarStatusRelatoRequest;
import br.com.fiap.guardario.dto.EstacaoRequest;
import br.com.fiap.guardario.dto.RelatoRequest;
import br.com.fiap.guardario.model.Papel;
import br.com.fiap.guardario.model.StatusAbrigo;
import br.com.fiap.guardario.model.StatusEstacao;
import br.com.fiap.guardario.model.StatusRelato;
import br.com.fiap.guardario.model.TipoRelato;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsConfiguration;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;

/**
 * HAL continua o formato padrão (e o de application/json). O HAL-FORMS entra para o cliente que pedir
 * Accept: application/prs.hal-forms+json: a mesma resposta, mais os templates de cada ação (método, alvo e campos do
 * corpo), que os assemblers penduram no link self com a classe Acoes. Nos campos de enum, o template lista os valores
 * aceitos; no status do abrigo, só os que o cliente pode mandar (LOTADO é calculado pela ocupação).
 */
@Configuration
@EnableHypermediaSupport(type = {HypermediaType.HAL, HypermediaType.HAL_FORMS})
public class HateoasConfig {

    @Bean
    public HalFormsConfiguration halFormsConfiguration(HalConfiguration hal) {
        return new HalFormsConfiguration(hal)
                .withOptions(AlterarStatusRelatoRequest.class, "status", campo -> HalFormsOptions.inline((Object[]) StatusRelato.values()))
                .withOptions(AlterarStatusAbrigoRequest.class, "status", campo -> HalFormsOptions.inline(StatusAbrigo.ABERTO, StatusAbrigo.FECHADO))
                .withOptions(AlterarPapelRequest.class, "papel", campo -> HalFormsOptions.inline((Object[]) Papel.values()))
                .withOptions(RelatoRequest.class, "tipo", campo -> HalFormsOptions.inline((Object[]) TipoRelato.values()))
                .withOptions(EstacaoRequest.class, "status", campo -> HalFormsOptions.inline((Object[]) StatusEstacao.values()));
    }
}
