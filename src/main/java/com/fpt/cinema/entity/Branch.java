package com.fpt.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "branch_name", nullable = false, unique = true, length = 150)
    private String branchName;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "status", nullable = false, length = 30)
    private String status;
}
