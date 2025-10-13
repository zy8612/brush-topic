package com.ey.model.excel.sytem;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import com.alibaba.excel.enums.poi.VerticalAlignmentEnum;
import lombok.Data;

import java.time.LocalDateTime;

// 数据行高度30
@ContentRowHeight(30)
// 表头行高20
@HeadRowHeight(20)
// 列宽25
@ColumnWidth(25)
// 对齐方式居中
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER, verticalAlignment = VerticalAlignmentEnum.CENTER)
@Data
public class SysUserExcel {


    @ExcelProperty(value = "账户")
    private String account;

    @ExcelProperty(value = "邮箱")
    private String email;

    @ExcelProperty(value = "密码")
    private String password;

    @ExcelProperty(value = "角色")
    private String roleName;

    @ExcelProperty(value = "注册时间")
    private LocalDateTime createTime;

    @ExcelProperty(value = "会员时间")
    private LocalDateTime memberTime;
}
