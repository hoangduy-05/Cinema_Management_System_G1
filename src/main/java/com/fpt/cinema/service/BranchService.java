package com.fpt.cinema.service;

import com.fpt.cinema.dto.response.BranchResponse;

import java.util.List;

public interface BranchService {

    List<BranchResponse> getBranches();
}
