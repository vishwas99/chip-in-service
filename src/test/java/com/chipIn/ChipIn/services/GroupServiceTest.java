package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dto.GroupDashboardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCalculateSettlements_SimpleTwoUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(userA, new BigDecimal("-100.00")); // Owes 100
        balances.put(userB, new BigDecimal("100.00"));  // Owed 100

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        assertEquals(1, result.size());
        assertEquals(userA, result.get(0).getPayerId());
        assertEquals(userB, result.get(0).getPayeeId());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.get(0).getAmount()));
    }

    @Test
    void testCalculateSettlements_CircularDebt() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        // A owes B 50, B owes C 50, C owes A 50.
        // In a net balance world:
        // A: +50 (from C) -50 (to B) = 0
        // B: +50 (from A) -50 (to C) = 0
        // C: +50 (from B) -50 (to A) = 0

        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(userA, BigDecimal.ZERO);
        balances.put(userB, BigDecimal.ZERO);
        balances.put(userC, BigDecimal.ZERO);

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        assertEquals(0, result.size(), "Circular debts should result in zero settlements if net is zero");
    }

    @Test
    void testCalculateSettlements_ThreeUserChain() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        // A owes B 100, B owes C 100.
        // Net:
        // A: -100
        // B: 0 (+100 - 100)
        // C: +100
        // Simplified: A owes C 100.

        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(userA, new BigDecimal("-100.00"));
        balances.put(userB, BigDecimal.ZERO);
        balances.put(userC, new BigDecimal("100.00"));

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        assertEquals(1, result.size());
        assertEquals(userA, result.get(0).getPayerId());
        assertEquals(userC, result.get(0).getPayeeId());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.get(0).getAmount()));
    }

    @Test
    void testCalculateSettlements_MultipleDebtorsOneCreditor() {
        UUID debtor1 = UUID.randomUUID();
        UUID debtor2 = UUID.randomUUID();
        UUID creditor = UUID.randomUUID();

        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(debtor1, new BigDecimal("-30.00"));
        balances.put(debtor2, new BigDecimal("-70.00"));
        balances.put(creditor, new BigDecimal("100.00"));

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        assertEquals(2, result.size());
        BigDecimal totalSettled = result.stream()
                .map(GroupDashboardResponse.SettlementSuggestionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        assertEquals(0, new BigDecimal("100.00").compareTo(totalSettled));
    }

    @Test
    void testCalculateSettlements_ComplexSplit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        // A: -50, B: -50, C: +75, D: +25
        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(a, new BigDecimal("-50.00"));
        balances.put(b, new BigDecimal("-50.00"));
        balances.put(c, new BigDecimal("75.00"));
        balances.put(d, new BigDecimal("25.00"));

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        // Possible outcomes:
        // A -> C (50), B -> C (25), B -> D (25)
        // A -> C (50), B -> D (25), B -> C (25)
        
        assertEquals(3, result.size(), "Should take 3 transactions to settle 2 debtors and 2 creditors in this case");
        
        BigDecimal totalSettled = result.stream()
                .map(GroupDashboardResponse.SettlementSuggestionDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00").compareTo(totalSettled));
    }

    @Test
    void testCalculateSettlements_RoundingEdgeCase() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        // 10.00 split 3 ways -> 3.33, 3.33, 3.34
        // Payer A: +6.67
        // Debtor B: -3.33
        // Debtor C: -3.34
        Map<UUID, BigDecimal> balances = new HashMap<>();
        balances.put(a, new BigDecimal("6.67"));
        balances.put(b, new BigDecimal("-3.33"));
        balances.put(c, new BigDecimal("-3.34"));

        List<GroupDashboardResponse.SettlementSuggestionDto> result = groupService.calculateSettlements(balances);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getAmount().equals(new BigDecimal("3.33"))));
        assertTrue(result.stream().anyMatch(s -> s.getAmount().equals(new BigDecimal("3.34"))));
    }
}
