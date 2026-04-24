package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class HomeFriendsResponse {
    private BigDecimal totalOwedToYou;
    private BigDecimal totalYouOwe;
    private UUID displayCurrencyId;
    private String displayCurrencyCode;
    
    private List<FriendSummaryDto> friends;

    @Data
    @Builder
    public static class FriendSummaryDto {
        private UUID friendId;
        private String friendName;
        private String friendProfilePic;
        private BigDecimal netBalance; // Positive: They owe you, Negative: You owe them
    }
}
