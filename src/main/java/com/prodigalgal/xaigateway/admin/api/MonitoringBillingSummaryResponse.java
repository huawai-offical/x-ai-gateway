package com.prodigalgal.xaigateway.admin.api;

public record MonitoringBillingSummaryResponse(
        long paidOrderCount,
        long paidAmountMinor,
        String currency,
        long tokenCreditsPurchased,
        long ledgerCreditTokenCredits,
        long ledgerDebitTokenCredits,
        long ledgerNetTokenCredits,
        long endingBalanceTokenCredits
) {
}
