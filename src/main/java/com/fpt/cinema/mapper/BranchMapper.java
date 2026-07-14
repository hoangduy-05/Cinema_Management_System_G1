package com.fpt.cinema.mapper;

import com.fpt.cinema.dto.response.BranchResponse;
import com.fpt.cinema.entity.Branch;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class BranchMapper {

    public BranchResponse toResponse(Branch branch) {
        if (branch == null) {
            return null;
        }

        return new BranchResponse(
                branch.getBranchId(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getEmail(),
                branch.getStatus()
        );
    }

    public List<BranchResponse> toResponses(Collection<Branch> branches) {
        if (branches == null || branches.isEmpty()) {
            return List.of();
        }

        return branches.stream()
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }
}
