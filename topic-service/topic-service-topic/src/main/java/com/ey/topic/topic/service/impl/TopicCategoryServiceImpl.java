package com.ey.topic.topic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.audit.TopicAuditCategory;
import com.ey.model.dto.topic.TopicCategoryDto;
import com.ey.model.dto.topic.TopicCategoryListDto;
import com.ey.model.entity.topic.TopicCategory;
import com.ey.model.entity.topic.TopicCategorySubject;
import com.ey.model.excel.topic.TopicCategoryExcel;
import com.ey.model.excel.topic.TopicCategoryExcelExport;
import com.ey.model.vo.topic.TopicCategoryVo;
import com.ey.service.utils.constant.RabbitConstant;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.service.utils.mq.RabbitService;
import com.ey.topic.topic.mapper.TopicCategoryMapper;
import com.ey.topic.topic.mapper.TopicCategorySubjectMapper;
import com.ey.topic.topic.service.TopicCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicCategoryServiceImpl extends ServiceImpl<TopicCategoryMapper, TopicCategory> implements TopicCategoryService {

    private final TopicCategoryMapper topicCategoryMapper;
    private final RabbitService rabbitService;
    private final TopicCategorySubjectMapper topicCategorySubjectMapper;

    /**
     * 分页查询分类列表
     *
     * @param topicCategoryDto
     * @return
     */
    @Override
    public Map<String, Object> categoryList(TopicCategoryListDto topicCategoryDto) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户登录id
        String currentRole = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和权限：{},{}", username, currentRole);
        // 设置分页条件
        LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 判断是否为管理员
        if (!currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 不是管理员，根据当前登录用户查询
            topicCategoryLambdaQueryWrapper.like(TopicCategory::getCreateBy, username);
        } else {
            // 是管理员
            // 判断是否查询创建人
            if (!StrUtil.isEmpty(topicCategoryDto.getCreateBy())) {
                topicCategoryLambdaQueryWrapper.like(TopicCategory::getCreateBy, topicCategoryDto.getCreateBy());
            }
        }
        // 判断分类名称
        if (!StrUtil.isEmpty(topicCategoryDto.getCategoryName())) {
            topicCategoryLambdaQueryWrapper.like(TopicCategory::getCategoryName, topicCategoryDto.getCategoryName());
        }
        // 判断创建时间
        if (!StrUtil.isEmpty(topicCategoryDto.getBeginCreateTime()) && !StrUtil.isEmpty(topicCategoryDto.getEndCreateTime())) {
            topicCategoryLambdaQueryWrapper.between(TopicCategory::getCreateTime, topicCategoryDto.getBeginCreateTime(), topicCategoryDto.getEndCreateTime());
        }
        topicCategoryLambdaQueryWrapper.orderByDesc(TopicCategory::getCreateTime);
        if (topicCategoryDto.getPageNum() == null || topicCategoryDto.getPageSize() == null) {
            if (topicCategoryDto.getStatus() != null) {
                topicCategoryLambdaQueryWrapper.eq(TopicCategory::getStatus, topicCategoryDto.getStatus());
            }
            List<TopicCategory> categories = topicCategoryMapper.selectList(topicCategoryLambdaQueryWrapper);
            return Map.of("total", categories.size(), "rows", categories);
        } else {
            // 设置分页参数
            Page<TopicCategory> topicCategoryPage = new Page<>(topicCategoryDto.getPageNum(), topicCategoryDto.getPageSize());
            // 开始查询
            Page<TopicCategory> topicCategoryList = topicCategoryMapper.selectPage(topicCategoryPage, topicCategoryLambdaQueryWrapper);
            return Map.of("total", topicCategoryList.getTotal(), "rows", topicCategoryList.getRecords());
        }
    }

    /**
     * 添加题目分类
     *
     * @param topicCategoryDto
     */
    @Override
    public void addCategory(TopicCategoryDto topicCategoryDto) {
        // 校验分类名称
        if (StrUtil.isEmpty(topicCategoryDto.getCategoryName())) {
            throw new TopicException(ResultCodeEnum.CATEGORY_NAME_IS_NULL);
        }
        LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCategoryName, topicCategoryDto.getCategoryName());
        TopicCategory topicCategoryDb = topicCategoryMapper.selectOne(topicCategoryLambdaQueryWrapper);
        if (ObjectUtil.isNotNull(topicCategoryDb)) {
            throw new TopicException(ResultCodeEnum.CATEGORY_NAME_EXIST);
        }
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前id
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前权限
        String currentRole = SecurityUtils.getCurrentRole();
        TopicCategory topicCategory = new TopicCategory();
        topicCategory.setCategoryName(topicCategoryDto.getCategoryName());
        topicCategory.setCreateBy(username);
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是管理员不需要审核
            topicCategory.setStatus(StatusEnums.NORMAL.getCode());
            topicCategoryMapper.insert(topicCategory);
        } else {
            // 不是开发者进行审核
            topicCategory.setStatus(StatusEnums.AUDITING.getCode());
            topicCategoryMapper.insert(topicCategory);
            topicCategoryDto.setId(topicCategory.getId());
            // 封装审核消息
            TopicAuditCategory topicAuditCategory = new TopicAuditCategory();
            BeanUtils.copyProperties(topicCategoryDto, topicAuditCategory);
            // 设置账户
            topicAuditCategory.setAccount(username);
            // 设置id
            topicAuditCategory.setUserId(currentId);
            // 转换json
            String jsonString = JSON.toJSONString(topicAuditCategory);
            log.info("发送的消息：{}", jsonString);
            // 异步发送消息调用ai审核
            rabbitService.sendMessage(RabbitConstant.CATEGORY_AUDIT_EXCHANGE, RabbitConstant.CATEGORY_AUDIT_ROUTING_KEY_NAME, jsonString);
        }
    }

    /**
     * 删除分类
     * @param ids
     */
    @Override
    public void deleteCategory(Long[] ids) {
        // 校验
        if (ids == null) {
            throw new TopicException(ResultCodeEnum.CATEGORY_DELETE_IS_NULL);
        }
        for (Long id : ids) {
            LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
            // 查询分类与专题关系表
            topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getCategoryId, id);
            boolean isExist = topicCategorySubjectMapper.exists(topicCategorySubjectLambdaQueryWrapper);
            if (isExist) {
                throw new TopicException(ResultCodeEnum.CATEGORY_DELETE_TOPIC_ERROR);
            }
            // 删除
            topicCategoryMapper.deleteById(id);
        }
    }

    /**
     * 修改题目分类
     * @param topicCategoryDto
     */
    @Override
    public void updateCategory(TopicCategoryDto topicCategoryDto) {
        // 校验分类名称
        if (StrUtil.isEmpty(topicCategoryDto.getCategoryName())) {
            throw new TopicException(ResultCodeEnum.CATEGORY_NAME_IS_NULL);
        }
        // 查询
        TopicCategory topicCategory = topicCategoryMapper.selectById(topicCategoryDto.getId());
        if (ObjectUtil.isNull(topicCategory)) {
            throw new TopicException(ResultCodeEnum.CATEGORY_UPDATE_IS_NULL);
        }
        // 获取当前的权限
        String role = SecurityUtils.getCurrentRole();
        // 判断当前名称和要修改的名称是否一样
        if (!topicCategory.getCategoryName().equals(topicCategoryDto.getCategoryName())) {
            // 查询新的名字是否存在
            LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCategoryName, topicCategoryDto.getCategoryName());
            boolean isExist = topicCategoryMapper.exists(topicCategoryLambdaQueryWrapper);
            if (isExist) {
                throw new TopicException(ResultCodeEnum.CATEGORY_NAME_EXIST);
            }
            // 如果是管理员，不需要审核
            if (role.equals(RoleEnum.ADMIN.getRoleKey())) {
                // 是开发者不需要审核
                topicCategory.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                // 不是开发者需要审核一下分类名称
                topicCategory.setStatus(StatusEnums.AUDITING.getCode());
                TopicAuditCategory topicAuditCategory = new TopicAuditCategory();
                BeanUtils.copyProperties(topicCategoryDto, topicAuditCategory);
                // 设置账户
                topicAuditCategory.setAccount(SecurityUtils.getCurrentName());
                topicAuditCategory.setUserId(SecurityUtils.getCurrentId());
                // 转换json
                String jsonString = JSON.toJSONString(topicAuditCategory);
                log.info("发送的消息：{}", jsonString);
                // 异步发送审核信息
                rabbitService.sendMessage(RabbitConstant.CATEGORY_AUDIT_EXCHANGE, RabbitConstant.CATEGORY_AUDIT_ROUTING_KEY_NAME, jsonString);
            }
            topicCategory.setFailMsg("");
        }
        // 开始修改
        topicCategory.setCategoryName(topicCategoryDto.getCategoryName());
        topicCategoryMapper.updateById(topicCategory);
    }

    /**
     * h5获取用户的题库分类列表
     * @param isCustomQuestion
     * @return
     */
    @Override
    public List<TopicCategoryVo> category(Boolean isCustomQuestion) {
        // 获取创建人
        String createBy = SecurityUtils.getCurrentName();
        // 获取当前登录用户角色
        String role = SecurityUtils.getCurrentRole();
        // 全部数据
        List<TopicCategory> topicCategoriesAll = null;
        // 会员数据
        List<TopicCategory> topicCategoriesMember = null;
        // 查公共的数据
        LambdaQueryWrapper<TopicCategory> objectLambdaQueryWrapper = new LambdaQueryWrapper<>();
        objectLambdaQueryWrapper.eq(TopicCategory::getStatus, StatusEnums.NORMAL.getCode())
                .eq(TopicCategory::getCreateBy, "admin")
                .orderByDesc(TopicCategory::getCreateTime);
        topicCategoriesAll = topicCategoryMapper.selectList(objectLambdaQueryWrapper);
        if (role.equals("member")) {
            if (isCustomQuestion) {
                // 是会员可以查自己专属的
                // 为true说明开启了可以查自己的
                LambdaQueryWrapper<TopicCategory> lambdaQueryWrapper = new LambdaQueryWrapper<>();
                lambdaQueryWrapper.eq(TopicCategory::getStatus, StatusEnums.NORMAL.getCode());
                lambdaQueryWrapper.orderByDesc(TopicCategory::getCreateTime);
                lambdaQueryWrapper.eq(TopicCategory::getCreateBy, createBy);
                topicCategoriesMember = topicCategoryMapper.selectList(lambdaQueryWrapper);
            }
        }
        // 判断会员数据是否为空
        if (!CollectionUtils.isEmpty(topicCategoriesMember)) {
            // 不为空将会员数据放在全部数据的前面
            topicCategoriesAll.addAll(0, topicCategoriesMember);
        }
        return topicCategoriesAll.stream().map(topicCategory -> {
            TopicCategoryVo topicCategoryVo = new TopicCategoryVo();
            BeanUtils.copyProperties(topicCategory, topicCategoryVo);
            return topicCategoryVo;
        }).toList();
    }

    @Override
    public void auditCategory(TopicCategory topicCategory) {
        // 查询一下这个分类存不存在
        TopicCategory topicCategoryDb = topicCategoryMapper.selectById(topicCategory.getId());
        if (topicCategoryDb == null) {
            throw new TopicException(ResultCodeEnum.CATEGORY_UPDATE_IS_NULL);
        }
        // 开始修改
        BeanUtils.copyProperties(topicCategory, topicCategoryDb);
        // 如果是正常将失败原因置空
        if (Objects.equals(topicCategory.getStatus(), StatusEnums.NORMAL.getCode())) {
            topicCategoryDb.setFailMsg("");
        }
        topicCategoryMapper.updateById(topicCategoryDb);
    }

    /**
     * 导出excel数据
     *
     * @param topicCategoryDto
     * @param ids
     * @return
     */
    public List<TopicCategoryExcelExport> getExcelVo(TopicCategoryListDto topicCategoryDto, Long[] ids) {
        // 是否有id
        if (ids != null && ids.length > 0 && ids[0] != 0) {
            // 根据id查询
            List<TopicCategory> topicCategories = topicCategoryMapper.selectBatchIds(Arrays.asList(ids));
            if (CollectionUtils.isEmpty(topicCategories)) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            return topicCategories.stream().map(topicCategory -> {
                TopicCategoryExcelExport topicCategoryExcelExport = new TopicCategoryExcelExport();
                BeanUtils.copyProperties(topicCategory, topicCategoryExcelExport);
                // 状态特殊处理
                topicCategoryExcelExport.setStatus(StatusEnums.getMessageByCode(topicCategory.getStatus()));
                return topicCategoryExcelExport;
            }).collect(Collectors.toList());
        } else {
            Map<String, Object> map = categoryList(topicCategoryDto);
            if (map.get("rows") == null) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            List<TopicCategory> categories = (List<TopicCategory>) map.get("rows");
            // 封装返回数据
            return categories.stream().map(topicCategory -> {
                TopicCategoryExcelExport topicCategoryExcelExport = new TopicCategoryExcelExport();
                BeanUtils.copyProperties(topicCategory, topicCategoryExcelExport);
                // 状态特殊处理
                topicCategoryExcelExport.setStatus(StatusEnums.getMessageByCode(topicCategory.getStatus()));
                return topicCategoryExcelExport;
            }).collect(Collectors.toList());
        }
    }

    /**
     * 导入excel数据
     *
     * @param excelVoList
     * @param updateSupport
     * @return
     */
    public String importExcel(List<TopicCategoryExcel> excelVoList, Boolean updateSupport) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 计算成功的数量
        int successNum = 0;
        // 计算失败的数量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();
        // 获取当前用户id
        Long currentId = SecurityUtils.getCurrentId();
        // 遍历
        for (TopicCategoryExcel topicCategoryExcel : excelVoList) {
            try {
                LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCategoryName, topicCategoryExcel.getCategoryName());
                TopicCategory topicCategory = topicCategoryMapper.selectOne(topicCategoryLambdaQueryWrapper);
                if (ObjectUtil.isEmpty(topicCategory)) {
                    // 不存在插入
                    TopicCategory topicCategoryDb = new TopicCategory();
                    BeanUtils.copyProperties(topicCategoryExcel, topicCategoryDb);
                    topicCategoryDb.setCreateBy(username);
                    if (currentId == 1L) {
                        // 是开发者不需要审核
                        topicCategoryDb.setStatus(StatusEnums.NORMAL.getCode());
                        topicCategoryMapper.insert(topicCategoryDb);
                    } else {
                        // 不是开发者是会员需要审核
                        topicCategoryDb.setStatus(StatusEnums.AUDITING.getCode());
                        topicCategoryMapper.insert(topicCategoryDb);
                        // 封装发送消息数据
                        TopicCategoryDto topicCategoryDto = new TopicCategoryDto();
                        topicCategoryDto.setCategoryName(topicCategoryExcel.getCategoryName());
                        topicCategoryDto.setId(topicCategoryDb.getId());
                        // 封装审核消息
                        TopicAuditCategory topicAuditCategory = new TopicAuditCategory();
                        BeanUtils.copyProperties(topicCategoryDto, topicAuditCategory);
                        // 设置账户
                        topicAuditCategory.setAccount(SecurityUtils.getCurrentName());
                        // 设置id
                        topicAuditCategory.setUserId(currentId);
                        // 转换字符串
                        String jsonString = JSON.toJSONString(topicAuditCategory);
                        log.info("发送消息{}", jsonString);
                        // 异步调用发送消息给ai审核
                        rabbitService.sendMessage(RabbitConstant.CATEGORY_AUDIT_EXCHANGE, RabbitConstant.CATEGORY_AUDIT_ROUTING_KEY_NAME, jsonString);
                    }
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目分类：").append(topicCategoryDb.getCategoryName()).append("-导入成功");
                } else if (updateSupport) {
                    // 判断要更新的名称和当前数据库的名称是否一致
                    if (!topicCategory.getCategoryName().equals(topicCategoryExcel.getCategoryName())) {
                        // 不一致
                        if (currentId == 1L) {
                            // 是开发者不需要审核
                            topicCategory.setStatus(StatusEnums.NORMAL.getCode());
                        } else {
                            // 不是开发者是会员需要审核
                            topicCategory.setStatus(StatusEnums.AUDITING.getCode());
                            // 封装发送消息数据
                            TopicCategoryDto topicCategoryDto = new TopicCategoryDto();
                            topicCategoryDto.setCategoryName(topicCategory.getCategoryName());
                            topicCategoryDto.setId(topicCategory.getId());
                            // 封装审核消息
                            TopicAuditCategory topicAuditCategory = new TopicAuditCategory();
                            BeanUtils.copyProperties(topicCategoryDto, topicAuditCategory);
                            // 设置账户
                            topicAuditCategory.setAccount(SecurityUtils.getCurrentName());
                            // 设置id
                            topicAuditCategory.setUserId(currentId);
                            // 转换字符串
                            String jsonString = JSON.toJSONString(topicAuditCategory);
                            log.info("发送消息{}", jsonString);
                            // 异步调用发送消息给ai审核
                            rabbitService.sendMessage(RabbitConstant.CATEGORY_AUDIT_EXCHANGE, RabbitConstant.CATEGORY_AUDIT_ROUTING_KEY_NAME, jsonString);
                        }
                        topicCategory.setFailMsg("");
                    }
                    // 更新
                    BeanUtils.copyProperties(topicCategoryExcel, topicCategory);
                    topicCategoryMapper.updateById(topicCategory);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目分类：").append(topicCategory.getCategoryName()).append("-更新成功");
                } else {
                    // 已存在
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-题目分类：").append(topicCategory.getCategoryName()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目分类： " + topicCategoryExcel.getCategoryName() + " 导入失败：";
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
