package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.GroupCurrency;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.repository.GroupCurrencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyResolutionServiceTest {

    private GroupCurrencyRepository groupCurrencyRepository;
    private CurrencyRepository currencyRepository;
    private CurrencyResolutionService service;

    private Currency inr;
    private Currency jpy;
    private Currency usd;
    private Group group;
    private GroupCurrency yenDay1Bucket;
    private GroupCurrency jpyToInrFx;

    @BeforeEach
    void setUp() {
        groupCurrencyRepository = Mockito.mock(GroupCurrencyRepository.class);
        currencyRepository = Mockito.mock(CurrencyRepository.class);
        service = new CurrencyResolutionService(groupCurrencyRepository, currencyRepository);

        inr = currency("INR");
        jpy = currency("JPY");
        usd = currency("USD");

        group = Group.builder()
                .groupId(UUID.randomUUID())
                .name("Trip")
                .defaultCurrency(inr)
                .simplifyDebt(true)
                .build();

        // 1 YEN-Day1 unit = 1 JPY (this is a per-bucket convenience name)
        yenDay1Bucket = GroupCurrency.builder()
                .currencyId(UUID.randomUUID())
                .group(group)
                .originCurrency(jpy)
                .masterCurrency(jpy)
                .name("YEN-Day1")
                .exchangeRate(BigDecimal.ONE)
                .build();

        // 1 JPY = 0.56 INR
        jpyToInrFx = GroupCurrency.builder()
                .currencyId(UUID.randomUUID())
                .group(group)
                .originCurrency(jpy)
                .masterCurrency(inr)
                .name("FX JPY->INR")
                .exchangeRate(new BigDecimal("0.56"))
                .build();
    }

    @Test
    void accumulate_singleCurrency_groupDefault_userDefault() {
        // viewer's default = INR (same as group default)
        User viewer = User.builder()
                .userid(UUID.randomUUID())
                .defaultCurrencyId(inr.getCurrencyId())
                .build();
        Mockito.when(currencyRepository.findById(inr.getCurrencyId())).thenReturn(Optional.of(inr));

        Mockito.when(groupCurrencyRepository.findFxCandidates(group.getGroupId(),
                jpy.getCurrencyId(), inr.getCurrencyId())).thenReturn(List.of(jpyToInrFx));

        CurrencyResolutionService.Aggregator agg = service.newAggregator();
        // Paid 1000 YEN-Day1 (= 1000 JPY = 560 INR)
        service.accumulate(agg, new BigDecimal("1000"), yenDay1Bucket, group, viewer);

        assertEquals(0, new BigDecimal("560.00").compareTo(agg.getTotalInGroupDefault()),
                "Should convert 1000 JPY * 0.56 = 560 INR");
        assertEquals(0, new BigDecimal("560.00").compareTo(agg.getTotalInUserDefault()),
                "User default == group default, should equal group total");
        assertEquals(1, agg.getRawByCurrency().size());
        assertEquals(0, new BigDecimal("1000.00").compareTo(agg.getRawByCurrency().get("JPY")));
        assertTrue(agg.getMissingRates().isEmpty());
    }

    @Test
    void accumulate_missingFx_recordsMissingPair() {
        User viewer = User.builder()
                .userid(UUID.randomUUID())
                .defaultCurrencyId(inr.getCurrencyId())
                .build();
        // No FX row at all
        Mockito.when(groupCurrencyRepository.findFxCandidates(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        CurrencyResolutionService.Aggregator agg = service.newAggregator();
        service.accumulate(agg, new BigDecimal("100"), yenDay1Bucket, group, viewer);

        // Raw by currency still gets the master amount.
        assertEquals(0, new BigDecimal("100.00").compareTo(agg.getRawByCurrency().get("JPY")));
        // Group default could not be resolved.
        assertEquals(0, BigDecimal.ZERO.compareTo(agg.getTotalInGroupDefault()));
        assertNull(agg.getTotalInUserDefault(), "user default unresolved when group hop fails");
        assertEquals(List.of("JPY->INR"), agg.getMissingRates());
    }

    @Test
    void convert_invertsRateIfReverseRowExists() {
        // No JPY->USD row, but USD->JPY exists at rate 150 -> implies JPY->USD = 1/150
        GroupCurrency usdToJpy = GroupCurrency.builder()
                .currencyId(UUID.randomUUID())
                .group(group)
                .originCurrency(usd)
                .masterCurrency(jpy)
                .exchangeRate(new BigDecimal("150"))
                .build();
        Mockito.when(groupCurrencyRepository.findFxCandidates(group.getGroupId(),
                jpy.getCurrencyId(), usd.getCurrencyId())).thenReturn(List.of());
        Mockito.when(groupCurrencyRepository.findFxCandidates(group.getGroupId(),
                usd.getCurrencyId(), jpy.getCurrencyId())).thenReturn(List.of(usdToJpy));

        BigDecimal rate = service.findRate(group, jpy, usd);
        assertNotNull(rate);
        // 1 / 150 = 0.00666666...
        assertTrue(rate.compareTo(new BigDecimal("0.006")) > 0);
        assertTrue(rate.compareTo(new BigDecimal("0.007")) < 0);
    }

    @Test
    void convert_sameCurrency_returnsInputUnchanged() {
        BigDecimal rate = service.findRate(group, inr, inr);
        assertEquals(0, BigDecimal.ONE.compareTo(rate));
    }

    private Currency currency(String code) {
        return Currency.builder()
                .currencyId(UUID.randomUUID())
                .code(code)
                .name(code)
                .symbol(code)
                .isActive(true)
                .build();
    }
}
