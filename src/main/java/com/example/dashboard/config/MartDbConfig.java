package com.example.dashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
public class MartDbConfig {

	@Bean
	@ConfigurationProperties("spring.datasource.mart")
	public DataSource martDataSource() {
		return new HikariDataSource();
	}

	@Bean
	public Flyway martFlyway(@Qualifier("martDataSource") DataSource martDataSource) {
		Flyway flyway = Flyway.configure()
				.dataSource(martDataSource)
				.locations("classpath:db/migration/mart")
				.load();
		flyway.migrate();
		return flyway;
	}

	@Bean
	public JdbcTemplate martJdbcTemplate(@Qualifier("martDataSource") DataSource martDataSource) {
		return new JdbcTemplate(martDataSource);
	}

	@Bean
	public DataSourceTransactionManager martTransactionManager(
			@Qualifier("martDataSource") DataSource martDataSource) {
		return new DataSourceTransactionManager(martDataSource);
	}

}
