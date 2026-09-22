package com.px.base.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DutyPolicyView {
    private String selectedRule;
    private String selectedRuleCode;
    private String explanation;
    private String rejectedAlternative;
    private String rejectedAlternativeReason;
    private String statusChain;
}
