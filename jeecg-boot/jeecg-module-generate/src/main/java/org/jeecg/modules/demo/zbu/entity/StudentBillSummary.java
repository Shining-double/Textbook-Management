package org.jeecg.modules.demo.zbu.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * @Description: 账单汇总导出实体
 * @Author: jeecg-boot
 * @Date: 2026-04-25
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "账单汇总导出实体")
public class StudentBillSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    /**学号*/
    @Excel(name = "学号", width = 15)
    @Schema(description = "学号")
    private java.lang.String studentNo;

    /**学生姓名*/
    @Excel(name = "学生姓名", width = 15)
    @Schema(description = "学生姓名")
    private java.lang.String studentName;

    /**班级*/
    @Excel(name = "班级", width = 15)
    @Schema(description = "班级")
    private java.lang.String className;

    /**专业*/
    @Excel(name = "专业", width = 15)
    @Schema(description = "专业")
    private java.lang.String majorName;

    /**学院*/
    @Excel(name = "学院", width = 15)
    @Schema(description = "学院")
    private java.lang.String collegeName;

    /**学年*/
    @Excel(name = "学年", width = 15)
    @Schema(description = "学年")
    private java.lang.String schoolYear;

    /**学期*/
    @Excel(name = "学期", width = 15)
    @Schema(description = "学期")
    private java.lang.String semester;

    /**折后价格汇总*/
    @Excel(name = "折后价格", width = 15)
    @Schema(description = "折后价格")
    private java.math.BigDecimal totalDiscountPrice;
}
