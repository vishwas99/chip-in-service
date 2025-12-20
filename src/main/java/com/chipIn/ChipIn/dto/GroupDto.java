package com.chipIn.ChipIn.dto;

import com.chipIn.ChipIn.entities.Group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
public class GroupDto {

    @NotBlank(message = "Created By cannot be empty")
    private UUID createdBy;

    @NotBlank(message = "Group name cannot be empty")
    @Size(min=2, max=100, message = "Group name must be between 2-100 characters")
    private String groupName;

    private String groupDescription;

    private LocalDateTime createdAt;

    public GroupDto(){
        this.createdAt = LocalDateTime.now();
    }

//    public Group toEntity() {
//        Group group = new Group();
//        group.setGroupName(this.groupName);
//        group.setGroupDescription(this.groupDescription);
//        group.setGroupAdmin(this.createdBy);
//        group.setGroupCreationDate(this.createdAt);
//        return group;
//    }

}
