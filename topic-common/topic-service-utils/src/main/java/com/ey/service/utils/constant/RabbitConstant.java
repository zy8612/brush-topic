package com.ey.service.utils.constant;

/**
 * Description: mq常量
 */
public class RabbitConstant {
    /**
     * 专题审核
     */
    // 专题交换机
    public static final String SUBJECT_AUDIT_EXCHANGE = "subject.audit.exchange"; // 交换机
    // 专题审核队列
    public static final String SUBJECT_AUDIT_QUEUE_NAME = "subject.audit.queue";
    // 专题审核路由键
    public static final String SUBJECT_AUDIT_ROUTING_KEY_NAME = "subject.audit.routing.key";
    /**
     * 分类审核
     */
    // 分类交换机
    public static final String CATEGORY_AUDIT_EXCHANGE = "category.audit.exchange";
    // 分类审核队列
    public static final String CATEGORY_AUDIT_QUEUE_NAME = "category.audit.queue";
    // 分类审核路由键
    public static final String CATEGORY_AUDIT_ROUTING_KEY_NAME = "category.audit.routing.key";
    /**
     * 标签审核
     */
    public static final String LABEL_AUDIT_EXCHANGE = "label.audit.exchange";
    public static final String LABEL_AUDIT_QUEUE_NAME = "label.audit.queue";
    public static final String LABEL_AUDIT_ROUTING_KEY_NAME = "label.audit.routing.key";
    /**
     * 题目审核
     */
    public static final String TOPIC_AUDIT_EXCHANGE = "topic.audit.exchange";
    public static final String TOPIC_AUDIT_QUEUE_NAME = "topic.audit.queue";
    public static final String TOPIC_AUDIT_ROUTING_KEY_NAME = "topic.audit.routing.key";
    /**
     * ai生成面试答案
     */
    public static final String AI_ANSWER_EXCHANGE = "ai.answer.exchange";
    public static final String AI_ANSWER_QUEUE_NAME = "ai.answer.queue";
    public static final String AI_ANSWER_ROUTING_KEY_NAME = "ai.answer.routing.key";
}
