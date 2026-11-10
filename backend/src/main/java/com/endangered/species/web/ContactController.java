package com.endangered.species.web;

import com.endangered.species.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Contact form endpoint formerly served by the Express side-car. */
@RestController
public class ContactController {

    private final ContactService contacts;

    public ContactController(ContactService contacts) {
        this.contacts = contacts;
    }

    @PostMapping("/api/contact")
    public ResponseEntity<Dtos.MessageResponse> submit(@RequestBody Dtos.ContactRequest request) {
        if (request == null) {
            throw ApiException.badRequest("All fields are required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(contacts.submit(request));
    }
}
