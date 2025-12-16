package com.chipIn.ChipIn.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import lombok.*;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class UserGroupsId {

    @Column(name = "userid", nullable = false)
    private UUID userId;
    @Column(name = "groupid", nullable = false)
    private UUID groupId;

    public UserGroupsId(UUID userId, UUID groupId){
        this.userId = userId;
        this.groupId = groupId;
    }
}
