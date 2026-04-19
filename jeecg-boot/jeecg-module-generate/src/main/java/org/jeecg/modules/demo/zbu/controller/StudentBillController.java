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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
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
	private static final String ADMIN_ROLE_CODE = "admin";
	private static final String COUNSELOR_ROLE_CODE = "counselor";

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
	@Autowired
	private ITCounselorService tCounselorService;
	@Autowired
	private ITClassService tClassService;
	@Autowired
	private ISysUserService sysUserService;
	@Autowired
	private ISysRoleService sysRoleService;
	@Autowired
	private ISysUserRoleService sysUserRoleService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	 * 账单汇总查询
	 *
	 * @param schoolYear 学年（可选）
	 * @param semester 学期（可选）
	 * @param pageNo 页码
	 * @param pageSize 每页数量
	 * @return 汇总列表
	 */
	@Operation(summary = "个人账单-汇总查询")
	@GetMapping(value = "/summary")
	public Result<?> querySummaryList(
			@RequestParam(name = "studentNo", required = false) String studentNo,
			@RequestParam(name = "studentName", required = false) String studentName,
			@RequestParam(name = "className", required = false) String className,
			@RequestParam(name = "majorName", required = false) String majorName,
			@RequestParam(name = "schoolYear", required = false) String schoolYear,
			@RequestParam(name = "semester", required = false) String semester,
			@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
			@RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {

		// 构建基础SQL（使用汇总视图）
		String baseSql = "SELECT * FROM v_student_bill_summary WHERE 1=1";
		String countSql = "SELECT COUNT(*) FROM v_student_bill_summary WHERE 1=1";

		// 辅导员角色过滤：只显示自己管理班级的学生
		LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
		if (loginUser != null) {
			String userRoleType = getUserRoleType(loginUser.getId());
			if (COUNSELOR_ROLE_CODE.equals(userRoleType)) {
				List<String> studentIds = getCounselorStudentIds(loginUser.getId());
				if (!studentIds.isEmpty()) {
					String studentIdInClause = studentIds.stream()
							.map(id -> "'" + id + "'")
							.collect(Collectors.joining(","));
					baseSql += " AND studentId IN (" + studentIdInClause + ")";
					countSql += " AND studentId IN (" + studentIdInClause + ")";
				} else {
					baseSql += " AND 1=0";
					countSql += " AND 1=0";
				}
			}
		}

		// 添加查询条件
		if (oConvertUtils.isNotEmpty(studentNo)) {
			baseSql += " AND studentNo LIKE '%" + studentNo + "%'";
			countSql += " AND studentNo LIKE '%" + studentNo + "%'";
		}
		if (oConvertUtils.isNotEmpty(studentName)) {
			baseSql += " AND studentName LIKE '%" + studentName + "%'";
			countSql += " AND studentName LIKE '%" + studentName + "%'";
		}
		if (oConvertUtils.isNotEmpty(className)) {
			baseSql += " AND className LIKE '%" + className + "%'";
			countSql += " AND className LIKE '%" + className + "%'";
		}
		if (oConvertUtils.isNotEmpty(majorName)) {
			baseSql += " AND majorName LIKE '%" + majorName + "%'";
			countSql += " AND majorName LIKE '%" + majorName + "%'";
		}
		if (oConvertUtils.isNotEmpty(schoolYear)) {
			baseSql += " AND schoolYear = '" + schoolYear + "'";
			countSql += " AND schoolYear = '" + schoolYear + "'";
		}
		if (oConvertUtils.isNotEmpty(semester)) {
			baseSql += " AND semester = '" + semester + "'";
			countSql += " AND semester = '" + semester + "'";
		}

		// 添加排序
		baseSql += " ORDER BY studentId, schoolYear DESC, semester";

		// 添加分页
		int offset = (pageNo - 1) * pageSize;
		baseSql += " LIMIT " + offset + ", " + pageSize;

		log.info("【账单汇总】执行SQL：{}", baseSql);

		// 执行查询
		List<Map<String, Object>> records = jdbcTemplate.queryForList(baseSql);

		// 转换字典值
		for (Map<String, Object> record : records) {
			// 转换学期
			String sem = (String) record.get("semester");
			if ("1".equals(sem)) {
				record.put("semester", "第一学期");
			} else if ("2".equals(sem)) {
				record.put("semester", "第二学期");
			}
		}

		// 获取总数
		int total = jdbcTemplate.queryForObject(countSql, Integer.class);

		// 构建分页结果
		Map<String, Object> result = new HashMap<>();
		result.put("records", records);
		result.put("total", total);
		result.put("size", pageSize);
		result.put("current", pageNo);
		result.put("pages", (total + pageSize - 1) / pageSize);

		log.info("【账单汇总】查询结果总数：{}", total);
		return Result.OK(result);
	}

	/**
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
	public Result<?> queryPageList(StudentBill studentBill,
								   @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
								   @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
								   HttpServletRequest req) {

		// 处理className参数（班级名称在t_student表中，需要用子查询）
		String className = req.getParameter("className");
		Map<String, String[]> paramMap = new HashMap<>(req.getParameterMap());
		if (className != null && !className.isEmpty()) {
			paramMap.remove("className");
		}

		QueryWrapper<StudentBill> queryWrapper = QueryGenerator.initQueryWrapper(studentBill, paramMap);

		// 如果有班级名称搜索条件，添加子查询
		if (className != null && !className.isEmpty()) {
			queryWrapper.inSql("student_id",
					"SELECT student_id FROM t_student WHERE class_id IN (SELECT id FROM t_class WHERE class_name LIKE '%" + className + "%')");
		}

		// 1. 获取当前登录用户
		LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
		String username = loginUser.getUsername();
		log.info("【账单列表】登录用户名：{}，是否管理员：{}", username, "admin".equals(username) || "sysadmin".equals(username));

		// 2. 判断是否是管理员
		boolean isAdmin = "admin".equals(username) || "sysadmin".equals(username);

		// 3. 非管理员（学生）：强制过滤
		if (!isAdmin) {
			queryWrapper.clear();
			queryWrapper.eq("student_id", username);
			queryWrapper.orderByDesc("create_time");
			log.info("【账单列表】学生端过滤条件：student_id = {}", username);
		}

		Page<StudentBill> page = new Page<StudentBill>(pageNo, pageSize);
		IPage<StudentBill> pageList = studentBillService.page(page, queryWrapper);

		// 4. 为每条记录查询ISBN和班级名称
		List<Map<String, Object>> recordsWithIsbn = new ArrayList<>();
		for (StudentBill bill : pageList.getRecords()) {
			Map<String, Object> recordMap = new HashMap<>();
			recordMap.put("id", bill.getId());
			recordMap.put("studentId", bill.getStudentId());
			recordMap.put("className", bill.getClassName());
			recordMap.put("majorName", bill.getMajorName());
			recordMap.put("subscriptionYear", bill.getSubscriptionYear());
			recordMap.put("subscriptionSemester", bill.getSubscriptionSemester());
			recordMap.put("textbookName", bill.getTextbookName());
			recordMap.put("price", bill.getPrice());
			recordMap.put("discountPrice", bill.getDiscountPrice());
			recordMap.put("subscribeStatus", bill.getSubscribeStatus());
			recordMap.put("receiveStatus", bill.getReceiveStatus());
			recordMap.put("remark", bill.getRemark());
			recordMap.put("createTime", bill.getCreateTime());
			recordMap.put("updateTime", bill.getUpdateTime());

			String textbookName = bill.getTextbookName();
			String isbn = "";
			if (textbookName != null && !textbookName.isEmpty()) {
				try {
					String isbnSql = "SELECT isbn FROM t_textbook WHERE textbook_name = ? LIMIT 1";
					isbn = jdbcTemplate.queryForObject(isbnSql, String.class, textbookName);
				} catch (Exception e) {
					log.warn("查询ISBN失败：{}", e.getMessage());
				}
			}
			recordMap.put("isbn", isbn != null ? isbn : "");

			// 查询班级名称
			String studentIdForClass = bill.getStudentId();
			if (studentIdForClass != null && !studentIdForClass.isEmpty()) {
				try {
					String classNameSql = "SELECT c.class_name FROM t_class c INNER JOIN t_student s ON s.class_id = c.id WHERE s.student_id = ? LIMIT 1";
					String classNameResult = jdbcTemplate.queryForObject(classNameSql, String.class, studentIdForClass);
					recordMap.put("className", classNameResult != null ? classNameResult : "");
				} catch (Exception e) {
					log.warn("查询班级名称失败：{}", e.getMessage());
					recordMap.put("className", "");
				}
			} else {
				recordMap.put("className", "");
			}

			recordsWithIsbn.add(recordMap);
		}

		// 5. 构建分页结果
		Map<String, Object> result = new HashMap<>();
		result.put("records", recordsWithIsbn);
		result.put("total", pageList.getTotal());
		result.put("size", pageList.getSize());
		result.put("current", pageList.getCurrent());
		result.put("pages", pageList.getPages());

		log.info("【账单列表】查询结果总数：{}", pageList.getTotal());
		return Result.OK(result);
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

	private String getUserRoleType(String userId) {
		QueryWrapper<SysUserRole> userRoleWrapper = new QueryWrapper<>();
		userRoleWrapper.eq("user_id", userId);
		List<SysUserRole> userRoleList = sysUserRoleService.list(userRoleWrapper);
		if (userRoleList.isEmpty()) {
			return "";
		}

		List<String> roleIds = userRoleList.stream()
				.map(SysUserRole::getRoleId)
				.collect(Collectors.toList());
		List<SysRole> roleList = sysRoleService.listByIds(roleIds);

		for (SysRole role : roleList) {
			if (ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
				return ADMIN_ROLE_CODE;
			}
			if (COUNSELOR_ROLE_CODE.equals(role.getRoleCode())) {
				return COUNSELOR_ROLE_CODE;
			}
		}
		return "";
	}

	private List<String> getCounselorStudentIds(String userId) {
		List<String> studentIds = new ArrayList<>();
		try {
			TCounselor counselor = tCounselorService.lambdaQuery()
					.eq(TCounselor::getUserId, userId).one();
			if (counselor == null) {
				return studentIds;
			}

			List<TClass> classList = tClassService.lambdaQuery()
					.eq(TClass::getCounselorId, counselor.getId()).list();
			if (classList.isEmpty()) {
				return studentIds;
			}

			List<String> classIds = classList.stream().map(TClass::getId).collect(Collectors.toList());
			List<TStudent> studentList = tStudentService.lambdaQuery()
					.in(TStudent::getClassId, classIds).list();
			if (studentList.isEmpty()) {
				return studentIds;
			}

			studentIds = studentList.stream()
					.map(TStudent::getStudentId)
					.filter(id -> !oConvertUtils.isEmpty(id))
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("获取辅导员学生ID列表失败", e);
		}
		return studentIds;
	}

}
