package br.com.fiap.guardario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GuardaRioApplication {

	public static void main(String[] args) {
		SpringApplication.run(GuardaRioApplication.class, args);
	}

}
