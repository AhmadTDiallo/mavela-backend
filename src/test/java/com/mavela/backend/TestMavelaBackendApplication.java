package com.mavela.backend;

import org.springframework.boot.SpringApplication;

public class TestMavelaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(MavelaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
