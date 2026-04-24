package com.chipIn.ChipIn.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class HomeGroupsResponse {
    private BigDecimal totalOwedToYou; // Positive amount (people owe you)
    private BigDecimal totalYouOwe;    // Positive amount (you owe people)
    private UUID displayCurrencyId;    // The base currency used for aggregation
    private String displayCurrencyCode; // E.g. "INR"
    
    private List<GroupSummaryDto> groups;

    @Data
    @Builder
    public static class GroupSummaryDto {
        private UUID groupId;
        private String groupName;
        private String groupImageUrl;
        private BigDecimal netBalance; // Net balance for this specific group
        private LocalDateTime lastActivity;
    }
}
