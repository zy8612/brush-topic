package com.ey.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.system.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("select exists(select 1 from sys_user_role where role_id = #{roleId})")
    Boolean selectExist(Long roleId);
}
