package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.CreateClassifierRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateClassifierRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.ClassifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final ClassifierRepository classifierRepository;

    @Transactional(readOnly = true)
    public List<ClassifierEntity> getAll() {
        return classifierRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ClassifierEntity getById(Long id) {
        return classifierRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Classifier not found: " + id));
    }

    @Transactional
    public ClassifierEntity create(CreateClassifierRequest request) {
        ClassifierEntity entity = new ClassifierEntity();
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setProviderId(request.providerId());
        entity.setPromptId(request.promptId());
        entity.setKeywords(request.keywords());
        entity.setRegexPattern(request.regex());
        entity.setModelConfigJson(request.modelConfig());
        entity.setVersion(request.version() != null ? request.version() : "1.0");
        entity.setClassifierOrder(request.order() != null ? request.order() : 100);
        entity.setStatus("DRAFT");
        return classifierRepository.save(entity);
    }

    @Transactional
    public ClassifierEntity update(Long id, UpdateClassifierRequest request) {
        ClassifierEntity entity = getById(id);
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setProviderId(request.providerId());
        entity.setPromptId(request.promptId());
        entity.setKeywords(request.keywords());
        entity.setRegexPattern(request.regex());
        entity.setModelConfigJson(request.modelConfig());
        entity.setVersion(request.version());
        if (request.order() != null) {
            entity.setClassifierOrder(request.order());
        }
        return classifierRepository.save(entity);
    }

    @Transactional
    public ClassifierEntity updateStatus(Long id, String status) {
        ClassifierEntity entity = getById(id);
        entity.setStatus(status);
        return classifierRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!classifierRepository.existsById(id)) {
            throw new IllegalArgumentException("Classifier not found: " + id);
        }
        classifierRepository.deleteById(id);
    }

    @Transactional
    public void reorder(List<Long> classifierIds) {
        List<ClassifierEntity> classifiers = classifierRepository.findAllById(classifierIds);
        java.util.Map<Long, ClassifierEntity> map = classifiers.stream()
            .collect(java.util.stream.Collectors.toMap(ClassifierEntity::getId, java.util.function.Function.identity()));

        for (int i = 0; i < classifierIds.size(); i++) {
            ClassifierEntity entity = map.get(classifierIds.get(i));
            if (entity == null) {
                throw new IllegalArgumentException("Classifier not found: " + classifierIds.get(i));
            }
            entity.setClassifierOrder(i);
        }
        classifierRepository.saveAll(classifiers);
    }
}
