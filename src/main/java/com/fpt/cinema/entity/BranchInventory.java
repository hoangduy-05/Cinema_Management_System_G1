package com.fpt.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "branch_inventory", indexes = {
        @Index(name = "idx_branch_inventory_item", columnList = "item_id")
})
@Getter
@Setter
@NoArgsConstructor
public class BranchInventory {

    @EmbeddedId
    private BranchInventoryId id;

    @MapsId("branchId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @MapsId("itemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Concession concession;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQuantity;

    @Column(name = "min_threshold", nullable = false)
    private Integer minimumThreshold;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
