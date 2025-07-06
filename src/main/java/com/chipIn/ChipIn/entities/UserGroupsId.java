package com.chipIn.ChipIn.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class UserGroupsId {

    private UUID userId;
    private UUID groupId;

}
