package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.GroupCurrency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO projection of a GroupCurrency row. `kind = BUCKET` means it's a real
 * expense bucket (origin == master, or origin is null for legacy rows).
 * `kind = FX_RATE` means it's a true-currency conversion row used only by
 * the resolver.
 */
@Data
@Builder
public class GroupCurrencyResponse {

    public enum Kind { BUCKET, FX_RATE }

    private UUID groupCurrencyId;
    private UUID groupId;
    private String name;

    private UUID masterCurrencyId;
    private String masterCurrencyCode;

    private UUID originCurrencyId;
    private String originCurrencyCode;

    private BigDecimal exchangeRate;
    private Kind kind;

    private LocalDateTime createdAt;
    private UUID createdByUserId;

    public static GroupCurrencyResponse from(GroupCurrency gc) {
        boolean isFx = gc.getOriginCurrency() != null
                && !gc.getOriginCurrency().getCurrencyId().equals(gc.getMasterCurrency().getCurrencyId());
        return GroupCurrencyResponse.builder()
                .groupCurrencyId(gc.getCurrencyId())
                .groupId(gc.getGroup().getGroupId())
                .name(gc.getName())
                .masterCurrencyId(gc.getMasterCurrency().getCurrencyId())
                .masterCurrencyCode(gc.getMasterCurrency().getCode())
                .originCurrencyId(gc.getOriginCurrency() == null ? null : gc.getOriginCurrency().getCurrencyId())
                .originCurrencyCode(gc.getOriginCurrency() == null ? null : gc.getOriginCurrency().getCode())
                .exchangeRate(gc.getExchangeRate())
                .kind(isFx ? Kind.FX_RATE : Kind.BUCKET)
                .createdAt(gc.getCreatedAt())
                .createdByUserId(gc.getCreatedBy() == null ? null : gc.getCreatedBy().getUserid())
                .build();
    }
}
