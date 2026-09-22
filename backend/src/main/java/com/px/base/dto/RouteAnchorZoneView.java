package com.px.base.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteAnchorZoneView {
    private Long anchorId;
    private String anchorCode;
    private String anchorZone;
    private boolean active;
}
