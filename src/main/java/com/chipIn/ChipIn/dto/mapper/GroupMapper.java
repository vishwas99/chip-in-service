package com.chipIn.ChipIn.dto.mapper;

import com.chipIn.ChipIn.dao.UserDao;
import com.chipIn.ChipIn.dto.GroupDto;
import com.chipIn.ChipIn.entities.Group;
import org.springframework.stereotype.Component;

@Component
public class GroupMapper {
    // Generate Mapper to map GroupDto to Group entity

    private final UserDao userDao;
    public GroupMapper(UserDao userDao) {
        this.userDao = userDao;
    }

    public Group toEntity(GroupDto groupDto) {
        Group group = new Group();
        group.setGroupName(groupDto.getGroupName());
        group.setGroupDescription(groupDto.getGroupDescription());
        group.setGroupAdmin(userDao.getUserById(groupDto.getCreatedBy()));
        return group;
    }

}
