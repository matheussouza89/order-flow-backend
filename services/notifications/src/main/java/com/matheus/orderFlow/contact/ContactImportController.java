package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Notifications",
        description = "Order emails, for administrators"
)
@RestController
@RequestMapping("/admin/notifications/contacts")
@RequiredArgsConstructor
class ContactImportController {
    private final ContactImportService contactImportService;

    @Operation(summary = "Import the customers' emails",
            description = """
                    Copies the email of every user from the identity service, page by page, on behalf
                    of the calling administrator. Meant to run once, when the service starts operating;
                    after that, registrations and email changes arrive on their own. A stored email is
                    replaced only by a newer one, so running it again changes nothing. `imported` counts
                    the emails written.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Emails copied"),
            @ApiResponse(responseCode = "403", description = "Only administrators can do this",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "The identity service did not answer; what was "
                    + "copied stays, and the import can be run again",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ImportResponse importContacts() {
        return new ImportResponse(contactImportService.importAll());
    }
}
