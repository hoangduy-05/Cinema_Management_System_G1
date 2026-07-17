package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.response.ComboResponse;
import com.fpt.cinema.mapper.ComboMapper;
import com.fpt.cinema.repository.ComboRepository;
import com.fpt.cinema.service.ComboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ComboServiceImpl implements ComboService {

    private static final String ACTIVE = "ACTIVE";

    private final ComboRepository comboRepository;
    private final ComboMapper comboMapper;

    public ComboServiceImpl(ComboRepository comboRepository, ComboMapper comboMapper) {
        this.comboRepository = comboRepository;
        this.comboMapper = comboMapper;
    }

    @Override
    public List<ComboResponse> getActiveCombos() {
        return comboMapper.toResponses(comboRepository.findAllByStatusIgnoreCaseOrderByIdAsc(ACTIVE));
    }
}
