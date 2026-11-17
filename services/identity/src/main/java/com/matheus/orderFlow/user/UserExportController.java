package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
class UserExportController {
    private final UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/export")
    public PageResponse<UserExportResponse> export(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "100") int size) {
        return userService.export(page, size);
    }
}
