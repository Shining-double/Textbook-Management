package org.jeecg.modules.demo.zbu.controller;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.demo.zbu.entity.*;
import org.jeecg.modules.demo.zbu.service.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import com.alibaba.fastjson.JSON;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.apache.shiro.authz.annotation.RequiresPermissions;
 /**
 * @Description: 总账单
 * @Author: jeecg-boot
 * @Date:   2026-01-28
 * @Version: V1.0
 */
@Tag(name="总账单")
@RestController
@RequestMapping("/zbu/studentAllBillSummary")
@Slf4j
public class StudentAllBillSummaryController extends JeecgController<StudentAllBillSummary, IStudentAllBillSummaryService> {
	@Autowired
	private IStudentAllBillSummaryService studentAllBillSummaryService;
	 @Autowired
	 private ITSubscriptionService tSubscriptionService;
	 @Autowired
	 private ITStudentService tStudentService;
	 @Autowired
	 private ITTextbookService tTextbookService;
	 @Autowired
	 private ITMajorService tMajorService;
	 @Autowired
	 private ITCollegeService tCollegeService;

	 @Transactional(rollbackFor = Exception.class)
	 public void autoSummarySubscriptionData() {
		 try {
			 // 1. 清空旧的汇总数据
			 studentAllBillSummaryService.remove(new QueryWrapper<>());
			 // 2. 查询所有有效征订记录
			 QueryWrapper<TSubscription> subWrapper = new QueryWrapper<>();
			 subWrapper.isNotNull("student_id").isNotNull("major_id")
					 .isNotNull("subscription_year").isNotNull("subscription_semester")
					 .isNotNull("textbook_id");
			 List<TSubscription> subList = tSubscriptionService.list(subWrapper);
			 if (subList.isEmpty()) {
				 log.warn("【自动汇总】未查询到有效征订记录，汇总结束");
				 return;
			 }
			 // 3. 按「学院ID（从专业表取）+专业ID+学年+学期」分组汇总（核心修复）
			 Map<String, List<TSubscription>> groupMap = subList.stream().collect(Collectors.groupingBy(sub -> {
				 // 从专业表获取学院ID（替代学生表）
				 String collegeId = "";
				 if (!oConvertUtils.isEmpty(sub.getMajorId())) {
					 TMajor major = tMajorService.getById(sub.getMajorId());
					 collegeId = major != null ? major.getCollegeId() : "";
				 }
				 return String.join("_",
						 oConvertUtils.isEmpty(collegeId) ? "未知学院" : collegeId,
						 oConvertUtils.isEmpty(sub.getMajorId()) ? "未知专业" : sub.getMajorId(),
						 oConvertUtils.isEmpty(sub.getSubscriptionYear()) ? "未知学年" : sub.getSubscriptionYear(),
						 oConvertUtils.isEmpty(sub.getSubscriptionSemester()) ? "未知学期" : sub.getSubscriptionSemester()
				 );
			 }));
			 // 4. 遍历分组计算汇总数据（后续逻辑不变）
			 List<StudentAllBillSummary> summaryList = new ArrayList<>();
			 for (Map.Entry<String, List<TSubscription>> entry : groupMap.entrySet()) {
				 String[] keyArr = entry.getKey().split("_");
				 String collegeId = keyArr.length > 0 ? keyArr[0] : "";
				 String majorId = keyArr.length > 1 ? keyArr[1] : "";
				 String year = keyArr.length > 2 ? keyArr[2] : "";
				 String semester = keyArr.length > 3 ? keyArr[3] : "";
				 List<TSubscription> groupSubList = entry.getValue();

				 // 基础信息
				 StudentAllBillSummary summary = new StudentAllBillSummary();
				 summary.setCollegeId(collegeId);
				 summary.setMajorId(majorId);
				 summary.setSubscriptionYear(year);
				 summary.setSubscriptionSemester(semester);
				 // 学院/专业名称（逻辑不变）
				 if (!"未知学院".equals(collegeId)) {
					 TCollege college = tCollegeService.getById(collegeId);
					 summary.setCollegeName(college != null ? college.getCollegeName() : "未知学院");
				 } else {
					 summary.setCollegeName("未知学院");
				 }
				 if (!"未知专业".equals(majorId)) {
					 TMajor major = tMajorService.getById(majorId);
					 summary.setMajorName(major != null ? major.getMajorName() : "未知专业");
				 } else {
					 summary.setMajorName("未知专业");
				 }
				 // 计算核心汇总字段（逻辑不变）
				 Set<String> studentIdSet = groupSubList.stream().map(TSubscription::getStudentId).collect(Collectors.toSet());
				 summary.setStudentCount(Long.valueOf(studentIdSet.size())); // 去重学生数
				 summary.setTextbookCount(Long.valueOf(groupSubList.size())); // 征订教材数
				 // 原价/折后总价
				 BigDecimal originalTotal = BigDecimal.ZERO;
				 BigDecimal discountTotal = BigDecimal.ZERO;
				 for (TSubscription sub : groupSubList) {
					 TTextbook textbook = tTextbookService.getById(sub.getTextbookId());
					 if (textbook == null) continue;
					 BigDecimal price = textbook.getPrice() != null ? textbook.getPrice() : BigDecimal.ZERO;
					 BigDecimal discount = textbook.getDiscount() != null ? textbook.getDiscount() : new BigDecimal("1");
					 originalTotal = originalTotal.add(price);
					 discountTotal = discountTotal.add(price.multiply(discount).setScale(2, BigDecimal.ROUND_HALF_UP));
				 }
				 summary.setOriginalTotal(originalTotal.setScale(2, BigDecimal.ROUND_HALF_UP));
				 summary.setDiscountTotal(discountTotal);
				 // 时间字段
				 summary.setCreateTime(new Date());
				 summary.setUpdateTime(new Date());
				 summaryList.add(summary);
			 }
			 // 5. 批量保存汇总数据
			 if (!summaryList.isEmpty()) {
				 studentAllBillSummaryService.saveBatch(summaryList);
				 log.info("【自动汇总】总账单生成完成，共{}条汇总记录", summaryList.size());
			 }
		 } catch (Exception e) {
			 log.error("【自动汇总】总账单生成失败", e);
			 throw new RuntimeException("总账单自动汇总失败：" + e.getMessage());
		 }
	 }

	 // ========== 定时任务：每天凌晨0点自动汇总（兜底保障） ==========
	 // cron表达式：0 0 0 * * ?  → 每天凌晨0点执行，可根据需求修改（如每小时：0 0 */1 * * ?）
	 @Scheduled(cron = "0 0 0 * * ?")
	 public void scheduleSummary() {
		 log.info("【定时汇总】开始执行总账单定时汇总任务");
		 autoSummarySubscriptionData();
	 }

	 // ========== 保留手动接口（可选，用于测试/手动兜底） ==========
	 @AutoLog(value = "总账单-手动汇总征订数据")
	 @Operation(summary="总账单-手动汇总征订数据")
	 @GetMapping(value = "/summarySubscriptionData")
	 public Result<String> manualSummary() {
		 autoSummarySubscriptionData();
		 return Result.OK("手动汇总成功！");
	 }
	
	/**
	 * 分页列表查询
	 *
	 * @param studentAllBillSummary
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "总账单-分页列表查询")
	@Operation(summary="总账单-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<StudentAllBillSummary>> queryPageList(StudentAllBillSummary studentAllBillSummary,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


        QueryWrapper<StudentAllBillSummary> queryWrapper = QueryGenerator.initQueryWrapper(studentAllBillSummary, req.getParameterMap());
		Page<StudentAllBillSummary> page = new Page<StudentAllBillSummary>(pageNo, pageSize);
		IPage<StudentAllBillSummary> pageList = studentAllBillSummaryService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param studentAllBillSummary
	 * @return
	 */
	@AutoLog(value = "总账单-添加")
	@Operation(summary="总账单-添加")
	@RequiresPermissions("zbu:student_all_bill_summary:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody StudentAllBillSummary studentAllBillSummary) {
		studentAllBillSummaryService.save(studentAllBillSummary);

		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param studentAllBillSummary
	 * @return
	 */
	@AutoLog(value = "总账单-编辑")
	@Operation(summary="总账单-编辑")
	@RequiresPermissions("zbu:student_all_bill_summary:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody StudentAllBillSummary studentAllBillSummary) {
		studentAllBillSummaryService.updateById(studentAllBillSummary);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "总账单-通过id删除")
	@Operation(summary="总账单-通过id删除")
	@RequiresPermissions("zbu:student_all_bill_summary:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		studentAllBillSummaryService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "总账单-批量删除")
	@Operation(summary="总账单-批量删除")
	@RequiresPermissions("zbu:student_all_bill_summary:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.studentAllBillSummaryService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "总账单-通过id查询")
	@Operation(summary="总账单-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<StudentAllBillSummary> queryById(@RequestParam(name="id",required=true) String id) {
		StudentAllBillSummary studentAllBillSummary = studentAllBillSummaryService.getById(id);
		if(studentAllBillSummary==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(studentAllBillSummary);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param studentAllBillSummary
    */
    @RequiresPermissions("zbu:student_all_bill_summary:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, StudentAllBillSummary studentAllBillSummary) {
        return super.exportXls(request, studentAllBillSummary, StudentAllBillSummary.class, "总账单");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:student_all_bill_summary:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, StudentAllBillSummary.class);
    }

}
