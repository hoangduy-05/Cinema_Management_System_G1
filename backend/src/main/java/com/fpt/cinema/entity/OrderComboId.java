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
public class OrderComboId implements Serializable {

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "combo_id")
    private Long comboId;
}
