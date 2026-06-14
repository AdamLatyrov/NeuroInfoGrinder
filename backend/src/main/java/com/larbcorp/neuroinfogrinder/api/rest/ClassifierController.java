package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.ClassifierService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.ClassifierResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.CreateClassifierRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateClassifierRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateClassifierStatusRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classifiers")
@RequiredArgsConstructor
public class ClassifierController {

    private final ClassifierService classifierService;

    @GetMapping
    public List<ClassifierResponse> getAll() {
        return classifierService.getAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassifierResponse create(@RequestBody CreateClassifierRequest request) {
        return toResponse(classifierService.create(request));
    }

    @PutMapping("/{id}")
    public ClassifierResponse update(@PathVariable Long id, @RequestBody UpdateClassifierRequest request) {
        return toResponse(classifierService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ClassifierResponse updateStatus(@PathVariable Long id, @RequestBody UpdateClassifierStatusRequest request) {
        return toResponse(classifierService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        classifierService.delete(id);
    }

    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.OK)
    public void reorder(@RequestBody ReorderClassifiersRequest request) {
        classifierService.reorder(request.classifierIds());
    }

    public record ReorderClassifiersRequest(List<Long> classifierIds) {}

    private ClassifierResponse toResponse(ClassifierEntity entity) {
        return new ClassifierResponse(
            entity.getId(),
            entity.getName(),
            entity.getType(),
            entity.getProviderId(),
            null,
            entity.getPromptId(),
            entity.getKeywords(),
            entity.getRegexPattern(),
            entity.getModelConfigJson(),
            entity.getVersion(),
            entity.getStatus(),
            entity.getClassifierOrder(),
            0
        );
    }
}
