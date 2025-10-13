package com.ey.topic.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.system.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    void insertBatch(List<SysRoleMenu> relations);
}
