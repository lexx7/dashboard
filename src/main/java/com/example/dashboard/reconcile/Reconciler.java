package com.example.dashboard.reconcile;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Reconciler {

	private final JdbcTemplate source;

	private final JdbcTemplate mart;

	public Reconciler(@Qualifier("sourceJdbcTemplate") JdbcTemplate source,
			@Qualifier("martJdbcTemplate") JdbcTemplate mart) {
		this.source = source;
		this.mart = mart;
	}

	public ReconcileReport reconcile() {
		long sourceCount = source.queryForObject("SELECT count(*) FROM products", Long.class);
		long martCount = mart.queryForObject("SELECT count(*) FROM products_mart", Long.class);
		long mismatch = Math.abs(sourceCount - martCount);
		return new ReconcileReport(sourceCount, martCount, mismatch,
				"products: source=" + sourceCount + ", mart=" + martCount);
	}

}
