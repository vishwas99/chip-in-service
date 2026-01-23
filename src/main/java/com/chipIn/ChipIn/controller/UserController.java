package com.chipIn.ChipIn.controller;
import com.chipIn.ChipIn.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;

    @PostMapping("/disable")
    public ResponseEntity<String> disableUser(@RequestParam String email) {
        userService.disableUser(email);
        return ResponseEntity.ok("User disabled successfully.");
    }

    @PostMapping("/enable")
    public ResponseEntity<String> enableUser(@RequestParam String email) {
        userService.enableUser(email);
        return ResponseEntity.ok("User enabled successfully.");
    }
}
