package com.chipIn.ChipIn.services;

import com.chipIn.ChipIn.dao.GroupDao;
import com.chipIn.ChipIn.dao.UserToGroupDao;
import com.chipIn.ChipIn.dto.GroupDto;
import com.chipIn.ChipIn.dto.UserGroupResponse;
import com.chipIn.ChipIn.dto.UserToGroupDto;
import com.chipIn.ChipIn.dto.mapper.GroupMapper;
import com.chipIn.ChipIn.entities.Group;
import com.chipIn.ChipIn.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GroupService {

    @Autowired
    private GroupDao groupDao;

    @Autowired
    private UserToGroupDao userToGroupDao;

    @Autowired
    private GroupMapper groupMapper;


    public UUID createGroup(@RequestBody GroupDto groupDto){
        groupDto.setCreatedAt(LocalDateTime.now());
        return groupDao.createGroup(groupMapper.toEntity(groupDto));
    }

    public boolean addMemberToGroup(UserToGroupDto userToGroupDto){
        return userToGroupDao.addMemberToGroup(userToGroupDto);
    }

    public Set<UUID> getUserByGroupId(UUID groupId){
        try{
            return (Set<UUID>) userToGroupDao.getUserByGroupId(groupId);
        }catch (Exception e){
            throw new RuntimeException("Error while fetching members");
        }
    }

    public Group getGroupById(UUID groupId){
        return groupDao.getGroupData(groupId);
    }

    public List<UserGroupResponse> getGroupsByUserId(UUID userId){

        return groupDao.getGroupsByUserId(userId);
    }

    public List<User> getUsersInGroup(UUID groupId){
        return userToGroupDao.getUsersInGroup(groupId);
    }

}
