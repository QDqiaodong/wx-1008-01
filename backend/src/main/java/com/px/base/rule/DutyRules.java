package com.px.base.rule;

import java.util.List;

public final class DutyRules {
    public static final List<String> WIND_LEVELS = List.of("微风", "轻风", "和风", "强风", "疾风");
    public static final List<String> ANCHOR_ZONES = List.of("东区", "南区", "西区", "北区");

    public static final String POLICY_CODE = "TAKEOFF_TIME";
    public static final String POLICY_NAME = "按计划起飞时刻判断证书有效";
    public static final String POLICY_EXPLANATION =
            "跨午夜任务只要求证书在计划起飞当日处于生效/有效状态；人员列表、值守详情和航线入口均按此同一时刻给出结论。"
                    + "起飞后临时风况和预计结束时刻不会反过来改变开航资格，保证跨午夜任务结论稳定。";
    public static final String REJECTED_ALTERNATIVE = "不采用“证书必须覆盖整个预计飞行区间”";
    public static final String REJECTED_ALTERNATIVE_REASON =
            "预计结束时间常受回收、天气和空管影响，若按整个区间，跨夜航班会因午夜后证书到期在起飞前被反复判定；"
                    + "这会把不确定的延误风险变成开航前置条件。现场风险通过起飞前复核、主管取消和到期后不得新开航控制。";
    public static final String STATUS_CHAIN =
            "证书：待生效 → 有效 → 已过期；安全主管可吊销为终态。值守：草拟 → 待复核（操作员到位）→ 就绪（复核员确认）；任一状态可由主管取消。";

    private DutyRules() {}
}
