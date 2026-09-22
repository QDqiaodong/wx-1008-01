package com.px.base.service;

/**
 * 航线/锚点/证书等值守资格依据变化事件。监听方只重判未就绪的未来安排，
 * READY 与已结束安排保留就绪时冻结的历史快照。
 */
public record DutyReferenceChangedEvent(String referenceType, Long referenceId, String reason) {
    public static final String ANCHOR = "ANCHOR";
    public static final String ROUTE = "ROUTE";
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String ROUTE_BINDING = "ROUTE_BINDING";
}
