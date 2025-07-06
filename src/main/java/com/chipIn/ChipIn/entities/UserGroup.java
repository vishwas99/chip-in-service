package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "user_groups", schema = "chipin")
@Getter
@Setter
public class UserGroup {
    @EmbeddedId
    @Column(name = "userid", nullable = false)
    private UserGroupsId userGroupsId;

}
