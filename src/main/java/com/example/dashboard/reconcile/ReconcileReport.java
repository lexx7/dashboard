package com.example.dashboard.reconcile;

public record ReconcileReport(long sourceCount, long martCount, long mismatch, String details) {

}
