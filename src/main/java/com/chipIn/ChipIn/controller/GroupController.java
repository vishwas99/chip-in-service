package com.chipIn.ChipIn.controller;

import com.chipIn.ChipIn.dto.ExpenseDto;
import com.chipIn.ChipIn.dto.GroupDto;
import com.chipIn.ChipIn.dto.UserGroupResponse;
import com.chipIn.ChipIn.dto.UserToGroupDto;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.services.ExpenseService;
import com.chipIn.ChipIn.services.GroupService;
import com.chipIn.ChipIn.util.ResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/groups")
@Validated
@Slf4j
public class GroupController extends BaseController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseService expenseService;

    @PostMapping("/create")
    public ResponseEntity<ResponseWrapper<UUID>> createGroup(@RequestBody GroupDto groupDto) {
        return ResponseEntity.ok(ResponseWrapper.success(groupService.createGroup(groupDto)));
    }

    @PostMapping("/addMember")
    public ResponseEntity<ResponseWrapper<Boolean>> addMemberToGroup(@RequestBody UserToGroupDto userToGroupDto) {
        return ResponseEntity.ok(ResponseWrapper.success(groupService.addMemberToGroup(userToGroupDto)));
    }

    @GetMapping("/getGroupDetails")
    public ResponseEntity<ResponseWrapper<Group>> getGroupDetails(@RequestParam("groupId") UUID groupId) {
        return ResponseEntity.ok(ResponseWrapper.success(groupService.getGroupById(groupId)));
    }

    @GetMapping("/getGroupMembers")
    public ResponseEntity<ResponseWrapper<Set<UUID>>> getGroupMembers(@RequestParam("groupId") UUID groupId) {
        return ResponseEntity.ok(ResponseWrapper.success(groupService.getUserByGroupId(groupId)));
    }

    @PostMapping("/addExpense")
    public ResponseEntity<ResponseWrapper<Boolean>> addExpenseToGroup(@RequestBody ExpenseDto expenseDto) {
        log.info(expenseDto.toString());
        expenseService.addExpense(expenseDto);
        return null;
    }

    @GetMapping("/users")
    public ResponseEntity<ResponseWrapper<List<UserGroupResponse>>> getGroupsByUserId(@RequestParam("userId") UUID userId) {
        return ResponseEntity.ok(ResponseWrapper.success(groupService.getGroupsByUserId(userId)));
    }

}