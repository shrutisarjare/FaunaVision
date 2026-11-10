package com.endangered.species.web;

import com.endangered.species.service.AdminService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Administrative CRUD over the species catalogue. */
@RestController
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/admin/species")
    public List<Map<String, Object>> species() {
        return admin.allSpecies();
    }

    @GetMapping("/admin/sheet/{region}/{category}")
    public List<Map<String, Object>> sheet(@PathVariable String region, @PathVariable String category) {
        return admin.sheet(region, category);
    }

    @PostMapping("/admin/upload-excel")
    public Map<String, Object> uploadWorkbook(@RequestParam("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return admin.replaceWorkbook(input);
        }
    }

    @PostMapping("/admin/upload/{region}/{category}")
    public Map<String, Object> uploadSheet(@PathVariable String region, @PathVariable String category,
                                           @RequestParam("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return admin.appendSheet(region, category, input);
        }
    }

    @DeleteMapping("/admin/delete/{region}/{category}/{animal}")
    public Map<String, Object> delete(@PathVariable String region, @PathVariable String category,
                                      @PathVariable String animal) {
        return admin.delete(region, category, animal);
    }

    @PutMapping("/admin/update/{region}/{category}/{animal}")
    public Map<String, Object> update(@PathVariable String region, @PathVariable String category,
                                      @PathVariable String animal, @RequestBody Map<String, Object> body) {
        return admin.update(region, category, animal, body == null ? Map.of() : body);
    }

    @PostMapping("/admin/add/{region}/{category}")
    public Map<String, Object> add(@PathVariable String region, @PathVariable String category,
                                   @RequestBody Map<String, Object> body) {
        return admin.add(region, category, body == null ? Map.of() : body);
    }
}
