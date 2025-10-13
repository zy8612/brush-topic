package com.ey.topic.topic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.DeleteEnum;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.audit.TopicAuditLabel;
import com.ey.model.dto.topic.TopicLabelDto;
import com.ey.model.dto.topic.TopicLabelListDto;
import com.ey.model.entity.topic.TopicLabel;
import com.ey.model.entity.topic.TopicLabelTopic;
import com.ey.model.excel.topic.TopicLabelExcel;
import com.ey.model.excel.topic.TopicLabelExcelExport;
import com.ey.model.vo.topic.TopicLabelVo;
import com.ey.service.utils.constant.RabbitConstant;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.service.utils.mq.RabbitService;
import com.ey.topic.topic.mapper.TopicLabelMapper;
import com.ey.topic.topic.mapper.TopicLabelTopicMapper;
import com.ey.topic.topic.service.TopicLabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicLabelServiceImpl extends ServiceImpl<TopicLabelMapper, TopicLabel> implements TopicLabelService {

    private final TopicLabelMapper topicLabelMapper;
    private final RabbitService rabbitService;
    private final TopicLabelTopicMapper topicLabelTopicMapper;

    @Override
    public List<TopicLabelVo> getAllLabel() {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户登录id
        String currentRole = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和权限：{},{}", username, currentRole);
        // 设置分页条件
        LambdaQueryWrapper<TopicLabel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicLabel::getStatus, DeleteEnum.NOT_DELETED.getStatus());
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            queryWrapper.eq(TopicLabel::getCreateBy, username);
        }
        return topicLabelMapper.selectList(queryWrapper).stream().map(item -> {
            TopicLabelVo topicLabelVo = new TopicLabelVo();
            BeanUtils.copyProperties(item, topicLabelVo);
            return topicLabelVo;
        }).toList();
    }

    /**
     * 分页查询标签列表
     *
     * @param topicLabelListDto
     * @return
     */
    @Override
    public Map<String, Object> labelList(TopicLabelListDto topicLabelListDto) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户登录权限
        String currentRole = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和id：{},{}", username, currentRole);
        // 设置分页条件
        LambdaQueryWrapper<TopicLabel> topiclabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 判断是否为Hao
        if (!currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 根据当前登录用户查询
            topiclabelLambdaQueryWrapper.like(TopicLabel::getCreateBy, username);
        } else {
            // 是管理员
            // 判断是否查询创建人
            if (!StrUtil.isEmpty(topicLabelListDto.getCreateBy())) {
                topiclabelLambdaQueryWrapper.like(TopicLabel::getCreateBy, topicLabelListDto.getCreateBy());
            }
        }
        // 判断标签名称
        if (!StrUtil.isEmpty(topicLabelListDto.getLabelName())) {
            topiclabelLambdaQueryWrapper.like(TopicLabel::getLabelName, topicLabelListDto.getLabelName());
        }
        // 判断创建时间
        if (!StrUtil.isEmpty(topicLabelListDto.getBeginCreateTime()) && !StrUtil.isEmpty(topicLabelListDto.getEndCreateTime())) {
            topiclabelLambdaQueryWrapper.between(TopicLabel::getCreateTime, topicLabelListDto.getBeginCreateTime(), topicLabelListDto.getEndCreateTime());
        }
        topiclabelLambdaQueryWrapper.orderByDesc(TopicLabel::getCreateTime);
        // 设置分页参数
        Page<TopicLabel> topicLabelPage = new Page<>(topicLabelListDto.getPageNum(), topicLabelListDto.getPageSize());
        // 开始查询
        Page<TopicLabel> topicLabelList = topicLabelMapper.selectPage(topicLabelPage, topiclabelLambdaQueryWrapper);
        return Map.of("total", topicLabelList.getTotal(), "rows", topicLabelList.getRecords());
    }

    /**
     * 根据标签id查询标签名称
     * @param labelIds
     * @return
     */
    @Override
    public List<String> getLabelNamesByIds(List<Long> labelIds) {
        return topicLabelMapper.getLabelNamesByIds(labelIds);
    }

    /**
     * 添加题目专题
     * @param topicLabelDto
     */
    @Override
    public void addLabel(TopicLabelDto topicLabelDto) {
        LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLabelLambdaQueryWrapper.eq(TopicLabel::getLabelName, topicLabelDto.getLabelName());
        // 查询
        TopicLabel topicLabelDb = topicLabelMapper.selectOne(topicLabelLambdaQueryWrapper);
        if (topicLabelDb != null) {
            throw new TopicException(ResultCodeEnum.LABEL_NAME_EXIST);
        }
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        TopicLabel topicLabel = new TopicLabel();
        topicLabel.setLabelName(topicLabelDto.getLabelName());
        topicLabel.setCreateBy(username);
        // 获取当前用户id
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前用户权限
        String currentRole = SecurityUtils.getCurrentRole();
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是开发者不需要审核
            topicLabel.setStatus(StatusEnums.NORMAL.getCode());
            topicLabelMapper.insert(topicLabel);
        } else {
            // 不是开发者进行审核
            topicLabel.setStatus(StatusEnums.AUDITING.getCode());
            topicLabelMapper.insert(topicLabel);
            // 异步发送消息给AI审核
            TopicAuditLabel topicAuditLabel = new TopicAuditLabel();
            topicAuditLabel.setLabelName(topicLabelDto.getLabelName());
            topicAuditLabel.setAccount(username);
            topicAuditLabel.setUserId(currentId);
            topicAuditLabel.setId(topicLabel.getId());
            String topicAuditLabelJson = JSON.toJSONString(topicAuditLabel);
            log.info("发送消息{}", topicAuditLabelJson);
            rabbitService.sendMessage(RabbitConstant.LABEL_AUDIT_EXCHANGE, RabbitConstant.LABEL_AUDIT_ROUTING_KEY_NAME, topicAuditLabelJson);
        }
    }

    /**
     * 删除题目标签
     * @param ids
     */
    @Override
    public void deleteLabel(Long[] ids) {
        // 校验
        if (ids == null) {
            throw new TopicException(ResultCodeEnum.LABEL_DELETE_IS_NULL);
        }
        for (Long id : ids) {
            LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            // 查询标签与题目关系表
            topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getLabelId, id);
            TopicLabelTopic topicLabelTopic = topicLabelTopicMapper.selectOne(topicLabelTopicLambdaQueryWrapper);
            if (ObjectUtil.isNotNull(topicLabelTopic)) {
                throw new TopicException(ResultCodeEnum.LABEL_DELETE_TOPIC_ERROR);
            }
            // 删除
            topicLabelMapper.deleteById(id);
        }
    }

    /**
     * 修改题目标签
     *
     * @param topicLabelDto
     */
    @Override
    public void updateLabel(TopicLabelDto topicLabelDto) {
        // 查询
        TopicLabel topicLabel = topicLabelMapper.selectById(topicLabelDto.getId());
        if (ObjectUtil.isNull(topicLabel)) {
            throw new TopicException(ResultCodeEnum.LABEL_NOT_EXIST);
        }
        TopicLabel isExist = topicLabelMapper.selectOne(new LambdaQueryWrapper<TopicLabel>()
                .eq(TopicLabel::getLabelName, topicLabelDto.getLabelName()));
        if (ObjectUtil.isNotNull(isExist) && !isExist.getId().equals(topicLabel.getId())) {
            throw new TopicException(ResultCodeEnum.LABEL_NAME_EXIST);
        }
        // 判断是否要修改的名称是一样的
        if (!topicLabel.getLabelName().equals(topicLabelDto.getLabelName())) {
            // 不一样
            // 异步发送消息给AI审核
            // 判断是否是开发者
            String currentRole = SecurityUtils.getCurrentRole();
            Long currentId = SecurityUtils.getCurrentId();
            if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                // 是开发者不需要审核
                topicLabel.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                // 不是开发者进行审核
                topicLabel.setStatus(StatusEnums.AUDITING.getCode());
                // 异步发送消息给AI审核
                TopicAuditLabel topicAuditLabel = new TopicAuditLabel();
                topicAuditLabel.setLabelName(topicLabelDto.getLabelName());
                topicAuditLabel.setAccount(SecurityUtils.getCurrentName());
                topicAuditLabel.setUserId(currentId);
                topicAuditLabel.setId(topicLabelDto.getId());
                String topicAuditLabelJson = JSON.toJSONString(topicAuditLabel);
                log.info("发送消息{}", topicAuditLabelJson);
                rabbitService.sendMessage(RabbitConstant.LABEL_AUDIT_EXCHANGE, RabbitConstant.LABEL_AUDIT_ROUTING_KEY_NAME, topicAuditLabelJson);
            }
            topicLabel.setFailMsg("");
            // 开始修改
            topicLabel.setLabelName(topicLabelDto.getLabelName());
            topicLabelMapper.updateById(topicLabel);
        }
    }


    /**
     * 导出excel
     *
     * @param topicLabelDto
     * @param ids
     * @return
     */
    public List<TopicLabelExcelExport> getExcelVo(TopicLabelListDto topicLabelDto, Long[] ids) {
        // 是否有id
        if (ids != null && ids.length > 0 && ids[0] != 0) {
            // 根据id查询
            List<TopicLabel> topicLabels = topicLabelMapper.selectBatchIds(Arrays.asList(ids));
            if (CollectionUtils.isEmpty(topicLabels)) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            return topicLabels.stream().map(TopicLabel -> {
                TopicLabelExcelExport TopicLabelExcelExport = new TopicLabelExcelExport();
                BeanUtils.copyProperties(TopicLabel, TopicLabelExcelExport);
                // 状态特殊处理
                TopicLabelExcelExport.setStatus(StatusEnums.getMessageByCode(TopicLabel.getStatus()));
                return TopicLabelExcelExport;
            }).collect(Collectors.toList());
        } else {
            Map<String, Object> map = labelList(topicLabelDto);
            if (map.get("rows") == null) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            List<TopicLabel> topicLabels = (List<TopicLabel>) map.get("rows");
            // 封装返回数据
            return topicLabels.stream().map(TopicLabel -> {
                TopicLabelExcelExport TopicLabelExcelExport = new TopicLabelExcelExport();
                BeanUtils.copyProperties(TopicLabel, TopicLabelExcelExport);
                // 状态特殊处理
                TopicLabelExcelExport.setStatus(StatusEnums.getMessageByCode(TopicLabel.getStatus()));
                return TopicLabelExcelExport;
            }).collect(Collectors.toList());
        }
    }

    /**
     * 导入excel
     *
     * @param excelVoList
     * @param updateSupport
     * @return
     */
    public String importExcel(List<TopicLabelExcel> excelVoList, Boolean updateSupport){
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 计算成功的数量
        int successNum = 0;
        // 计算失败的数量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();
        // 遍历
        for (TopicLabelExcel topicLabelExcel : excelVoList) {
            try {
                LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicLabelLambdaQueryWrapper.eq(TopicLabel::getLabelName, topicLabelExcel.getLabelName());
                TopicLabel topicLabel = topicLabelMapper.selectOne(topicLabelLambdaQueryWrapper);
                if (ObjectUtil.isNull(topicLabel)) {
                    // 不存在插入
                    TopicLabel topicLabelDb = new TopicLabel();
                    BeanUtils.copyProperties(topicLabelExcel, topicLabelDb);
                    topicLabelDb.setCreateBy(username);
                    // 判断是否为开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topicLabelDb.setStatus(StatusEnums.NORMAL.getCode());
                        topicLabelMapper.insert(topicLabelDb);
                    } else {
                        // 不是开发者需要审核
                        topicLabelDb.setStatus(StatusEnums.AUDITING.getCode());
                        topicLabelMapper.insert(topicLabelDb);
                        // 封装发送消息数据
                        TopicAuditLabel topicAuditLabel = new TopicAuditLabel();
                        // 封装审核消息
                        topicAuditLabel.setAccount(username);
                        topicAuditLabel.setUserId(currentId);
                        topicAuditLabel.setLabelName(topicLabelDb.getLabelName());
                        topicAuditLabel.setId(topicLabelDb.getId());
                        // 转换字符串
                        String jsonString = JSON.toJSONString(topicAuditLabel);
                        log.info("发送消息{}", jsonString);
                        // 异步调用发送消息给ai审核
                        rabbitService.sendMessage(RabbitConstant.LABEL_AUDIT_EXCHANGE, RabbitConstant.LABEL_AUDIT_ROUTING_KEY_NAME, jsonString);
                    }
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目标签：").append(topicLabelDb.getLabelName()).append("-导入成功");
                } else if (updateSupport) {
                    // 判断要更新的名称和当前数据库的名称是否一致
                    if (!topicLabel.getLabelName().equals(topicLabelExcel.getLabelName())) {
                        // 不一致
                        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                            // 是开发者不需要审核
                            topicLabel.setStatus(StatusEnums.NORMAL.getCode());
                        } else {
                            // 不是开发者需要审核
                            topicLabel.setStatus(StatusEnums.AUDITING.getCode());
                            // 封装发送消息数据
                            TopicAuditLabel topicAuditLabel = new TopicAuditLabel();
                            topicAuditLabel.setAccount(username);
                            topicAuditLabel.setUserId(currentId);
                            topicAuditLabel.setLabelName(topicLabelExcel.getLabelName());
                            topicAuditLabel.setId(topicLabel.getId());
                            String jsonString = JSON.toJSONString(topicAuditLabel);
                            log.info("发送消息{}", jsonString);
                            rabbitService.sendMessage(RabbitConstant.LABEL_AUDIT_EXCHANGE, RabbitConstant.LABEL_AUDIT_ROUTING_KEY_NAME, jsonString);
                        }
                        topicLabel.setFailMsg("");
                    }
                    // 更新
                    BeanUtils.copyProperties(topicLabelExcel, topicLabel);
                    topicLabelMapper.updateById(topicLabel);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目标签：").append(topicLabel.getLabelName()).append("-更新成功");
                } else {
                    // 已存在
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-题目标签：").append(topicLabel.getLabelName()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目标签： " + topicLabelExcel.getLabelName() + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new TopicException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
}
