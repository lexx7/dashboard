package com.example.dashboard.source;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SourceReader {

	private final JdbcTemplate jdbc;

	private final Duration lagWindow;

	public SourceReader(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbc,
			@Value("${etl.lag-window}") Duration lagWindow) {
		this.jdbc = jdbc;
		this.lagWindow = lagWindow;
	}

	public List<SourceProduct> fetchSince(Instant cursor, String afterSku, int limit) {
		return jdbc.query("""
				SELECT sku, name, price, updated_at FROM products
				WHERE (updated_at, sku) > (?, ?)
				  AND updated_at < now() - make_interval(secs => ?)
				ORDER BY updated_at, sku
				LIMIT ?
				""", (rs, rowNum) -> new SourceProduct(rs.getString("sku"), rs.getString("name"),
				rs.getBigDecimal("price"), rs.getObject("updated_at", OffsetDateTime.class).toInstant()),
				java.sql.Timestamp.from(cursor), afterSku, lagWindow.toSeconds(), limit);
	}

}
