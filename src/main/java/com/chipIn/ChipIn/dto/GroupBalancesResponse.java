package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupBalancesResponse {

    private UUID groupId;
    private String groupName;
    private String currencyCode;
    private List<UserBalanceDto> userBalances;
    private List<UserTransactionHistoryDto> transactionHistory;

    @Data
    @Builder
    public static class UserBalanceDto {
        private UUID userId;
        private String userName;
        private BigDecimal netBalance; // Positive = owed to user, Negative = user owes
        private String balanceStatus; // "You owe", "Owes you", "Settled"
    }

    @Data
    @Builder
    public static class UserTransactionHistoryDto {
        private UUID otherUserId;
        private String otherUserName;
        private BigDecimal netAmount; // Positive = other user owes you, Negative = you owe other user
        private List<TransactionDto> transactions;
    }

    @Data
    @Builder
    public static class TransactionDto {
        private UUID transactionId;
        private String type; // "EXPENSE" or "SETTLEMENT"
        private String description;
        private LocalDateTime date;
        private BigDecimal amount; // Positive = you received, Negative = you paid
        private String currencyCode;
    }
}
