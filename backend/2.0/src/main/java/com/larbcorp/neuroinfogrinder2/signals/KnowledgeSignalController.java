package com.larbcorp.neuroinfogrinder2.signals;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class KnowledgeSignalController {
    private final KnowledgeSignalService service;

    public KnowledgeSignalController(KnowledgeSignalService service) {
        this.service = service;
    }

    @GetMapping({"/api/v1/topics", "/api/v2/topics"})
    public Map<String, Object> topics() {
        return service.topics();
    }

    @GetMapping({"/api/v1/topics/{slug}", "/api/v2/topics/{slug}"})
    public Map<String, Object> topic(
            @PathVariable String slug,
            @RequestParam(required = false) String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.topic(slug, tab, page, size);
    }

    @GetMapping({"/api/v1/signals/{id}", "/api/v2/signals/{id}"})
    public Map<String, Object> signal(@PathVariable long id) {
        return service.signal(id);
    }

    @GetMapping({"/api/v1/signals/{id}/sources", "/api/v2/signals/{id}/sources"})
    public List<Map<String, Object>> signalSources(@PathVariable long id) {
        return service.signalSources(id);
    }

    /** Promote a signal to a DRAFT material (manual "В материал" action). Returns the new material id. */
    @PostMapping({"/api/v1/signals/{id}/promote", "/api/v2/signals/{id}/promote"})
    public ResponseEntity<Map<String, Object>> promoteSignal(@PathVariable long id) {
        Long materialId = service.promoteToMaterial(id);
        return ResponseEntity.ok(Map.of("materialId", materialId, "signalId", id, "status", "DRAFT"));
    }
}
