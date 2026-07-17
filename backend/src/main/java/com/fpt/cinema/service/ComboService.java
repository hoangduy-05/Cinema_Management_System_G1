package com.fpt.cinema.service;

import com.fpt.cinema.dto.response.ComboResponse;

import java.util.List;

public interface ComboService {

    List<ComboResponse> getActiveCombos();
}
