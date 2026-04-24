package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.HomeFriendsResponse;
import com.chipIn.ChipIn.dto.HomeGroupsResponse;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.entities.Currency;
import com.chipIn.ChipIn.repository.CurrencyRepository;
import com.chipIn.ChipIn.services.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController extends BaseController {

    private final HomeService homeService;
    private final CurrencyRepository currencyRepository;

    @GetMapping("/groups")
    public ResponseEntity<HomeGroupsResponse> getGroupsView(
            @RequestParam(required = false) UUID displayCurrencyId) {

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID currencyId = displayCurrencyId;
        if (currencyId == null) {
            currencyId = currentUser.getDefaultCurrencyId();
            if (currencyId == null) {
                // Fallback to INR
                Currency inr = currencyRepository.findByCode("INR").orElseThrow(
                        () -> new RuntimeException("INR currency not found")
                );
                currencyId = inr.getCurrencyId();
            }
        }
        return ResponseEntity.ok(homeService.getHomeGroupsData(currentUser.getUserid(), currencyId));
    }

    @GetMapping("/friends")
    public ResponseEntity<HomeFriendsResponse> getFriendsView(
            @RequestParam(required = false) UUID displayCurrencyId) {

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID currencyId = displayCurrencyId;
        if (currencyId == null) {
            currencyId = currentUser.getDefaultCurrencyId();
            if (currencyId == null) {
                // Fallback to INR
                Currency inr = currencyRepository.findByCode("INR").orElseThrow(
                        () -> new RuntimeException("INR currency not found")
                );
                currencyId = inr.getCurrencyId();
            }
        }
        return ResponseEntity.ok(homeService.getHomeFriendsData(currentUser.getUserid(), currencyId));
    }
}
