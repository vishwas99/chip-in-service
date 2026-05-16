package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class GroupBalancesResponse {

    private UUID groupId;
    private String groupName;
    private String currencyCode;            // group default
    private String userDefaultCurrencyCode; // viewer default

    private List<UserBalanceDto> userBalances;
    private List<UserTransactionHistoryDto> transactionHistory;

    /** Net amount the viewer is owed / owes, broken down by true currency. */
    private Map<String, BigDecimal> rawByCurrency;
    private List<String> missingRates;

    @Data
    @Builder
    public static class UserBalanceDto {
        private UUID userId;
        private String userName;
        /** From viewer's perspective in group default currency. Positive = owes you. */
        private BigDecimal netBalance;
        private BigDecimal netBalanceInUserDefault;
        private Map<String, BigDecimal> rawByCurrency;
        private String balanceStatus; // "You owe", "Owes you", "Settled"
    }

    @Data
    @Builder
    public static class UserTransactionHistoryDto {
        private UUID otherUserId;
        private String otherUserName;
        /** In group default. */
        private BigDecimal netAmount;
        private BigDecimal netAmountInUserDefault;
        private Map<String, BigDecimal> rawByCurrency;
        private List<TransactionDto> transactions;
    }

    @Data
    @Builder
    public static class TransactionDto {
        private UUID transactionId;
        private String type;
        private String description;
        private LocalDateTime date;

        /** Original amount in the bucket / master currency. */
        private BigDecimal amount;
        private String currencyCode;

        /** Same amount projected into the group default currency (null if unresolved). */
        private BigDecimal amountInGroupDefault;
    }
}
