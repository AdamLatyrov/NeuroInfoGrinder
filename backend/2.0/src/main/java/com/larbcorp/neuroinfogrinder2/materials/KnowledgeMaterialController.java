package com.larbcorp.neuroinfogrinder2.materials;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KnowledgeMaterialController {
    private final KnowledgeMaterialService service;

    public KnowledgeMaterialController(KnowledgeMaterialService service) {
        this.service = service;
    }

    @GetMapping({"/api/v1/materials", "/api/v2/materials"})
    public Map<String, Object> materials(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.list(status, contentType, page, size);
    }

    @GetMapping({"/api/v1/materials/{id}", "/api/v2/materials/{id}", "/api/v1/guides/{id}", "/api/v2/guides/{id}"})
    public Map<String, Object> material(@PathVariable long id) {
        return service.detail(id);
    }

    @DeleteMapping({"/api/v1/materials/{id}", "/api/v2/materials/{id}", "/api/v1/guides/{id}", "/api/v2/guides/{id}"})
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.softDelete(id, 1L);
        return ResponseEntity.noContent().build();
    }
}
