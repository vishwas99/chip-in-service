package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Group dashboard payload. Currency totals are exposed in three views:
 *   - totalInGroupDefault: aggregated in Group.defaultCurrency.
 *   - totalInUserDefault:  aggregated in User.defaultCurrency. Null when any
 *                          required FX rate is missing (see missingRates).
 *   - rawByCurrency:       map of true-currency-code -> total in that currency,
 *                          summed without conversion.
 */
@Data
@Builder
public class GroupDashboardResponse {
    private UUID groupId;
    private String groupName;

    private UUID targetCurrencyId;
    private String currencyCode;            // == group default code, kept for compatibility
    private String userDefaultCurrencyCode; // viewer's default for context

    private List<UserBalanceDto> userBalances;
    private List<ExpenseSummaryDto> expenses;
    private List<SettlementSuggestionDto> settlements;

    private List<String> missingRates;      // e.g. ["JPY->INR"] — UI can prompt admin

    @Data
    @Builder
    public static class UserBalanceDto {
        private UUID userId;
        private String userName;
        private String profilePic;

        /** Net balance in group default currency. */
        private BigDecimal netBalance;
        /** Same value re-expressed in the viewer's default currency; null if unresolved. */
        private BigDecimal netBalanceInUserDefault;
        /** Net balance broken down by the true currency it originated from. */
        private Map<String, BigDecimal> rawByCurrency;
    }

    @Data
    @Builder
    public static class ExpenseSummaryDto {
        private UUID expenseId;
        private String description;
        private LocalDateTime date;
        private String category;
        private String type;
        private String createdByName;

        /** Viewer's net share in group default currency. Positive = you lent. */
        private BigDecimal yourNetShare;
        private String formattedShare;

        /** The bucket currency (e.g. "YEN-Day1"). */
        private String bucketName;
        /** The underlying ISO currency the bucket maps to. */
        private String masterCurrencyCode;
    }

    @Data
    @Builder
    public static class SettlementSuggestionDto {
        private UUID payerId;
        private String payerName;
        private UUID payeeId;
        private String payeeName;
        /** Amount in group default currency. */
        private BigDecimal amount;
    }
}
