package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.response.ComboResponse;
import com.fpt.cinema.service.ComboService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/combos")
@Tag(name = "Combos", description = "Public concession combo catalog")
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    @Operation(summary = "List active combos", description = "Public endpoint.")
    public ApiResponse<List<ComboResponse>> getCombos() {
        return ApiResponse.success("Lấy danh sách combo thành công", comboService.getActiveCombos());
    }
}
