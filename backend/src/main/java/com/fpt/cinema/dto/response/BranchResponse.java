package com.fpt.cinema.dto.response;

public record BranchResponse(
        Long branchId,
        String branchName,
        String address,
        String email,
        String status
) {
}
