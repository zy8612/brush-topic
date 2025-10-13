package com.ey.topic.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.system.SysNoticeDto;
import com.ey.model.dto.system.SysNoticeReadDto;
import com.ey.model.entity.system.SysNotice;
import com.ey.model.vo.system.SysNoticeVo;

import java.util.List;

public interface SysNoticeService extends IService<SysNotice> {
    List<SysNoticeVo> listNotice();

    void read(SysNoticeReadDto sysNoticeReadDto);

    Boolean has();

    void clearNotice();

    void recordNotice(SysNoticeDto sysNoticeDto);
}
