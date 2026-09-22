package com.px.base.dto;

import lombok.Data;

@Data
public class PersonnelDTO {
    private String employeeNo;
    private String personName;
    private String roleCode;
    private Boolean active;
}
