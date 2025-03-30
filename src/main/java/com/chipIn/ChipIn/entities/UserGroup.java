package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "user_groups", schema = "chipin") // ✅ Ensure correct table mapping
@Getter
@Setter
public class UserGroup {
    @Id
    @Column(name = "userid", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "groupid", nullable = false)
    private UUID groupId;
}
