package com.ey.topic.ai.listener;

import com.alibaba.fastjson2.JSON;
import com.ey.common.constant.RedisConstant;
import com.ey.model.dto.audit.TopicAudit;
import com.ey.model.dto.audit.TopicAuditCategory;
import com.ey.model.dto.audit.TopicAuditLabel;
import com.ey.model.dto.audit.TopicAuditSubject;
import com.ey.service.utils.constant.RabbitConstant;
import com.ey.topic.ai.service.AiModelService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuditListener {

    @Autowired
    private AiModelService aiModelService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 接收生产者题目分类发送审核的信息
     *
     * @param topicAuditCategoryJson 要审核的分类
     * @param message                接收到的完整消息对象
     * @param channel                跟mq通信的方法
     */
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = RabbitConstant.CATEGORY_AUDIT_QUEUE_NAME),// 存储消息队列
                    exchange = @Exchange(value = RabbitConstant.CATEGORY_AUDIT_EXCHANGE),// 转发消息的交换机
                    key = {RabbitConstant.CATEGORY_AUDIT_ROUTING_KEY_NAME}))// 路由key
    public void auditCategory(String topicAuditCategoryJson, Message message, Channel channel) {
        log.info("接收到分类审核消息{}", topicAuditCategoryJson);
        // json转换
        TopicAuditCategory topicAuditCategory = JSON.parseObject(topicAuditCategoryJson, TopicAuditCategory.class);
        // 锁的key
        String lockKey = RedisConstant.CATEGORY_AUDIT + topicAuditCategory.getId();
        // 锁的value
        String lockValue = String.valueOf(topicAuditCategory.getId());
        try {
            // 尝试获取锁,并设置过期时间
            Boolean acquiredLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue,
                    RedisConstant.AUDIT_EXPIRE_TIME, TimeUnit.SECONDS);
            // 如果没有获取到锁，说明审核任务在执行
            if (acquiredLock == null || !acquiredLock) {
                log.info("分类审核消息正在处理中，忽略重复消息: {}", topicAuditCategory.getId());
                // 确认消息，避免重复消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
            // 开始审核
            aiModelService.auditCategory(topicAuditCategory);
            // 手动确认该消息 通过唯一标识已被消费，明确告知RabbitMQ服务器，当前消息已被正确处理，可以从队列中删除
            // 参数1：标号用于消息确认 记载 消息重试等  参数2：仅确认当前消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            // 记录审核日志
            aiModelService.recordAuditLog("服务器发生异常", topicAuditCategory.getAccount(), topicAuditCategory.getUserId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 接收生产者题目专题发送审核的信息
     *
     * @param topicAuditSubjectJson 要审核的专题
     * @param message               接收到的完整消息对象
     * @param channel               跟mq通信的方法
     */
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = RabbitConstant.SUBJECT_AUDIT_QUEUE_NAME),// 存储消息队列
                    exchange = @Exchange(value = RabbitConstant.SUBJECT_AUDIT_EXCHANGE),// 转发消息的交换机
                    key = {RabbitConstant.SUBJECT_AUDIT_ROUTING_KEY_NAME}))// 路由key
    public void auditSubject(String topicAuditSubjectJson, Message message, Channel channel) {
        log.info("接收到专题审核消息{}", topicAuditSubjectJson);
        TopicAuditSubject topicAuditSubject = JSON.parseObject(topicAuditSubjectJson, TopicAuditSubject.class);
        String lockKey = RedisConstant.SUBJECT_AUDIT + topicAuditSubject.getId();
        String lockValue = String.valueOf(topicAuditSubject.getId());
        try {
            Boolean acquiredLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue,
                    RedisConstant.AUDIT_EXPIRE_TIME, TimeUnit.SECONDS);
            if (acquiredLock == null || !acquiredLock) {
                log.info("审核消息正在处理中，忽略重复消息: {}", topicAuditSubject.getId());
                // 确认消息，避免重复消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 开始审核
            aiModelService.auditSubject(topicAuditSubject);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            aiModelService.recordAuditLog("服务器发生异常", topicAuditSubject.getAccount(), topicAuditSubject.getUserId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 接收生产者题目标签发送审核的信息
     *
     * @param topicAuditLabelJson 要审核的标签
     * @param message             接收到的完整消息对象
     * @param channel             跟mq通信的方法
     */
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = RabbitConstant.LABEL_AUDIT_QUEUE_NAME),// 存储消息队列
                    exchange = @Exchange(value = RabbitConstant.LABEL_AUDIT_EXCHANGE),// 转发消息的交换机
                    key = {RabbitConstant.LABEL_AUDIT_ROUTING_KEY_NAME}))// 路由key
    public void auditLabel(String topicAuditLabelJson, Message message, Channel channel) {
        log.info("接收到标签审核消息{}", topicAuditLabelJson);
        // 转换json
        TopicAuditLabel topicAuditLabel = JSON.parseObject(topicAuditLabelJson, TopicAuditLabel.class);
        // 锁的key
        String lockKey = RedisConstant.LABEL_AUDIT + topicAuditLabel.getId();
        // 锁的value
        String lockValue = String.valueOf(topicAuditLabel.getId());
        try {
            // 尝试获取锁,并设置过期时间
            Boolean acquiredLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, RedisConstant.AUDIT_EXPIRE_TIME, TimeUnit.MINUTES);
            if (acquiredLock == null || !acquiredLock) {
                log.info("审核消息正在处理中，忽略重复消息: {}", topicAuditLabel.getId());
                // 确认消息，避免重复消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 开始审核
            aiModelService.auditLabel(topicAuditLabel);
            // 手动确认该消息 通过唯一标识已被消费
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            aiModelService.recordAuditLog("服务器发生异常", topicAuditLabel.getAccount(), topicAuditLabel.getUserId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 接收生产者题目发送审核的信息
     *
     * @param topicAuditJson 要审核的题目
     * @param message        接收到的完整消息对象
     * @param channel        跟mq通信的方法
     */
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = RabbitConstant.TOPIC_AUDIT_QUEUE_NAME),// 存储消息队列
                    exchange = @Exchange(value = RabbitConstant.TOPIC_AUDIT_EXCHANGE),// 转发消息的交换机
                    key = {RabbitConstant.TOPIC_AUDIT_ROUTING_KEY_NAME}))// 路由key
    public void auditTopic(String topicAuditJson, Message message, Channel channel) {
        log.info("接收到题目审核消息{}", topicAuditJson);
        // 转换json
        TopicAudit topicAudit = JSON.parseObject(topicAuditJson, TopicAudit.class);
        // 锁的key
        String lockKey = RedisConstant.TOPIC_AUDIT + topicAudit.getId();
        // 锁的value
        String lockValue = String.valueOf(topicAudit.getId());
        try {
            // 尝试获取锁,并设置过期时间
            Boolean acquiredLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, RedisConstant.AUDIT_EXPIRE_TIME, TimeUnit.MINUTES);
            if (acquiredLock == null || !acquiredLock) {
                log.info("审核消息正在处理中，忽略重复消息: {}", topicAudit.getId());
                // 确认消息，避免重复消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 开始审核
            aiModelService.auditTopic(topicAudit);
            // 手动确认该消息 通过唯一标识已被消费
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            aiModelService.recordAuditLog("服务器发生异常", topicAudit.getAccount(), topicAudit.getUserId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 接收生产者题目发送生成ai答案的信息
     *
     * @param topicAuditJson 要生成的题目
     * @param message        接收到的完整消息对象
     * @param channel        跟mq通信的方法
     */
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = RabbitConstant.AI_ANSWER_QUEUE_NAME),// 存储消息队列
                    exchange = @Exchange(value = RabbitConstant.AI_ANSWER_EXCHANGE),// 转发消息的交换机
                    key = {RabbitConstant.AI_ANSWER_ROUTING_KEY_NAME}))// 路由key
    public void generateAnswer(String topicAuditJson, Message message, Channel channel) {
        log.info("接收到题目生成答案消息{}", topicAuditJson);
        // 转换json
        TopicAudit topicAudit = JSON.parseObject(topicAuditJson, TopicAudit.class);
        // 锁的key
        String lockKey = RedisConstant.TOPIC_AUDIT + topicAudit.getId();
        // 锁的value
        String lockValue = String.valueOf(topicAudit.getId());
        try {
            // 尝试获取锁,并设置过期时间
            Boolean acquiredLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, RedisConstant.AUDIT_EXPIRE_TIME, TimeUnit.MINUTES);
            if (acquiredLock == null || !acquiredLock) {
                log.info("正在生成答案中，请耐心等待");
                // 确认消息，避免重复消费
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            // 开始审核
            aiModelService.generateAnswer(topicAudit);
            // 手动确认该消息 通过唯一标识已被消费
            // 参数1：标号用于消息确认 记载 消息重试等
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            aiModelService.recordAuditLog("服务器发生异常", topicAudit.getAccount(), topicAudit.getUserId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }
}