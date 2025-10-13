package com.ey.model.excel.topic;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import com.alibaba.excel.enums.poi.VerticalAlignmentEnum;
import lombok.Data;

@ContentRowHeight(30)
// 表头行高20
@HeadRowHeight(20)
// 列宽25
@ColumnWidth(25)
// 对齐方式居中
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER, verticalAlignment = VerticalAlignmentEnum.CENTER)
@Data
public class TopicMemberExcel {
    @ExcelProperty(value = "题目名称")
    private String topicName;
    @ColumnWidth(40)
    @ExcelProperty(value = "题目答案")
    private String answer;
    @ExcelProperty(value = "题目排序")
    private Integer sorted;
    @ColumnWidth(30)
    @ExcelProperty(value = "题目专题")
    private String subjectName;
    @ColumnWidth(30)
    @ExcelProperty(value = "题目标签格式(label1:label2:label3等等")
    private String labelName;
}
