package com.matheus.orderFlow.contact;

import com.matheus.orderFlow.shared.exception.ServiceUnavailableException;
import com.matheus.orderFlow.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
class ContactImportService {
    private static final int PAGE_SIZE = 100;

    private final UserExportClient identity;
    private final ContactService contactService;

    int importAll() {
        int imported = 0;
        int next = 0;
        PageResponse<ExportedUser> page;

        do {
            page = exportPage(next++);
            imported += (int) page.content().stream()
                    .filter(user -> contactService.record(user.userId(), user.email(), user.updatedAt()))
                    .count();
        } while (next < page.totalPages());

        log.info("Contacts imported from the identity export: {}", imported);
        return imported;
    }

    private PageResponse<ExportedUser> exportPage(int number) {
        try {
            return identity.export(number, PAGE_SIZE);
        } catch (ResourceAccessException | HttpServerErrorException unavailable) {
            throw new ServiceUnavailableException(IdentityClientConfig.IDENTITY, unavailable);
        }
    }
}
