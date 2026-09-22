package com.px.base.security;

import com.px.base.entity.GroundPersonnel;

public record ActorContext(Long id, String name, String roleCode, boolean safetyManager) {
    public static final String HEADER = "X-Actor-Id";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_SAFETY_MANAGER = "SAFETY_MANAGER";

    public static ActorContext from(GroundPersonnel person) {
        boolean manager = ROLE_SAFETY_MANAGER.equals(person.getRoleCode());
        return new ActorContext(person.getId(), person.getPersonName(), person.getRoleCode(), manager);
    }
}
