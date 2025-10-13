package com.ey.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.dto.system.SysUserListDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.entity.system.SysUser;
import com.ey.model.vo.system.SysUserListVo;
import com.ey.model.vo.topic.TopicDataVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Description: 用户数据层
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    List<SysUserListVo> selectUserList(SysUserListDto sysUserListDto);

    long countUserList(SysUserListDto sysUserListDto);

    List<SysUserListVo> selectExportUsers(List<Long> ids);

    SysRole getByRoleName(String roleName);

    List<TopicDataVo> countUserDay7();
}
