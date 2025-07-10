package com.chipIn.ChipIn.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "groups")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@EqualsAndHashCode
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "groupid")
    private UUID groupId;

    @Column(name = "name")
    private String groupName;

    @Column(name = "description")
    private String groupDescription;

    @Column(name = "created_by")
    private UUID groupAdmin;

    @Column(name = "created_at")
    private LocalDateTime groupCreationDate;

    @Column(name = "image_url")
    private String imageUrl;
}
