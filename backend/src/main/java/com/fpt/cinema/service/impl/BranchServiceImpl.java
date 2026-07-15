package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.response.BranchResponse;
import com.fpt.cinema.mapper.BranchMapper;
import com.fpt.cinema.repository.BranchRepository;
import com.fpt.cinema.service.BranchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private static final String ACTIVE = "ACTIVE";

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    public BranchServiceImpl(BranchRepository branchRepository, BranchMapper branchMapper) {
        this.branchRepository = branchRepository;
        this.branchMapper = branchMapper;
    }

    @Override
    public List<BranchResponse> getBranches() {
        return branchMapper.toResponses(branchRepository.findAllByStatusOrderByBranchNameAsc(ACTIVE));
    }
}
