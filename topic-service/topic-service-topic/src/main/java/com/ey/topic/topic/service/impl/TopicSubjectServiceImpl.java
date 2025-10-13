package com.ey.topic.topic.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.DeleteEnum;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.audit.TopicAuditSubject;
import com.ey.model.dto.topic.TopicSubjectDto;
import com.ey.model.dto.topic.TopicSubjectListDto;
import com.ey.model.entity.topic.*;
import com.ey.model.excel.topic.TopicSubjectExcel;
import com.ey.model.excel.topic.TopicSubjectExcelExport;
import com.ey.model.vo.system.TopicSubjectWebVo;
import com.ey.model.vo.topic.TopicNameVo;
import com.ey.model.vo.topic.TopicSubjectDetailAndTopicVo;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.service.utils.constant.RabbitConstant;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.service.utils.mq.RabbitService;
import com.ey.topic.topic.mapper.TopicCategorySubjectMapper;
import com.ey.topic.topic.mapper.TopicMapper;
import com.ey.topic.topic.mapper.TopicSubjectMapper;
import com.ey.topic.topic.mapper.TopicSubjectTopicMapper;
import com.ey.topic.topic.service.TopicCategoryService;
import com.ey.topic.topic.service.TopicSubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicSubjectServiceImpl extends ServiceImpl<TopicSubjectMapper, TopicSubject> implements TopicSubjectService {

    private final TopicSubjectMapper topicSubjectMapper;
    private final TopicCategorySubjectMapper topicCategorySubjectMapper;
    private final TopicCategoryService topicCategoryService;
    private final RabbitService rabbitService;
    private final TopicSubjectTopicMapper topicSubjectTopicMapper;
    private final TopicMapper topicMapper;

    /**
     * 获取全部的专题
     *
     * @return
     */
    @Override
    public List<TopicSubjectVo> getAllSubject() {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户权限
        String currentRole = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和权限：{},{}", username, currentRole);
        // 设置分页条件
        LambdaQueryWrapper<TopicSubject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicSubject::getStatus, DeleteEnum.NOT_DELETED.getStatus());
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            queryWrapper.eq(TopicSubject::getCreateBy, username);
        }
        return topicSubjectMapper.selectList(queryWrapper).stream().map(item -> {
            TopicSubjectVo topicSubjectVo = new TopicSubjectVo();
            BeanUtils.copyProperties(item, topicSubjectVo);
            return topicSubjectVo;
        }).toList();
    }

    /**
     * 查询专题列表
     *
     * @param topicSubjectListDto
     * @return
     */
    @Override
    public Map<String, Object> subjectList(TopicSubjectListDto topicSubjectListDto) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户登录id
        String currentRole = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和权限：{},{}", username, currentRole);
        // 设置分页条件
        LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 判断是否为管理员
        if (!currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 不是管理员，根据当前登录用户查询
            topicSubjectLambdaQueryWrapper.like(TopicSubject::getCreateBy, username);
        } else {
            // 是管理员
            // 判断是否查询创建人
            if (!StrUtil.isEmpty(topicSubjectListDto.getCreateBy())) {
                topicSubjectLambdaQueryWrapper.like(TopicSubject::getCreateBy, topicSubjectListDto.getCreateBy());
            }
        }
        // 判断专题名称
        if (!StrUtil.isEmpty(topicSubjectListDto.getSubjectName())) {
            topicSubjectLambdaQueryWrapper.like(TopicSubject::getSubjectName, topicSubjectListDto.getSubjectName());
        }
        // 判断创建时间
        if (!StrUtil.isEmpty(topicSubjectListDto.getBeginCreateTime()) && !StrUtil.isEmpty(topicSubjectListDto.getEndCreateTime())) {
            topicSubjectLambdaQueryWrapper.between(TopicSubject::getCreateTime, topicSubjectListDto.getBeginCreateTime(), topicSubjectListDto.getEndCreateTime());
        }
        topicSubjectLambdaQueryWrapper.orderByDesc(TopicSubject::getCreateTime);
        // 设置分页参数
        Page<TopicSubject> topicSubjectPage = new Page<>(topicSubjectListDto.getPageNum(), topicSubjectListDto.getPageSize());
        // 开始查询
        Page<TopicSubject> topicSubjectPageResult = topicSubjectMapper.selectPage(topicSubjectPage, topicSubjectLambdaQueryWrapper);
        topicSubjectPageResult.getRecords().forEach(topicSubject -> {
            // 根据专题id查询分类专题表
            LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getSubjectId, topicSubject.getId());
            TopicCategorySubject topicCategorySubject = topicCategorySubjectMapper.selectOne(topicCategorySubjectLambdaQueryWrapper);
            if (ObjectUtil.isNotNull(topicCategorySubject)) {
                TopicCategory topicCategory = topicCategoryService.getById(topicCategorySubject.getCategoryId());
                if (ObjectUtil.isNotNull(topicCategory)) {
                    topicSubject.setCategoryName(topicCategory.getCategoryName());
                }
            }
        });
        // 校验参数
        if (topicSubjectListDto.getCategoryName() != null) {
            // 模糊匹配过滤分类名称
            topicSubjectPageResult.getRecords().removeIf(topicSubject -> !topicSubject.getCategoryName().contains(topicSubjectListDto.getCategoryName()));
        }
        return Map.of("total", topicSubjectPageResult.getTotal(), "rows", topicSubjectPageResult.getRecords());
    }

    /**
     * 新增专题
     *
     * @param topicSubjectDto
     */
    @Transactional
    public void addSubject(TopicSubjectDto topicSubjectDto) {
        // 查询
        TopicSubject topicSubjectDb = topicSubjectMapper.selectById(topicSubjectDto.getId());
        if (ObjectUtil.isNotNull(topicSubjectDb)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_NAME_EXIST);
        }
        // 根据专题名称查询专题
        TopicSubject topicSubjectDbByName = topicSubjectMapper.selectOne(new LambdaQueryWrapper<TopicSubject>().
                eq(TopicSubject::getSubjectName, topicSubjectDto.getSubjectName()));
        if (ObjectUtil.isNotNull(topicSubjectDbByName)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_NAME_EXIST);
        }
        //  根据分类名称查询分类
        TopicCategory topicCategoryDb = topicCategoryService
                .getOne(new LambdaQueryWrapper<TopicCategory>().
                        eq(TopicCategory::getCategoryName, topicSubjectDto.getCategoryName()));
        if (ObjectUtil.isNull(topicCategoryDb)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_SELECT_ERROR);
        }
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        TopicSubject topicSubject = new TopicSubject();
        BeanUtils.copyProperties(topicSubjectDto, topicSubject);
        topicSubject.setCreateBy(username);

        // 获取当前id
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前权限
        String currentRole = SecurityUtils.getCurrentRole();
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是开发者不需要审核
            topicSubject.setStatus(StatusEnums.NORMAL.getCode());
            topicSubjectMapper.insert(topicSubject);
        } else {
            // 不是开发者需要审核
            topicSubject.setStatus(StatusEnums.AUDITING.getCode());
            topicSubjectMapper.insert(topicSubject);
            // 封装消息对象
            TopicAuditSubject topicAuditSubject = new TopicAuditSubject();
            topicAuditSubject.setSubjectName(topicSubject.getSubjectName());
            topicAuditSubject.setCategoryName(topicCategoryDb.getCategoryName());
            topicAuditSubject.setId(topicSubject.getId());
            topicAuditSubject.setSubjectDesc(topicSubject.getSubjectDesc());
            topicAuditSubject.setAccount(username);
            topicAuditSubject.setUserId(currentId);
            // 转换json
            String topicAuditSubjectJson = JSON.toJSONString(topicAuditSubject);
            log.info("发送消息{}", topicAuditSubjectJson);
            // 异步发送消息给AI审核
            rabbitService.sendMessage(RabbitConstant.SUBJECT_AUDIT_EXCHANGE, RabbitConstant.SUBJECT_AUDIT_ROUTING_KEY_NAME, topicAuditSubjectJson);
        }

        // 插入到关系表中
        TopicCategorySubject topicCategorySubject = new TopicCategorySubject();
        topicCategorySubject.setCategoryId(topicCategoryDb.getId());
        topicCategorySubject.setSubjectId(topicSubject.getId());
        topicCategorySubjectMapper.insert(topicCategorySubject);

        topicCategoryDb.setSubjectCount(topicCategoryDb.getSubjectCount() + 1);
        // 更新分类专题数量
        topicCategoryService.updateById(topicCategoryDb);
    }

    /**
     * 删除专题
     *
     * @param ids
     */
    @Override
    @Transactional
    public void deleteSubject(Long[] ids) {
        // 校验
        if (ids == null) {
            throw new TopicException(ResultCodeEnum.SUBJECT_DELETE_IS_NULL);
        }
        for (Long id : ids) {
            // 查询题目与专题关系表
            LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getSubjectId, id);
            boolean isExist = topicSubjectTopicMapper.exists(topicSubjectTopicLambdaQueryWrapper);
            // 如果专题有相关题目
            if (isExist) {
                throw new TopicException(ResultCodeEnum.SUBJECT_DELETE_TOPIC_ERROR);
            }
            // 查询分类与专题关系表
            LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getSubjectId, id);
            TopicCategorySubject topicCategorySubject = topicCategorySubjectMapper.selectOne(topicCategorySubjectLambdaQueryWrapper);
            if (ObjectUtil.isNotNull(topicCategorySubject)) {
                TopicCategory topicCategory = topicCategoryService.getById(topicCategorySubject.getCategoryId());
                if (ObjectUtil.isNotNull(topicCategory)) {
                    topicCategory.setSubjectCount(topicCategory.getSubjectCount() - 1);
                    // 更新分类专题数量
                    topicCategoryService.updateById(topicCategory);
                }
            }
            // 删除分类与专题关系表
            topicCategorySubjectMapper.delete(topicCategorySubjectLambdaQueryWrapper);
            // 删除
            topicSubjectMapper.deleteById(id);
        }
    }

    /**
     * 修改专题
     *
     * @param topicSubjectDto
     */
    @Override
    @Transactional
    public void updateSubject(TopicSubjectDto topicSubjectDto) {
        TopicSubject topicSubjectDb = topicSubjectMapper.selectById(topicSubjectDto.getId());
        if (ObjectUtil.isNull(topicSubjectDb)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_UPDATE_IS_NULL);
        }
        // 查询原本的分类
        TopicCategory originCategory = topicCategoryService.getOne(
                new LambdaQueryWrapper<TopicCategory>()
                        .eq(TopicCategory::getId, topicCategorySubjectMapper.selectOne(
                                        new LambdaQueryWrapper<TopicCategorySubject>()
                                                .eq(TopicCategorySubject::getSubjectId, topicSubjectDto.getId()))
                                .getCategoryId()));
        // 根据分类名称查询分类
        LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCategoryName, topicSubjectDto.getCategoryName());
        TopicCategory topicCategoryDb = topicCategoryService.getOne(topicCategoryLambdaQueryWrapper);
        if (ObjectUtil.isNull(topicCategoryDb)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_SELECT_ERROR);
        }
        // 判断是否修改分类
        if (!originCategory.getCategoryName().equals(topicSubjectDto.getCategoryName())) {
            // 删除关系表
            topicCategorySubjectMapper.delete(new LambdaQueryWrapper<TopicCategorySubject>()
                    .eq(TopicCategorySubject::getSubjectId, topicSubjectDto.getId()));
            // 修改分类下专题数
            // 修改的分类+1，原本的分类-1
            topicCategoryDb.setSubjectCount(topicCategoryDb.getSubjectCount() + 1);
            originCategory.setSubjectCount(originCategory.getSubjectCount() - 1);
            List<TopicCategory> updateSubjectCount = new ArrayList<>();
            updateSubjectCount.add(topicCategoryDb);
            updateSubjectCount.add(originCategory);
            topicCategoryService.updateBatchById(updateSubjectCount);
            // 判断当前要修改的名称是否和数据库的名称一样
            isUpdateSubjectName(topicSubjectDto, topicSubjectDb, topicCategoryDb);
            // 插入到关系表中
            TopicCategorySubject topicCategorySubject = new TopicCategorySubject();
            topicCategorySubject.setCategoryId(topicCategoryDb.getId());
            topicCategorySubject.setSubjectId(topicSubjectDb.getId());
            topicCategorySubjectMapper.insert(topicCategorySubject);
        } else {
            // 判断当前要修改的名称是否和数据库的名称一样
            isUpdateSubjectName(topicSubjectDto, topicSubjectDb, topicCategoryDb);
        }
    }

    private void isUpdateSubjectName(TopicSubjectDto topicSubjectDto, TopicSubject topicSubjectDb, TopicCategory topicCategoryDb) {
        if (!topicSubjectDb.getSubjectName().equals(topicSubjectDto.getSubjectName())
                || !topicSubjectDb.getSubjectDesc().equals(topicSubjectDto.getSubjectDesc())
                || !topicSubjectDb.getImageUrl().equals(topicSubjectDto.getImageUrl())) {
            // 获取当前用户的信息
            Long currentId = SecurityUtils.getCurrentId();
            String currentRole = SecurityUtils.getCurrentRole();
            if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                // 是开发者不需要审核
                topicSubjectDb.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                // 不是开发者需要审核
                topicSubjectDb.setStatus(StatusEnums.AUDITING.getCode());
                // 异步发送消息给AI审核
                TopicAuditSubject topicAuditSubject = new TopicAuditSubject();
                topicAuditSubject.setSubjectName(topicSubjectDb.getSubjectName());
                topicAuditSubject.setCategoryName(topicCategoryDb.getCategoryName());
                topicAuditSubject.setUserId(currentId);
                topicAuditSubject.setAccount(SecurityUtils.getCurrentName());
                topicAuditSubject.setId(topicSubjectDb.getId());
                topicAuditSubject.setSubjectDesc(topicSubjectDto.getSubjectDesc());
                String topicAuditSubjectJson = JSON.toJSONString(topicAuditSubject);
                log.info("发送消息{}", topicAuditSubjectJson);
                rabbitService.sendMessage(RabbitConstant.SUBJECT_AUDIT_EXCHANGE, RabbitConstant.SUBJECT_AUDIT_ROUTING_KEY_NAME, topicAuditSubjectJson);
            }
            topicSubjectDb.setFailMsg("");
            BeanUtils.copyProperties(topicSubjectDto, topicSubjectDb);
            topicSubjectMapper.updateById(topicSubjectDb);
        }
    }

    @Override
    public void auditSubject(TopicSubject topicSubject) {
        // 查询一下这个分类存不存在
        TopicSubject topicSubjectDb = topicSubjectMapper.selectById(topicSubject.getId());
        if (topicSubjectDb == null) {
            throw new TopicException(ResultCodeEnum.SUBJECT_UPDATE_IS_NULL);
        }
        // 开始修改
        BeanUtils.copyProperties(topicSubject, topicSubjectDb);
        // 如果是正常将失败原因置空
        if (Objects.equals(topicSubject.getStatus(), StatusEnums.NORMAL.getCode())) {
            topicSubject.setFailMsg("");
        }
        topicSubjectMapper.updateById(topicSubject);
    }

    @Override
    public Set<Long> getTopicIdsBySubjectIds(List<Long> subjectIds) {
        if (CollectionUtil.isEmpty(subjectIds)) {
            return null;
        }
        // 查询专题题目关系表
        LambdaQueryWrapper<TopicSubjectTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(TopicSubjectTopic::getSubjectId, subjectIds);
        List<TopicSubjectTopic> relations = topicSubjectTopicMapper.selectList(queryWrapper);
        return relations.stream().map(TopicSubjectTopic::getTopicId).collect(Collectors.toSet());
    }

    /**
     * 根据专题id查询专题详细信息和题目列表
     * @param id
     * @return
     */
    @Override
    public TopicSubjectDetailAndTopicVo subjectDetail(Long id) {
        // 查询专题
        if (id == null) {
            return null;
        }
        TopicSubject topicSubject = topicSubjectMapper.selectById(id);
        if (topicSubject == null) {
            return null;
        }
        topicSubject.setViewCount(topicSubject.getViewCount() + 1);
        TopicSubjectDetailAndTopicVo topicSubjectDetailAndTopicVo = new TopicSubjectDetailAndTopicVo();
        BeanUtils.copyProperties(topicSubject, topicSubjectDetailAndTopicVo);
        // 查询专题题目关系表
        LambdaQueryWrapper<TopicSubjectTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicSubjectTopic::getSubjectId, id);
        List<TopicSubjectTopic> relations = topicSubjectTopicMapper.selectList(queryWrapper);
        // 题目名称列表
        List<TopicNameVo> topicNameVos = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(relations)) {
            List<Long> topicIds = relations.stream().map(TopicSubjectTopic::getTopicId).toList();
            // 根据题目id批量查询题目名称
            List<Topic> topics = topicMapper.selectBatchIds(topicIds);
            if (CollectionUtil.isNotEmpty(topics)) {
                for (Topic topic : topics) {
                    TopicNameVo topicNameVo = new TopicNameVo();
                    topicNameVo.setTopicName(topic.getTopicName());
                    topicNameVo.setId(topic.getId());
                    topicNameVos.add(topicNameVo);
                }
            }
        }
        topicSubjectDetailAndTopicVo.setTopicNameVos(topicNameVos);
        return topicSubjectDetailAndTopicVo;
    }

    /**
     * h5根据分类id查询专题
     * @param categoryId
     * @return
     */
    @Override
    public List<TopicSubjectWebVo> subject(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        TopicCategory topicCategory = topicCategoryService.getById(categoryId);
        if (topicCategory == null) {
            throw new TopicException(ResultCodeEnum.CATEGORY_NOT_EXIST);
        }
        // 查询关系表
        LambdaQueryWrapper<TopicCategorySubject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicCategorySubject::getCategoryId, categoryId);
        List<TopicCategorySubject> relations = topicCategorySubjectMapper.selectList(queryWrapper);
        if (CollectionUtil.isEmpty(relations)) {
            return null;
        }
        // 查询专题的名称
        List<Long> subjectIds = relations.stream().map(TopicCategorySubject::getSubjectId).toList();
        LambdaQueryWrapper<TopicSubject> subjectQueryWrapper = new LambdaQueryWrapper<>();
        subjectQueryWrapper.in(TopicSubject::getId, subjectIds)
                .eq(TopicSubject::getStatus, StatusEnums.NORMAL.getCode());
        List<TopicSubject> subjects = topicSubjectMapper.selectList(subjectQueryWrapper);
        return subjects.stream()
                .map(subject -> {
                    TopicSubjectWebVo topicSubjectWebVo = new TopicSubjectWebVo();
                    BeanUtils.copyProperties(subject, topicSubjectWebVo);
                    return topicSubjectWebVo;
                }).toList();
    }

    /**
     * 导出excel
     *
     * @param topicSubjectListDto
     * @param ids
     * @return
     */
    public List<TopicSubjectExcelExport> getExcelVo(TopicSubjectListDto topicSubjectListDto, Long[] ids) {
        // 是否有id
        if (ids[0] != 0) {
            // 根据id查询
            List<TopicSubject> topicSubjects = topicSubjectMapper.selectBatchIds(Arrays.asList(ids));
            if (CollectionUtils.isEmpty(topicSubjects)) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            return topicSubjects.stream().map(topicSubject -> {
                TopicSubjectExcelExport topicSubjectExcelExport = new TopicSubjectExcelExport();
                BeanUtils.copyProperties(topicSubject, topicSubjectExcelExport);
                // 状态特殊处理
                topicSubjectExcelExport.setStatus(StatusEnums.getMessageByCode(topicSubject.getStatus()));
                // 分类名称特殊处理
                // 根据专题id查询分类专题表
                LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getSubjectId, topicSubject.getId());
                TopicCategorySubject topicCategorySubject = topicCategorySubjectMapper.selectOne(topicCategorySubjectLambdaQueryWrapper);
                if (topicCategorySubject != null) {
                    TopicCategory topicCategory = topicCategoryService.getById(topicCategorySubject.getCategoryId());
                    if (topicCategory != null) {
                        topicSubjectExcelExport.setCategoryName(topicCategory.getCategoryName());
                    }
                }
                return topicSubjectExcelExport;
            }).collect(Collectors.toList());
        } else {
            Map<String, Object> map = subjectList(topicSubjectListDto);
            if (map.get("rows") == null) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            List<TopicSubject> categories = (List<TopicSubject>) map.get("rows");
            // 封装返回数据
            return categories.stream().map(topicSubject -> {
                TopicSubjectExcelExport topicSubjectExcelExport = new TopicSubjectExcelExport();
                BeanUtils.copyProperties(topicSubject, topicSubjectExcelExport);
                // 状态特殊处理
                topicSubjectExcelExport.setStatus(StatusEnums.getMessageByCode(topicSubject.getStatus()));
                return topicSubjectExcelExport;
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
    @Transactional
    public String importExcel(List<TopicSubjectExcel> excelVoList, Boolean updateSupport) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 校验
        if (CollectionUtils.isEmpty(excelVoList)) {
            throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
        }
        // 计算成功的数量
        int successNum = 0;
        // 计算失败的数量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();
        // 校验参数
        for (TopicSubjectExcel topicSubjectExcel : excelVoList) {
            if (StrUtil.isEmpty(topicSubjectExcel.getSubjectName()) || StrUtil.isEmpty(topicSubjectExcel.getSubjectDesc()) || StrUtil.isEmpty(topicSubjectExcel.getImageUrl()) || StrUtil.isEmpty(topicSubjectExcel.getCategoryName())) {
                throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
            }
            boolean isExist = topicCategoryService.exists(new LambdaQueryWrapper<TopicCategory>()
                    .eq(TopicCategory::getCategoryName, topicSubjectExcel.getCategoryName()));
            if (!isExist) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目专题： " + topicSubjectExcel.getSubjectName() + " 导入失败题目分类不存在：";
                failureMsg.append(msg);
                throw new TopicException(failureMsg.toString());
            }
        }
        // 遍历
        for (TopicSubjectExcel topicSubjectExcel : excelVoList) {
            try {
                LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicSubjectLambdaQueryWrapper.eq(TopicSubject::getSubjectName, topicSubjectExcel.getSubjectName());
                TopicSubject topicSubject = topicSubjectMapper.selectOne(topicSubjectLambdaQueryWrapper);
                if (ObjectUtil.isEmpty(topicSubject)) {
                    // 不存在插入
                    TopicSubject topicSubjectDb = new TopicSubject();
                    BeanUtils.copyProperties(topicSubjectExcel, topicSubjectDb);
                    topicSubjectDb.setCreateBy(username);
                    // 判断是否为开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topicSubjectDb.setStatus(StatusEnums.NORMAL.getCode());
                        topicSubjectMapper.insert(topicSubjectDb);
                    } else {
                        // 不是开发者需要审核
                        topicSubjectDb.setStatus(StatusEnums.AUDITING.getCode());
                        topicSubjectMapper.insert(topicSubjectDb);
                        // 封装发送消息数据
                        TopicAuditSubject topicAuditSubject = new TopicAuditSubject();
                        topicAuditSubject.setSubjectName(topicSubjectDb.getSubjectName());
                        topicAuditSubject.setCategoryName(topicSubjectExcel.getCategoryName());
                        topicAuditSubject.setId(topicSubjectDb.getId());
                        topicAuditSubject.setAccount(SecurityUtils.getCurrentName());
                        topicAuditSubject.setSubjectDesc(topicSubjectDb.getSubjectDesc());
                        topicAuditSubject.setUserId(currentId);
                        String topicAuditSubjectJson = JSON.toJSONString(topicAuditSubject);
                        log.info("发送消息{}", topicAuditSubjectJson);
                        rabbitService.sendMessage(RabbitConstant.SUBJECT_AUDIT_EXCHANGE, RabbitConstant.SUBJECT_AUDIT_ROUTING_KEY_NAME, topicAuditSubjectJson);
                    }
                    TopicCategory topicCategory = topicCategoryService.getOne(new LambdaQueryWrapper<TopicCategory>()
                            .eq(TopicCategory::getCategoryName, topicSubjectExcel.getCategoryName()));
                    // 插入到关联表
                    TopicCategorySubject topicCategorySubject = new TopicCategorySubject();
                    topicCategorySubject.setCategoryId(topicCategory.getId());
                    topicCategorySubject.setSubjectId(topicSubjectDb.getId());
                    if (!topicCategorySubjectMapper.exists(new LambdaQueryWrapper<TopicCategorySubject>()
                            .eq(TopicCategorySubject::getSubjectId, topicCategorySubject.getSubjectId()))) {
                        topicCategorySubjectMapper.insert(topicCategorySubject);
                    }
                    topicCategory.setSubjectCount(topicCategory.getSubjectCount() + 1);
                    topicCategoryService.updateById(topicCategory);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目专题：").append(topicSubjectDb.getSubjectName()).append("-导入成功");
                } else if (updateSupport) {
                    // 判断是否为开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topicSubject.setStatus(StatusEnums.NORMAL.getCode());
                    } else {
                        // 不是开发者判断当前要修改的名称和数据库的名称是否一样
                        if (!topicSubjectExcel.getSubjectName().equals(topicSubject.getSubjectName())
                                || !topicSubjectExcel.getSubjectDesc().equals(topicSubject.getSubjectDesc())
                        ) {
                            // 不是同一个名开始审核
                            // 封装发送消息数据
                            TopicAuditSubject topicAuditSubject = new TopicAuditSubject();
                            topicAuditSubject.setSubjectName(topicSubjectExcel.getSubjectName());
                            topicAuditSubject.setCategoryName(topicSubjectExcel.getCategoryName());
                            topicAuditSubject.setId(topicSubject.getId());
                            topicAuditSubject.setAccount(SecurityUtils.getCurrentName());
                            topicAuditSubject.setUserId(currentId);
                            topicAuditSubject.setSubjectDesc(topicSubjectExcel.getSubjectDesc());
                            String topicAuditSubjectJson = JSON.toJSONString(topicAuditSubject);
                            log.info("发送消息{}", topicAuditSubjectJson);
                            rabbitService.sendMessage(RabbitConstant.SUBJECT_AUDIT_EXCHANGE, RabbitConstant.SUBJECT_AUDIT_ROUTING_KEY_NAME, topicAuditSubjectJson);
                        }
                        topicSubject.setFailMsg("");
                    }
                    // 更新
                    BeanUtils.copyProperties(topicSubjectExcel, topicSubject);
                    topicSubjectMapper.updateById(topicSubject);
                    // 删除关联表
                    LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getSubjectId, topicSubject.getId());
                    TopicCategorySubject originTopicCategorySubject = topicCategorySubjectMapper.selectOne(topicCategorySubjectLambdaQueryWrapper);
                    TopicCategory originCategory = topicCategoryService.getById(originTopicCategorySubject.getCategoryId());
                    topicCategorySubjectMapper.delete(topicCategorySubjectLambdaQueryWrapper);
                    TopicCategory newCategory = topicCategoryService.getOne(new LambdaQueryWrapper<TopicCategory>()
                            .eq(TopicCategory::getCategoryName, topicSubjectExcel.getCategoryName()));
                    // 插入
                    TopicCategorySubject topicCategorySubject = new TopicCategorySubject();
                    topicCategorySubject.setCategoryId(newCategory.getId());
                    topicCategorySubject.setSubjectId(topicSubject.getId());
                    topicCategorySubjectMapper.insert(topicCategorySubject);
                    newCategory.setSubjectCount(newCategory.getSubjectCount() + 1);
                    originCategory.setSubjectCount(originCategory.getSubjectCount() - 1);
                    List<TopicCategory> updateSubjectCount = new ArrayList<>();
                    updateSubjectCount.add(newCategory);
                    updateSubjectCount.add(originCategory);
                    topicCategoryService.updateBatchById(updateSubjectCount);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目专题：").append(topicSubject.getSubjectName()).append("-更新成功");
                } else {
                    // 已存在
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-题目专题：").append(topicSubject.getSubjectName()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目专题： " + topicSubjectExcel.getSubjectName() + " 导入失败：";
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