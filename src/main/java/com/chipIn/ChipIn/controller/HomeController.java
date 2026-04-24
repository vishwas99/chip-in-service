package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.HomeFriendsResponse;
import com.chipIn.ChipIn.dto.HomeGroupsResponse;
import com.chipIn.ChipIn.entities.User;
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

    @GetMapping("/groups")
    public ResponseEntity<HomeGroupsResponse> getGroupsView(
            @RequestParam UUID displayCurrencyId) {
            
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(homeService.getHomeGroupsData(currentUser.getUserid(), displayCurrencyId));
    }

    @GetMapping("/friends")
    public ResponseEntity<HomeFriendsResponse> getFriendsView(
            @RequestParam UUID displayCurrencyId) {
            
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(homeService.getHomeFriendsData(currentUser.getUserid(), displayCurrencyId));
    }
}
