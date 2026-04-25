package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupDashboardResponse {
    // 1. Group Meta Data
    private UUID groupId;
    private String groupName;
    private UUID targetCurrencyId; // Added targetCurrencyId (e.g. INR ID)
    private String currencyCode; // Optional: Keep for display purposes if needed

    // 2. The Totals (Who owes what in Total)
    private List<UserBalanceDto> userBalances;

    // 3. The Timeline (List of expenses with "My Stand")
    private List<ExpenseSummaryDto> expenses;

    // --- Inner DTOs (Or can be standalone) ---

    @Data
    @Builder
    public static class UserBalanceDto {
        private UUID userId;
        private String userName;
        private String profilePic;
        private BigDecimal netBalance; // +500 (Owed) or -200 (Owes)
    }

    @Data
    @Builder
    public static class ExpenseSummaryDto {
        private UUID expenseId;
        private String description;
        private LocalDateTime date;
        private String category; // FOOD, TRAVEL
        private String type;     // EXPENSE vs SETTLEMENT
        private String createdByName; // The name of the user who created the expense

        // "You lent ₹500" or "You borrowed ₹200"
        private BigDecimal yourNetShare;
        private String formattedShare; // "You lent" or "You owe"
    }

    // 4. Instructions: Who should pay whom?
    private List<SettlementSuggestionDto> settlements;

    @Data
    @Builder
    public static class SettlementSuggestionDto {
        private UUID payerId;
        private String payerName;
        private UUID payeeId;
        private String payeeName;
        private BigDecimal amount;
    }
}
