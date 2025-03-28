package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.UserDto;
import com.chipIn.ChipIn.entities.User;
import com.chipIn.ChipIn.services.UserService;
import com.chipIn.ChipIn.util.ErrorResponse;
import com.chipIn.ChipIn.util.ResponseWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Validated
public class UserController extends BaseController {

    /**
     * GET /users
     * @return
     */

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public ResponseEntity<ResponseWrapper<List<User>>> getUsers(){
        return ResponseEntity.ok(ResponseWrapper.success(userService.getUsers()));
    }

    @PostMapping("/add")
    public ResponseEntity<ResponseWrapper<UUID>> addUser(@RequestBody UserDto userData){
        userData.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(ResponseWrapper.success(userService.addUser(userData.toEntity())));
    }



}
