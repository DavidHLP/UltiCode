package com.ulticode.modules.contest.port;

import com.ulticode.modules.contest.entity.Contest;

public interface ContestWritePort {
    void insert(Contest contest);
    void updateById(Contest contest);
    void deleteById(String id);
    Contest selectById(String id);
    Contest selectBySlug(String slug);
}
