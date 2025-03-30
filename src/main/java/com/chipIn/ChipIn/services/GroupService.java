package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.GroupDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.GroupDto;
import com.chipIn.ChipIn.dto.UserToGroupDto;
import com.chipIn.ChipIn.entities.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GroupService {

    @Autowired
    private GroupDao groupDao;

    @Autowired
    private UserToGroupDao groupToUserDao;


    public UUID createGroup(@RequestBody GroupDto groupDto){
        return groupDao.createGroup(groupDto.toEntity());
    }

    public boolean addMemberToGroup(UserToGroupDto userToGroupDto){
        return groupToUserDao.addMemberToGroup(userToGroupDto);
    }

    public Set<UUID> getUserByGroupId(UUID groupId){
        try{
            return (Set<UUID>) groupToUserDao.getUserByGroupId(groupId);
        }catch (Exception e){
            throw new RuntimeException("Error while fetching members");
        }
    }

    public Group getGroupById(UUID groupId){
        return groupDao.getGroupData(groupId);
    }

}
