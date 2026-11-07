package br.com.fiap.guardario.configurations;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O @CacheEvict de um método @Transactional limpa o cache quando o método termina, antes do commit. Nesse intervalo,
 * uma leitura de outra requisição ainda vê o banco antigo e guarda o dado velho no cache de novo, por até 10 minutos.
 * Este embrulho adia o evict e o put para depois do commit (e não faz nada no rollback).
 *
 * O CaffeineCacheManager continua sendo criado pelo Spring Boot, com o spec do application.yml; aqui ele só é
 * envolvido no TransactionAwareCacheManagerProxy. O Actuator desembrulha o cache para ler as métricas do Caffeine,
 * então cache.gets continua aparecendo como antes.
 */
@Configuration
public class CacheConfig {

    // static: o BeanPostProcessor precisa existir antes dos outros beans, inclusive do CacheManager
    @Bean
    public static BeanPostProcessor cacheQueRespeitaATransacao() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nome) {
                return bean instanceof CaffeineCacheManager caffeine ? new TransactionAwareCacheManagerProxy(caffeine) : bean;
            }
        };
    }
}
