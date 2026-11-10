package com.endangered.species.service;

import com.endangered.species.domain.ContactMessage;
import com.endangered.species.repo.ContactMessageRepository;
import com.endangered.species.web.ApiException;
import com.endangered.species.web.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists contact-form submissions. */
@Service
public class ContactService {

    private final ContactMessageRepository messages;

    public ContactService(ContactMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public Dtos.MessageResponse submit(Dtos.ContactRequest request) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.message())) {
            throw ApiException.badRequest("All fields are required");
        }
        messages.save(new ContactMessage(
                request.name().trim(), request.email().trim(), request.message().trim()));
        return new Dtos.MessageResponse("Thank you for contacting us!");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
