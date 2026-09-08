package com.example.dashboard.source;

import java.math.BigDecimal;
import java.time.Instant;

public record SourceProduct(String sku, String name, BigDecimal price, Instant updatedAt) {

}
