package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class HomeFriendsResponse {
    private BigDecimal totalOwedToYou;
    private BigDecimal totalYouOwe;

    private UUID displayCurrencyId;
    private String displayCurrencyCode;

    private Map<String, BigDecimal> rawByCurrency;
    private List<String> missingRates;

    private List<FriendSummaryDto> friends;

    @Data
    @Builder
    public static class FriendSummaryDto {
        private UUID friendId;
        private String friendName;
        private String friendProfilePic;
        /** In display currency. Positive: they owe you. */
        private BigDecimal netBalance;
        private Map<String, BigDecimal> rawByCurrency;
    }
}
