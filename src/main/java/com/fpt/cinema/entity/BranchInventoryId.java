package com.fpt.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class BranchInventoryId implements Serializable {

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "item_id")
    private Long itemId;
}
