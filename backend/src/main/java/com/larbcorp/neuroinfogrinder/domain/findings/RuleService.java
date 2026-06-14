package com.larbcorp.neuroinfogrinder.domain.findings;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.CreateRuleRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.ReorderRulesRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateRuleRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.RuleEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<RuleEntity> getAll() {
        return ruleRepository.findAllByOrderByRuleOrderAsc();
    }

    @Transactional(readOnly = true)
    public RuleEntity getById(Long id) {
        return ruleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }

    @Transactional
    public RuleEntity create(CreateRuleRequest request) {
        RuleEntity entity = new RuleEntity();
        entity.setName(request.name() != null ? request.name() : "Unnamed rule");
        entity.setDescription(request.description());
        entity.setActionType(request.actionType() != null ? request.actionType() : "INCLUDE");
        entity.setRuleOrder(request.order());
        entity.setConditionsJson(request.conditions());
        entity.setActionsJson(request.actions());
        entity.setStatus(request.status() != null ? request.status() : "DRAFT");
        return ruleRepository.save(entity);
    }

    @Transactional
    public RuleEntity update(Long id, UpdateRuleRequest request) {
        RuleEntity entity = getById(id);
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.actionType() != null) {
            entity.setActionType(request.actionType());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        entity.setRuleOrder(request.order());
        entity.setConditionsJson(request.conditions());
        entity.setActionsJson(request.actions());
        return ruleRepository.save(entity);
    }

    @Transactional
    public RuleEntity updateStatus(Long id, String status) {
        RuleEntity entity = getById(id);
        entity.setStatus(status);
        return ruleRepository.save(entity);
    }

    @Transactional
    public void reorder(ReorderRulesRequest request) {
        List<RuleEntity> rules = ruleRepository.findAllById(request.ruleIds());
        Map<Long, RuleEntity> ruleMap = rules.stream()
            .collect(Collectors.toMap(RuleEntity::getId, Function.identity()));

        for (int i = 0; i < request.ruleIds().size(); i++) {
            Long ruleId = request.ruleIds().get(i);
            RuleEntity entity = ruleMap.get(ruleId);
            if (entity == null) {
                throw new IllegalArgumentException("Rule not found: " + ruleId);
            }
            entity.setRuleOrder(i);
        }

        ruleRepository.saveAll(rules);
    }

    @Transactional
    public void delete(Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
        ruleRepository.deleteById(id);
    }
}
