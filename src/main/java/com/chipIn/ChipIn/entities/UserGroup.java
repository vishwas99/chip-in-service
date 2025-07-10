package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import com.chipIn.ChipIn.entities.UserGroupsId;

@Entity
@Table(name = "user_groups", schema = "chipin")
@Getter
@Setter
public class UserGroup {
    @EmbeddedId
    private UserGroupsId userGroupsId;

    @Column(name = "moneyowed")
    private Double moneyOwed;
}
