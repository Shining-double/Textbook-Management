package org.jeecg.modules.demo.zbu.controller;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.system.vo.LoginUser;
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
 * @Description: 个人账单
 * @Author: jeecg-boot
 * @Date: 2026-01-25
 * @Version: V1.0
 */
@Tag(name = "个人账单")
@RestController
@RequestMapping("/zbu/studentBill")
@Slf4j
public class StudentBillController extends JeecgController<StudentBill, IStudentBillService> {
	@Autowired
	private IStudentBillService studentBillService;
	@Autowired
	private ITSubscriptionService tSubscriptionService;
	@Autowired
	private ITStudentService tStudentService;
	@Autowired
	private ITTextbookService tTextbookService;
	@Autowired
	private ITMajorService tMajorService;
	@Autowired
	private ITReceiveService tReceiveService;

	/**
	 * 同步征订数据到个人账单表（最终完整版）
	 * 字段来源：
	 * - 学号/征订学年/学期：征订表（TSubscription）
	 * - 专业名称：专业表（TMajor）（通过征订表majorId关联，保持历史专业信息）
	 * - 教材名称/定价/折扣：教材表（TTextbook）（通过征订表textbookId关联）
	 * - 领取状态：领取表（TReceive）（通过征订表id关联领取表subscriptionId）
	 */
	@AutoLog(value = "个人账单-同步征订数据")
	@Operation(summary = "个人账单-同步征订数据")
	// @RequiresPermissions("zbu:student_bill:sync")
	@PostMapping(value = "/syncFromSubscription")
	public Result<String> syncFromSubscription() {
		try {
			// 1. 查询所有征订记录（仅含关联ID）
			List<TSubscription> subscriptionList = tSubscriptionService.list();

			// 2. 遍历征订记录，关联查询所有关联表
			List<StudentBill> billList = new ArrayList<>();
			for (TSubscription sub : subscriptionList) {
				// 核心判空：学号/专业ID/教材ID为空则跳过
				if (oConvertUtils.isEmpty(sub.getStudentId())
						|| oConvertUtils.isEmpty(sub.getMajorId())
						|| oConvertUtils.isEmpty(sub.getTextbookId())) {
					log.warn("征订记录ID:{} 学号/专业ID/教材ID为空，跳过", sub.getId());
					continue;
				}

				// ====================== 1. 关联专业表：获取专业名称 ======================
				String majorName = "未知专业";
				try {
					TMajor tMajor = tMajorService.getById(sub.getMajorId());
					if (tMajor != null && oConvertUtils.isNotEmpty(tMajor.getMajorName())) {
						majorName = tMajor.getMajorName();
					}
				} catch (Exception e) {
					log.warn("征订记录ID:{} 查询专业名称失败（majorId={}）", sub.getId(), sub.getMajorId(), e);
				}

				// ====================== 2. 关联教材表：获取教材名称/定价/折扣 ======================
				String textbookName = "未知教材";
				BigDecimal price = new BigDecimal("0.00");
				BigDecimal discount = new BigDecimal("1.00"); // 默认无折扣
				try {
					TTextbook tTextbook = tTextbookService.getById(sub.getTextbookId());
					if (tTextbook != null) {
						if (oConvertUtils.isNotEmpty(tTextbook.getTextbookName())) {
							textbookName = tTextbook.getTextbookName();
						}
						if (tTextbook.getPrice() != null) {
							price = tTextbook.getPrice();
						}
						if (tTextbook.getDiscount() != null) {
							discount = tTextbook.getDiscount();
						}
					}
				} catch (Exception e) {
					log.warn("征订记录ID:{} 查询教材信息失败（textbookId={}）", sub.getId(), sub.getTextbookId(), e);
				}

				// ====================== 3. 关联领取表：获取领取状态（核心新增） ======================
				// 领取表的subscriptionId = 征订表的id（sub.getId()）
				String receiveStatus = "未领取"; // 兜底值
				try {
					// 查询领取表中，关联当前征订记录的领取状态
					QueryWrapper<TReceive> receiveQuery = new QueryWrapper<>();
					receiveQuery.eq("subscription_id", sub.getId()); // 征订表id关联领取表subscriptionId
					TReceive tReceive = tReceiveService.getOne(receiveQuery, false); // 不抛异常
					if (tReceive != null && oConvertUtils.isNotEmpty(tReceive.getReceiveStatus())) {
						receiveStatus = tReceive.getReceiveStatus(); // 从领取表取领取状态
					}
				} catch (Exception e) {
					log.warn("征订记录ID:{} 查询领取状态失败", sub.getId(), e);
				}

				// ====================== 4. 计算折后价 ======================
				BigDecimal discountPrice = price.multiply(discount).setScale(2, java.math.RoundingMode.HALF_UP);

				// ====================== 5. 组装账单实体 ======================
				StudentBill bill = new StudentBill();

				String studentNo = "未知学号";
				try {
					// 征订表的student_id是学生表主键，通过它查学生表的学号
					TStudent tStudent = tStudentService.getById(sub.getStudentId());
					if (tStudent != null && StringUtils.isNotBlank(tStudent.getStudentId())) {
						log.info("征订记录ID:{} 征订表student_id:{} 学生表主键ID:{} 学生表业务学号:{}",
								sub.getId(), sub.getStudentId(), tStudent.getId(), tStudent.getStudentId());
						if (StringUtils.isNotBlank(tStudent.getStudentId())) {
							studentNo = tStudent.getStudentId();
						}
					}
				} catch (Exception e) {
					log.warn("征订记录ID:{} 查询学号失败", sub.getId(), e);
				}
				// 组装账单实体时，设置student_id为学号
				bill.setStudentId(studentNo);

				bill.setMajorName(majorName); // 专业名称（专业表，保持历史专业信息）
				bill.setSubscriptionYear(sub.getSubscriptionYear()); // 征订学年（征订表）
				bill.setSubscriptionSemester(sub.getSubscriptionSemester()); // 征订学期（征订表）
				bill.setTextbookName(textbookName); // 教材名称（教材表）
				bill.setPrice(price); // 教材定价（教材表）
				bill.setDiscountPrice(discountPrice); // 折后费用（教材表定价*教材表折扣）
				bill.setSubscribeStatus(sub.getSubscribeStatus() != null ? sub.getSubscribeStatus() : "已征订"); // 征订状态（征订表）
				bill.setReceiveStatus(receiveStatus); // 领取状态（领取表，核心修正）
				bill.setRemark(sub.getRemark()); // 备注（征订表）

				billList.add(bill);
			}

			// 6. 批量同步数据
			if (!billList.isEmpty()) {
				// 优化：不再清空旧账单，而是根据征订记录的唯一性进行更新或新增
				// 这样可以保留历史账单记录，即使学生转专业
				for (StudentBill bill : billList) {
					// 根据学号、学年、学期、教材名称来判断是否已存在相同的账单记录
					QueryWrapper<StudentBill> queryWrapper = new QueryWrapper<>();
					queryWrapper.eq("student_id", bill.getStudentId());
					queryWrapper.eq("subscription_year", bill.getSubscriptionYear());
					queryWrapper.eq("subscription_semester", bill.getSubscriptionSemester());
					queryWrapper.eq("textbook_name", bill.getTextbookName());

					StudentBill existingBill = studentBillService.getOne(queryWrapper, false);
					if (existingBill != null) {
						// 更新现有记录
						bill.setId(existingBill.getId());
						studentBillService.updateById(bill);
					} else {
						// 新增记录
						studentBillService.save(bill);
					}
				}
				log.info("同步成功！共处理{}条记录（领取状态来自领取表）", billList.size());
				return Result.OK("同步征订数据到个人账单成功！共处理" + billList.size() + "条记录");
			} else {
				log.info("无新的征订记录需要同步");
				return Result.OK("无新的征订记录需要同步");
			}
		} catch (Exception e) {
			log.error("同步失败", e);
			return Result.error("同步失败：" + e.getMessage());
		}
	}

	/**
	 * 分页列表查询
	 *
	 * @param studentBill
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	// @AutoLog(value = "个人账单-分页列表查询")
	@Operation(summary = "个人账单-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<StudentBill>> queryPageList(StudentBill studentBill,
													@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
													@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
													HttpServletRequest req) {

		QueryWrapper<StudentBill> queryWrapper = QueryGenerator.initQueryWrapper(studentBill, req.getParameterMap());

		// 1. 获取当前登录用户
		LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
		String username = loginUser.getUsername();
		// 新增日志：确认登录用户名和是否为管理员
		log.info("【账单列表】登录用户名：{}，是否管理员：{}", username, "admin".equals(username) || "sysadmin".equals(username));

		// 2. 判断是否是管理员
		boolean isAdmin = "admin".equals(username) || "sysadmin".equals(username);

		// 3. 非管理员（学生）：强制过滤
		if (!isAdmin) {
			queryWrapper.clear();
			queryWrapper.eq("student_id", username);
			queryWrapper.orderByDesc("create_time");
			// 新增日志：确认过滤条件
			log.info("【账单列表】学生端过滤条件：student_id = {}", username);
		}

		Page<StudentBill> page = new Page<StudentBill>(pageNo, pageSize);
		IPage<StudentBill> pageList = studentBillService.page(page, queryWrapper);
		// 新增日志：确认查询结果
		log.info("【账单列表】查询结果总数：{}", pageList.getTotal());
		return Result.OK(pageList);
	}

	/**
	 * 添加
	 *
	 * @param studentBill
	 * @return
	 */
	@AutoLog(value = "个人账单-添加")
	@Operation(summary = "个人账单-添加")
	@RequiresPermissions("zbu:student_bill:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody StudentBill studentBill) {
		studentBillService.save(studentBill);

		return Result.OK("添加成功！");
	}

	/**
	 * 编辑
	 *
	 * @param studentBill
	 * @return
	 */
	@AutoLog(value = "个人账单-编辑")
	@Operation(summary = "个人账单-编辑")
	@RequiresPermissions("zbu:student_bill:edit")
	@RequestMapping(value = "/edit", method = { RequestMethod.PUT, RequestMethod.POST })
	public Result<String> edit(@RequestBody StudentBill studentBill) {
		studentBillService.updateById(studentBill);
		return Result.OK("编辑成功!");
	}

	/**
	 * 通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "个人账单-通过id删除")
	@Operation(summary = "个人账单-通过id删除")
	@RequiresPermissions("zbu:student_bill:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
		studentBillService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 * 批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "个人账单-批量删除")
	@Operation(summary = "个人账单-批量删除")
	@RequiresPermissions("zbu:student_bill:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
		this.studentBillService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	// @AutoLog(value = "个人账单-通过id查询")
	@Operation(summary = "个人账单-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<StudentBill> queryById(@RequestParam(name = "id", required = true) String id) {
		StudentBill studentBill = studentBillService.getById(id);
		if (studentBill == null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(studentBill);
	}

	/**
	 * 导出excel
	 *
	 * @param request
	 * @param studentBill
	 */
	@RequiresPermissions("zbu:student_bill:exportXls")
	@RequestMapping(value = "/exportXls")
	public ModelAndView exportXls(HttpServletRequest request, StudentBill studentBill) {
		return super.exportXls(request, studentBill, StudentBill.class, "个人账单");
	}

	/**
	 * 通过excel导入数据
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	@RequiresPermissions("zbu:student_bill:importExcel")
	@RequestMapping(value = "/importExcel", method = RequestMethod.POST)
	public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
		return super.importExcel(request, response, StudentBill.class);
	}

	/**
	 * 批量修改领取状态（仅Controller实现，无需改Service）
	 * 学生端：仅能修改自己的账单；管理员端：可修改所有账单
	 */
	@AutoLog(value = "个人账单-批量修改领取状态")
	@Operation(summary = "个人账单-批量修改领取状态")
	@PostMapping(value = "/batchUpdateReceiveStatus")
	public Result<String> batchUpdateReceiveStatus(@RequestBody Map<String, Object> params) {
		try {
			// 1. 接收前端参数
			List<String> ids = (List<String>) params.get("ids");
			String targetReceiveStatus = (String) params.get("receiveStatus");

			// 2. 基础参数校验
			if (ids == null || ids.isEmpty()) {
				return Result.error("错误：暂无需要修改的账单数据！");
			}
			if (StringUtils.isEmpty(targetReceiveStatus) ||
					(!("已领取".equals(targetReceiveStatus)) && !("未领取".equals(targetReceiveStatus)))) {
				return Result.error("错误：领取状态值非法（仅支持“已领取”/“未领取”）！");
			}

			// 3. 权限控制：学生只能改自己的，管理员可改所有
			LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
			String username = loginUser.getUsername();
			boolean isAdmin = "admin".equals(username) || "sysadmin".equals(username);

			// 4. 构建更新条件
			QueryWrapper<StudentBill> updateWrapper = new QueryWrapper<>();
			updateWrapper.in("id", ids); // 批量更新指定ID的账单
			if (!isAdmin) {
				// 学生端：强制筛选自己的学号（student_id = 登录用户名）
				updateWrapper.eq("student_id", username);
			}

			// 5. 执行批量更新（直接用父类的update方法，无需Service新增方法）
			StudentBill updateBill = new StudentBill();
			updateBill.setReceiveStatus(targetReceiveStatus);
			boolean updateSuccess = studentBillService.update(updateBill, updateWrapper);

			// 6. 返回结果
			if (updateSuccess) {
				return Result.OK("领取状态批量修改成功！");
			} else {
				return Result.error("领取状态修改失败：无数据被更新（可能无权限修改他人账单）！");
			}
		} catch (Exception e) {
			log.error("批量修改领取状态异常", e);
			return Result.error("领取状态修改失败：" + e.getMessage());
		}
	}

}
