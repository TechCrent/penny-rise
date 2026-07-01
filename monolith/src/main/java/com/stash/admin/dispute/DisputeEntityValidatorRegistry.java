package com.stash.admin.dispute;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DisputeEntityValidatorRegistry {

    private final Map<RelatedEntityType, DisputeEntityValidator> validators;

    public DisputeEntityValidatorRegistry(List<DisputeEntityValidator> allValidators) {
        this.validators = allValidators.stream()
                .collect(Collectors.toMap(DisputeEntityValidator::supportedType, Function.identity()));
    }

    public DisputeEntityValidator get(RelatedEntityType type) {
        DisputeEntityValidator v = validators.get(type);
        if (v == null) {
            throw new IllegalStateException("No DisputeEntityValidator registered for " + type);
        }
        return v;
    }
}
