package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.dto.ModerationUserInfo;

import java.util.Collection;
import java.util.Map;

/** Local moderation read seam; not part of the cross-service app-api. */
public interface ModerationUserReadPort {

    ModerationUserInfo findById(String userId);

    Map<String, ModerationUserInfo> findByIds(Collection<String> userIds);
}
