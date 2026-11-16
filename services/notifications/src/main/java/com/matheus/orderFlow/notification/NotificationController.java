package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import com.matheus.orderFlow.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Notifications",
        description = "Order emails, for administrators"
)
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
class NotificationController {
    private final NotificationService notificationService;

    @Operation(summary = "Notifications by state",
            description = "Lists the notifications in one state, most recently changed first. Without a "
                    + "state, lists the ones that gave up after every attempt. The customer's email is "
                    + "not shown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of notifications"),
            @ApiResponse(responseCode = "400", description = "Unknown state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Only administrators can do this",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "FAILED") NotificationState state,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(state, pageable);
    }
}
