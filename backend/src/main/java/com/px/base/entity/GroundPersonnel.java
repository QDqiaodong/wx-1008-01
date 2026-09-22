package com.px.base.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ground_personnel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroundPersonnel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", unique = true, nullable = false, length = 50)
    private String employeeNo;

    @Column(name = "person_name", nullable = false, length = 100)
    private String personName;

    /** OPERATOR-普通值班员；SAFETY_MANAGER-安全主管。同一人也可以是持普通值班员身份的主管。 */
    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    @Column(name = "active", nullable = false, columnDefinition = "TINYINT(1)")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
