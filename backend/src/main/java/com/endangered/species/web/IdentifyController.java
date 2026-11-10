package com.endangered.species.web;

import com.endangered.species.ml.IdentifyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Image, video, audio and text identification. */
@RestController
public class IdentifyController {

    private final IdentifyService identify;

    public IdentifyController(IdentifyService identify) {
        this.identify = identify;
    }

    @PostMapping(path = "/identify-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dtos.IdentifyResult image(@RequestParam("file") MultipartFile file) {
        return identify.fromImage(file);
    }

    @PostMapping(path = "/identify-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dtos.IdentifyResult video(@RequestParam("file") MultipartFile file) {
        return identify.fromVideo(file);
    }

    @PostMapping(path = "/identify-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dtos.IdentifyResult audio(@RequestParam("file") MultipartFile file) {
        return identify.fromAudio(file);
    }

    @PostMapping("/identify-text")
    public Dtos.IdentifyResult text(@RequestBody Dtos.TextInput input) {
        return identify.fromText(input == null ? null : input.text());
    }
}
