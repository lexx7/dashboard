package com.example.dashboard;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

	protected static final PostgreSQLContainer<?> SOURCE_DB = new PostgreSQLContainer<>("postgres:17");

	protected static final PostgreSQLContainer<?> MART_DB = new PostgreSQLContainer<>("postgres:17");

	static {
		SOURCE_DB.start();
		MART_DB.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.source.jdbc-url", SOURCE_DB::getJdbcUrl);
		registry.add("spring.datasource.source.username", SOURCE_DB::getUsername);
		registry.add("spring.datasource.source.password", SOURCE_DB::getPassword);
		registry.add("spring.datasource.mart.jdbc-url", MART_DB::getJdbcUrl);
		registry.add("spring.datasource.mart.username", MART_DB::getUsername);
		registry.add("spring.datasource.mart.password", MART_DB::getPassword);
		registry.add("etl.scheduling.enabled", () -> "false");
	}

}
