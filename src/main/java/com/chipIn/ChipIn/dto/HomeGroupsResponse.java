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
public class HomeGroupsResponse {
    /** Aggregated in the requested display currency (usually user default). */
    private BigDecimal totalOwedToYou;
    private BigDecimal totalYouOwe;

    private UUID displayCurrencyId;
    private String displayCurrencyCode;

    /** Net by true currency across all groups (no conversion). */
    private Map<String, BigDecimal> rawByCurrency;
    private List<String> missingRates;

    private List<GroupSummaryDto> groups;

    @Data
    @Builder
    public static class GroupSummaryDto {
        private UUID groupId;
        private String groupName;
        private String groupImageUrl;

        /** Net balance for this group in the group's default currency. */
        private BigDecimal netBalanceInGroupDefault;
        private String groupCurrencyCode;

        /** Same balance projected to display currency (null if FX missing). */
        private BigDecimal netBalanceInDisplayCurrency;

        private Map<String, BigDecimal> rawByCurrency;
        private LocalDateTime lastActivity;
    }
}
