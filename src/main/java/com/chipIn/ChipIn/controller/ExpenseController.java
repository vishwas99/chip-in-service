package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.ExpenseDetailsDto;
import com.chipIn.ChipIn.dto.UserExpensesDto;
import com.chipIn.ChipIn.dto.UserSplitsDto;
import com.chipIn.ChipIn.entities.Expense;
import com.chipIn.ChipIn.entities.Split;
import com.chipIn.ChipIn.services.ExpenseService;
import com.chipIn.ChipIn.util.ResponseWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    @Autowired
    ExpenseService expenseService;

    @GetMapping("/group")
    public ResponseEntity<ResponseWrapper<List<Expense>>> getExpensesByGroupId(@RequestParam UUID groupId){
        return ResponseEntity.ok(ResponseWrapper.success(expenseService.getExpensesByGroupId(groupId)));
    }

    @GetMapping("/user")
    public ResponseEntity<ResponseWrapper<UserExpensesDto>> getExpensesByUserId(@RequestParam UUID userId){
        return ResponseEntity.ok(ResponseWrapper.success(expenseService.getExpensesByUserId(userId)));
    }

    @GetMapping("/user-group")
    public ResponseEntity<ResponseWrapper<List<Split>>> getExpensesByUserIdAndGroupId(@RequestParam UUID userId, @RequestParam UUID groupId){
        return ResponseEntity.ok(ResponseWrapper.success(expenseService.getExpensesByUserIdAndGroupId(userId, groupId)));
    }

    @GetMapping("/user-user")
    public ResponseEntity<ResponseWrapper<UserSplitsDto>> getAllUserToUserSplits(@RequestParam UUID userId) throws InterruptedException {
        return ResponseEntity.ok(ResponseWrapper.success(expenseService.getAllExpensesByUserIdGroupByUserId(userId)));
    }

    @GetMapping("/details")
    public ResponseEntity<ResponseWrapper<ExpenseDetailsDto>> getExpenseById(@RequestParam UUID expenseId) throws InterruptedException {
        return ResponseEntity.ok(ResponseWrapper.success(expenseService.getExpenseById(expenseId)));
    }
}
