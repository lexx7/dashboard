package com.example.dashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

@Configuration
public class SourceDbConfig {

	@Bean
	@ConfigurationProperties("spring.datasource.source")
	public DataSource sourceDataSource() {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setReadOnly(true);
		return dataSource;
	}

	@Bean
	public Flyway sourceFlyway(@Qualifier("sourceDataSource") DataSource sourceDataSource) {
		// Пул источника read-only (T007); миграции выполняются через обёртку,
		// снимающую флаг read-only с соединения.
		DataSource writable = new DelegatingDataSource(sourceDataSource) {
			@Override
			public Connection getConnection() throws SQLException {
				Connection connection = super.getConnection();
				connection.setReadOnly(false);
				return connection;
			}
		};
		Flyway flyway = Flyway.configure()
				.dataSource(writable)
				.locations("classpath:db/migration/source")
				.load();
		flyway.migrate();
		return flyway;
	}

	@Bean
	public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource sourceDataSource) {
		return new JdbcTemplate(sourceDataSource);
	}

}
