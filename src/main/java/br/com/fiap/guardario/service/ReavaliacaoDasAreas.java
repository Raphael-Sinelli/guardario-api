package br.com.fiap.guardario.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// a estação que para de mandar leitura não dispara o recálculo do nível: a cada minuto, a área solta o risco velho
@Component
@RequiredArgsConstructor
public class ReavaliacaoDasAreas {

    private final AreaService areaService;

    @Scheduled(fixedDelay = 60000)
    public void reavaliar() {
        areaService.reavaliarNiveis();
    }
}
