package com.ey.common.constant;

//Description: Redis的key
public class RedisConstant {
    // 登录key
    public static final String USER_LOGIN = "user:login:";
    // 验证码
    public static final String VERIFICATION_CODE = "verificationCode:";
    // 生成ai答案中的key
    public static final String GENERATE_ANSWER = "generate:answer:";

    // 题目审核的key
    public static final String TOPIC_AUDIT = "topic:audit:";
    // 题目分类审核的key
    public static final String CATEGORY_AUDIT = "category:audit:";
    // 题目标签审核的key
    public static final String LABEL_AUDIT = "label:audit:";
    // 题目专题审核的key
    public static final String SUBJECT_AUDIT = "subject:audit:";
    // 审核过期时间
    public static final Long AUDIT_EXPIRE_TIME = 30L;

    // 题目收藏的key
    public static final String USER_COLLECTIONS = "user:collections:";
    // 题目收藏redis的过期时间
    public static final Long COLLECTIONS_EXPIRE_TIME = 7L;
    // 刷题次数排行榜的key
    public static final String TOPIC_RANK_TOTAL = "topic:rank:total";
    // 今日刷题次数的key
    public static final String TOPIC_RANK_TODAY = "topic:rank:today";
    // 用户排行信息
    public static final String USER_RANK = "user:rank:";
}
