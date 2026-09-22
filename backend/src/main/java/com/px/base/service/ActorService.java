package com.px.base.service;

import com.px.base.entity.GroundPersonnel;
import com.px.base.repository.GroundPersonnelRepository;
import com.px.base.security.ActorContext;
import com.px.base.security.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActorService {
    private final GroundPersonnelRepository personnelRepository;

    public ActorContext require(String actorIdHeader) {
        if (actorIdHeader == null || actorIdHeader.isBlank()) {
            throw new ForbiddenException("缺少请求头 X-Actor-Id，无法识别现场操作人");
        }
        final Long id;
        try {
            id = Long.parseLong(actorIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new ForbiddenException("X-Actor-Id 必须是系统人员ID");
        }
        GroundPersonnel person = personnelRepository.findById(id)
                .orElseThrow(() -> new ForbiddenException("当前操作人不存在或已停用"));
        if (!Boolean.TRUE.equals(person.getActive())) {
            throw new ForbiddenException("当前操作人已停用");
        }
        return ActorContext.from(person);
    }

    public ActorContext requireSafetyManager(String actorIdHeader) {
        ActorContext actor = require(actorIdHeader);
        if (!actor.safetyManager()) {
            throw new ForbiddenException("仅安全主管可以执行该操作；后端已拒绝本次数据变更");
        }
        return actor;
    }
}
