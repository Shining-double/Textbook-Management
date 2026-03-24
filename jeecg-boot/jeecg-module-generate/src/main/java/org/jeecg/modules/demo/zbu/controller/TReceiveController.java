package org.jeecg.modules.demo.zbu.controller;

import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
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
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * @Description: 领取表
 * @Author: jeecg-boot
 * @Date:   2026-01-23
 * @Version: V1.0
 */
@Tag(name="领取表")
@RestController
@RequestMapping("/zbu/tReceive")
@Slf4j
public class TReceiveController extends JeecgController<TReceive, ITReceiveService> {
	@Autowired
	private ITReceiveService tReceiveService;
	 @Autowired
	 private ITStudentService tStudentService;
	 @Autowired
	 private IStudentBillService studentBillService;
	 @Autowired
	 private ITSubscriptionService tSubscriptionService;
	 @Autowired
	 private ITTextbookService tTextbookService;
	 @Autowired
	 private ISysUserService sysUserService;
	 @Autowired
	 private ISysRoleService sysRoleService;
	 @Autowired
	 private ISysUserRoleService sysUserRoleService;
     @Autowired
     private ITCounselorService tCounselorService;
     @Autowired
     private ITClassService tClassService;
	 @Autowired
	 private JdbcTemplate jdbcTemplate;
	 @Autowired
	 private ITMajorService tMajorService;
	 @Autowired
	 private ITCollegeService tCollegeService;

	 // 角色编码常量
	 private static final String ADMIN_ROLE_CODE = "admin";
	 private static final String COUNSELOR_ROLE_CODE = "counselor";
	 private static final String STUDENT_ROLE_CODE = "student";


	/**
	 * 分页列表查询
	 *
	 * @param tReceive
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "领取表-分页列表查询")
	@Operation(summary="领取表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TReceive>> queryPageList(TReceive tReceive,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


		// 1. 获取当前登录用户信息
		LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
		if (loginUser == null) {
			return Result.error("用户未登录，请先登录！");
		}
		String username = loginUser.getUsername();
		SysUser currentUser = sysUserService.getUserByName(username);
		if (currentUser == null) {
			return Result.error("未查询到当前登录用户信息！");
		}

		// 2. 判断当前用户角色
		String userRoleType = getUserRoleType(currentUser.getId());
		log.info("【领取表查询】当前用户：{}，角色类型：{}", username, userRoleType);

		// 3. 自定义查询规则（适配多选筛选）
		Map<String, QueryRuleEnum> customeRuleMap = new HashMap<>();
		customeRuleMap.put("receiveStatus", QueryRuleEnum.LIKE_WITH_OR);
		QueryWrapper<TReceive> queryWrapper = QueryGenerator.initQueryWrapper(tReceive, req.getParameterMap(), customeRuleMap);

		// 学院模糊查询
		String collegeName = req.getParameter("collegeName");
		if (oConvertUtils.isNotEmpty(collegeName)) {
			queryWrapper.inSql("receive_operator", "SELECT id FROM t_student WHERE major_id IN (SELECT id FROM t_major WHERE college_id IN (SELECT id FROM t_college WHERE college_name LIKE CONCAT('%', '" + collegeName + "', '%')))");
		}

		// 4. 按角色过滤查询条件
		switch (userRoleType) {
			case ADMIN_ROLE_CODE:
				// 管理员：查询所有，无需过滤
				break;
			case COUNSELOR_ROLE_CODE:
				// 辅导员：查询所有学生的领取记录（可扩展：按辅导员管辖班级/专业过滤）
				// 示例：如果辅导员关联了班级，可添加 queryWrapper.in("class_id", 管辖班级ID列表)
				log.info("【辅导员端】查询所有学生的领取记录");
				break;
			case STUDENT_ROLE_CODE:
				// 学生：仅查询自己的领取记录（receive_operator=学生表ID）
				TStudent student = tStudentService.lambdaQuery()
						.eq(TStudent::getUserId, currentUser.getId())
						.one();
				if (student != null) {
					queryWrapper.eq("receive_operator", student.getId());
				} else {
					// 学生未关联学生表，返回空数据
					Page<TReceive> emptyPage = new Page<>(pageNo, pageSize);
					emptyPage.setRecords(new ArrayList<>());
					emptyPage.setTotal(0);
					return Result.OK(emptyPage);
				}
				break;
			default:
				// 未知角色：返回空数据
				Page<TReceive> emptyPage = new Page<>(pageNo, pageSize);
				emptyPage.setRecords(new ArrayList<>());
				emptyPage.setTotal(0);
				return Result.OK(emptyPage);
		}

		// 5. 执行分页查询
		Page<TReceive> page = new Page<TReceive>(pageNo, pageSize);
		IPage<TReceive> pageList = tReceiveService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tReceive
	 * @return
	 */
	@AutoLog(value = "领取表-添加")
	@Operation(summary="领取表-添加")
	@RequiresPermissions("zbu:t_receive:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TReceive tReceive) {
		if ("1".equals(tReceive.getReceiveStatus())) {
			tReceive.setReceiveTime(new Date());
		}
		tReceiveService.save(tReceive);

		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tReceive
	 * @return
	 */
	@AutoLog(value = "领取表-编辑")
	@Operation(summary="领取表-编辑")
	@RequiresPermissions("zbu:t_receive:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TReceive tReceive) {
		if ("1".equals(tReceive.getReceiveStatus())) {
			tReceive.setReceiveTime(new Date());
		}

		tReceiveService.updateById(tReceive);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "领取表-通过id删除")
	@Operation(summary="领取表-通过id删除")
	@RequiresPermissions("zbu:t_receive:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tReceiveService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "领取表-批量删除")
	@Operation(summary="领取表-批量删除")
	@RequiresPermissions("zbu:t_receive:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tReceiveService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "领取表-通过id查询")
	@Operation(summary="领取表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TReceive> queryById(@RequestParam(name="id",required=true) String id) {
		TReceive tReceive = tReceiveService.getById(id);
		if(tReceive==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tReceive);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tReceive
    */
    @RequiresPermissions("zbu:t_receive:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TReceive tReceive) {
		// 1. 初始化导出视图（JeecgBoot原生，无任何报错）
		ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
		ExportParams exportParams = new ExportParams("领取表", "领取表数据");
		mv.addObject(NormalExcelConstants.PARAMS, exportParams);
		mv.addObject(NormalExcelConstants.CLASS, TReceive.class);

		List<TReceive> list = new ArrayList<>();
		try {
			// 1. 获取登录用户
			Subject subject = SecurityUtils.getSubject();
			if (subject == null || !subject.isAuthenticated()) {
				mv.addObject(NormalExcelConstants.DATA_LIST, list);
				return mv;
			}
			LoginUser loginUser = (LoginUser) subject.getPrincipal();
			if (loginUser == null) {
				mv.addObject(NormalExcelConstants.DATA_LIST, list);
				return mv;
			}

			// 2. 角色判断
			String roleCodeStr = loginUser.getRoleCode();
			boolean isAdmin = false;
			boolean isCounselor = false;
			if (roleCodeStr != null && !roleCodeStr.isEmpty()) {
				String[] roleCodes = roleCodeStr.split(",");
				for (String code : roleCodes) {
					code = code.trim();
					if ("admin".equals(code)) {
						isAdmin = true;
						break;
					}
					if ("counselor".equals(code)) {
						isCounselor = true;
					}
				}
			}
			// 管理员兜底判断
			if (!isAdmin && "admin".equals(loginUser.getUsername())) {
				isAdmin = true;
			}

			// 3. 按角色查询 领取表视图 v_receive_with_details
			if (isAdmin) {
				// 管理员：导出全部视图数据
				list = jdbcTemplate.query(
						"SELECT * FROM v_receive_with_details ORDER BY createTime DESC",
						new BeanPropertyRowMapper<>(TReceive.class)
				);
			} else if (isCounselor) {
				// 辅导员：仅导出自己管理的班级
				TCounselor counselor = tCounselorService.lambdaQuery()
						.eq(TCounselor::getUserId, loginUser.getId()).one();
				if (counselor != null) {
					List<TClass> classList = tClassService.lambdaQuery()
							.eq(TClass::getCounselorId, counselor.getId()).list();
					if (!classList.isEmpty()) {
						List<String> classIds = classList.stream().map(TClass::getId).toList();
						List<TStudent> studentList = tStudentService.lambdaQuery()
								.in(TStudent::getClassId, classIds).list();
						if (!studentList.isEmpty()) {
							List<String> studentIds = studentList.stream().map(TStudent::getId).toList();
							String ids = String.join("','", studentIds);
							// 查询视图
							String sql = "SELECT * FROM v_receive_with_details WHERE receiveOperator IN ('" + ids + "') ORDER BY createTime DESC";
							list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(TReceive.class));
						}
					}
				}
			} else {
				// 学生：仅导出自己的领取记录
				TStudent student = tStudentService.lambdaQuery()
						.eq(TStudent::getStudentId, loginUser.getUsername()).one();
				if (student != null) {
					list = jdbcTemplate.query(
							"SELECT * FROM v_receive_with_details WHERE receiveOperator = ? ORDER BY createTime DESC",
							new BeanPropertyRowMapper<>(TReceive.class),
							student.getId()
					);
				}
			}
		} catch (Exception e) {
			log.error("领取表导出失败", e);
		}

		// 视图自带学院信息，无需手动赋值，直接导出
		mv.addObject(NormalExcelConstants.DATA_LIST, list);
		return mv;
	}

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:t_receive:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TReceive.class);
    }

	 /**
	  * 通过学号查询学生信息
	  *
	  * @param studentNo 学生学号
	  * @return
	  */
//@AutoLog(value = "领取表-通过学号查询学生信息")
	 @Operation(summary="领取表-通过学号查询学生信息")
	 @GetMapping(value = "/getStudentByNo")
	 public Result<TStudent> getStudentByNo(@RequestParam(name="studentNo",required=true) String studentNo) {
		 // 构造查询条件：根据学号查询学生表
		 QueryWrapper<TStudent> queryWrapper = new QueryWrapper<>();
		 queryWrapper.eq("student_id", studentNo);
		 TStudent student = tStudentService.getOne(queryWrapper);

		 if(student == null) {
			 return Result.error("未找到学号为【" + studentNo + "】的学生信息");
		 }
		 return Result.OK(student);
	 }

	 /**
	  * 原有按ID查学生的接口
	  *
	  * @param id 学生表ID
	  * @return
	  */
//@AutoLog(value = "领取表-通过ID查询学生信息")
	 @Operation(summary="领取表-通过ID查询学生信息")
	 @GetMapping(value = "/getStudentById")
	 public Result<TStudent> getStudentById(@RequestParam(name="id",required=true) String id) {
		 TStudent student = tStudentService.getById(id);
		 if(student == null) {
			 return Result.error("未找到ID为【" + id + "】的学生信息");
		 }
		 return Result.OK(student);
	 }


	 /**
	  * 批量修改领取状态（学生端仅改自己的，管理员改所有）
	  */
	 @Transactional(rollbackFor = Exception.class)
	 @AutoLog(value = "领取表-批量修改领取状态")
	 @PostMapping("/batchUpdateReceiveStatus")
	 public Result<String> batchUpdateReceiveStatus(@RequestBody Map<String, Object> params) {
		 try {
			 // 1. 获取当前登录用户及角色
			 LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
			 if (loginUser == null) {
				 return Result.error("用户未登录，请先登录！");
			 }
			 String username = loginUser.getUsername();
			 SysUser currentUser = sysUserService.getUserByName(username);
			 String userRoleType = getUserRoleType(currentUser.getId());

			 // 2. 获取参数
			 List<String> ids = (List<String>) params.get("ids");
			 String receiveStatus = (String) params.get("receiveStatus");
			 String receiveOperator = (String) params.get("receiveOperator");

			 // 3. 参数校验
			 if (ids == null || ids.isEmpty()) {
				 return Result.error("暂无需要修改的领取记录！");
			 }
			 if (oConvertUtils.isEmpty(receiveStatus)) {
				 return Result.error("领取状态不能为空！");
			 }
			 // 非管理员/辅导员必须传receiveOperator（学生仅能修改自己）
			 if (!ADMIN_ROLE_CODE.equals(userRoleType) && !COUNSELOR_ROLE_CODE.equals(userRoleType)) {
				 if (oConvertUtils.isEmpty(receiveOperator)) {
					 return Result.error("缺少学生标识，无权限修改他人记录！");
				 }
			 }

			 // 4. 构建更新条件（按角色过滤）
			 QueryWrapper<TReceive> updateWrapper = new QueryWrapper<>();
			 updateWrapper.in("id", ids);
			 // 学生仅能修改自己的记录
			 if (STUDENT_ROLE_CODE.equals(userRoleType)) {
				 TStudent student = tStudentService.lambdaQuery()
						 .eq(TStudent::getUserId, currentUser.getId())
						 .one();
				 if (student != null) {
					 updateWrapper.eq("receive_operator", student.getId());
				 } else {
					 return Result.error("未查询到当前学生信息，无法修改！");
				 }
			 }

			 // 5. 构造更新对象
			 TReceive updateReceive = new TReceive();
			 updateReceive.setReceiveStatus(receiveStatus);
			 if ("1".equals(receiveStatus)) {
				 updateReceive.setReceiveTime(new Date());
			 }

			 // 6. 执行更新
			 boolean receiveUpdateSuccess = tReceiveService.update(updateReceive, updateWrapper);
			 if (!receiveUpdateSuccess) {
				 return Result.error("领取表状态修改失败：无匹配的领取记录！");
			 }

			 // 7. 同步更新个人账单（核心逻辑不变，适配角色）
			 if (receiveUpdateSuccess) {
				 Map<String, String> statusMap = new HashMap<>();
				 statusMap.put("1", "已领取");
				 statusMap.put("0", "未领取");
				 String billReceiveStatus = statusMap.getOrDefault(receiveStatus, receiveStatus);

				 QueryWrapper<TReceive> receiveQuery = new QueryWrapper<>();
				 receiveQuery.in("id", ids);
				 List<TReceive> receiveList = tReceiveService.list(receiveQuery);
				 if (receiveList.isEmpty()) {
					 log.warn("【同步账单失败】未查询到领取记录（ids={}）", ids);
					 return Result.OK("领取表状态修改成功，但未同步到账单（无匹配领取记录）！");
				 }

				 int billUpdateCount = 0;
				 for (TReceive receive : receiveList) {
					 String subscriptionId = receive.getSubscriptionId();
					 if (oConvertUtils.isEmpty(subscriptionId)) {
						 log.warn("【同步账单失败】领取记录ID={} 的subscriptionId为空", receive.getId());
						 continue;
					 }

					 TSubscription subscription = tSubscriptionService.getById(subscriptionId);
					 if (subscription == null) {
						 log.warn("【同步账单失败】征订ID={} 不存在", subscriptionId);
						 continue;
					 }

					 String studentNo = subscription.getStudentId();
					 TStudent student = tStudentService.getById(studentNo);
					 if (student != null) {
						 studentNo = student.getStudentId();
					 }

					 String textbookName = "未知教材";
					 TTextbook textbook = tTextbookService.getById(subscription.getTextbookId());
					 if (textbook != null) {
						 textbookName = textbook.getTextbookName();
					 }

					 QueryWrapper<StudentBill> billWrapper = new QueryWrapper<>();
					 billWrapper.eq("student_id", studentNo)
							 .eq("subscription_year", subscription.getSubscriptionYear())
							 .eq("subscription_semester", subscription.getSubscriptionSemester())
							 .eq("textbook_name", textbookName);

					 StudentBill billUpdate = new StudentBill();
					 billUpdate.setReceiveStatus(billReceiveStatus);
					 billUpdate.setUpdateTime(new Date());

					 boolean singleBillSuccess = studentBillService.update(billUpdate, billWrapper);
					 if (singleBillSuccess) {
						 billUpdateCount++;
						 log.info("【同步账单成功】领取记录ID={} → 账单（学号={}，教材={}）状态改为{}",
								 receive.getId(), studentNo, textbookName, billReceiveStatus);
					 }
				 }
				 log.info("同步更新个人账单结果：成功更新{}条，领取状态改为{}", billUpdateCount, billReceiveStatus);
			 }

			 return Result.OK("领取表状态修改成功（已同步个人账单）！");
		 } catch (Exception e) {
			 log.error("批量修改领取状态失败", e);
			 return Result.error("修改失败：" + e.getMessage());
		 }
	 }

	 private String getUserRoleType(String userId) {
		 // 1. 查询用户关联的角色
		 QueryWrapper<SysUserRole> userRoleWrapper = new QueryWrapper<>();
		 userRoleWrapper.eq("user_id", userId);
		 List<SysUserRole> userRoleList = sysUserRoleService.list(userRoleWrapper);
		 if (userRoleList.isEmpty()) {
			 return "";
		 }

		 // 2. 提取角色编码
		 List<String> roleIds = userRoleList.stream()
				 .map(SysUserRole::getRoleId)
				 .collect(Collectors.toList());
		 List<SysRole> roleList = sysRoleService.listByIds(roleIds);

		 // 3. 判断角色优先级：管理员 > 辅导员 > 学生
		 for (SysRole role : roleList) {
			 if (ADMIN_ROLE_CODE.equals(role.getRoleCode())) {
				 return ADMIN_ROLE_CODE;
			 }
			 if (COUNSELOR_ROLE_CODE.equals(role.getRoleCode())) {
				 return COUNSELOR_ROLE_CODE;
			 }
			 if (STUDENT_ROLE_CODE.equals(role.getRoleCode())) {
				 return STUDENT_ROLE_CODE;
			 }
		 }
		 return "";
	 }

	 @AutoLog(value = "领取表-获取我的领取记录")
	 @Operation(summary = "获取当前登录用户的领取记录", description = "管理员查所有、辅导员查管理班级、学生查自己")
	 @GetMapping(value = "/getMyReceive")
	 public Result<List<Map<String, Object>>> getMyReceive() {
		 try {
			 // 1. 获取当前登录用户（和征订表完全一致）
			 Subject subject = SecurityUtils.getSubject();
			 if (subject == null || !subject.isAuthenticated()) {
				 log.warn("用户未登录，无法获取领取记录");
				 return Result.error("用户未登录，无法获取领取记录");
			 }
			 LoginUser loginUser = (LoginUser) subject.getPrincipal();
			 if (loginUser == null) {
				 log.warn("未获取到当前登录用户信息");
				 return Result.error("未获取到当前登录用户信息");
			 }

			 log.info("当前登录用户: {}，角色码: {}", loginUser.getUsername(), loginUser.getRoleCode());

			 // 2. 解析角色编码，判断用户类型（兼容多角色逗号分隔，和征订表一致）
			 String roleCodeStr = loginUser.getRoleCode();
			 boolean isAdmin = false;
			 boolean isCounselor = false;
			 if (roleCodeStr != null && !roleCodeStr.isEmpty()) {
				 String[] roleCodes = roleCodeStr.split(",");
				 for (String code : roleCodes) {
					 code = code.trim();
					 if ("admin".equals(code)) {
						 isAdmin = true;
						 break; // 管理员优先级最高
					 }
					 if ("counselor".equals(code)) {
						 isCounselor = true;
					 }
				 }
			 }
			 // 管理员用户名兜底判断（和征订表一致）
			 if (!isAdmin && "admin".equals(loginUser.getUsername())) {
				 isAdmin = true;
			 }

			 List<Map<String, Object>> receiveList = new ArrayList<>();
			 if (isAdmin) {
				 // 管理员：查询所有领取记录（使用视图）
				 receiveList = jdbcTemplate
						 .queryForList("SELECT * FROM v_receive_with_details ORDER BY receiveTime DESC");
				 log.info("管理员模式，查询到{}条领取记录", receiveList.size());
			 } else if (isCounselor) {
				 // ========== 辅导员逻辑（完全仿照征订表） ==========
				 // 步骤1：通过sys_user.id查询辅导员信息
				 QueryWrapper<TCounselor> counselorWrapper = new QueryWrapper<>();
				 counselorWrapper.eq("user_id", loginUser.getId()); // t_counselor的userId关联sys_user.id
				 TCounselor counselor = tCounselorService.getOne(counselorWrapper);
				 if (counselor == null) {
					 log.warn("当前登录用户未关联辅导员信息，用户ID: {}", loginUser.getId());
					 return Result.error("当前登录用户未关联辅导员信息");
				 }

				 // 步骤2：查询该辅导员管理的所有班级
				 QueryWrapper<TClass> classWrapper = new QueryWrapper<>();
				 classWrapper.eq("counselor_id", counselor.getId()); // t_class的counselorId关联t_counselor.id
				 List<TClass> classList = tClassService.list(classWrapper);
				 if (classList.isEmpty()) {
					 log.info("辅导员{}暂无管理的班级，无领取记录", counselor.getCounselorName());
					 return Result.OK("你暂无管理的班级，无领取记录", receiveList);
				 }
				 // 提取班级ID列表
				 List<String> classIds = classList.stream().map(TClass::getId).collect(Collectors.toList());

				 // 步骤3：查询这些班级下的所有学生
				 QueryWrapper<TStudent> studentWrapper = new QueryWrapper<>();
				 studentWrapper.in("class_id", classIds); // t_student的classId关联t_class.id
				 List<TStudent> studentList = tStudentService.list(studentWrapper);
				 if (studentList.isEmpty()) {
					 log.info("辅导员{}管理的班级暂无学生，无领取记录", counselor.getCounselorName());
					 return Result.OK("你管理的班级暂无学生，无领取记录", receiveList);
				 }
				 // 提取学生ID列表（t_receive的receive_operator关联t_student.id）
				 List<String> studentIds = studentList.stream().map(TStudent::getId).collect(Collectors.toList());

				 // 步骤4：查询这些学生的所有领取记录（使用视图）
				 String studentIdInClause = String.join(",",
						 studentIds.stream().map(id -> "'" + id + "'").collect(Collectors.toList()));
				 receiveList = jdbcTemplate
						 .queryForList("SELECT * FROM v_receive_with_details WHERE receiveOperator IN ("
								 + studentIdInClause + ") ORDER BY receiveTime DESC");
				 log.info("辅导员{}模式，查询到管理班级下{}条领取记录", counselor.getCounselorName(), receiveList.size());
			 } else {
				 // ========== 学生逻辑（仿照征订表，替换为领取表字段） ==========
				 String username = loginUser.getUsername();
				 if (username == null || username.isEmpty()) {
					 log.warn("当前登录用户无用户名（学号）信息");
					 return Result.error("当前登录用户无用户名（学号）信息");
				 }

				 // 通过学号查询学生信息
				 QueryWrapper<TStudent> studentWrapper = new QueryWrapper<>();
				 studentWrapper.eq("student_id", username);
				 TStudent student = tStudentService.getOne(studentWrapper);
				 if (student == null) {
					 log.warn("当前登录用户未关联学生信息，用户名: {}", username);
					 return Result.error("当前登录用户未关联学生信息，用户名: " + username);
				 }

				 // 查询该学生的领取记录（receive_operator=学生id）（使用视图）
				 receiveList = jdbcTemplate.queryForList(
						 "SELECT * FROM v_receive_with_details WHERE receiveOperator = ? ORDER BY receiveTime DESC",
						 student.getId());
				 log.info("学生模式，查询到{}条领取记录", receiveList.size());
			 }

			 return Result.OK("", receiveList);
		 } catch (Exception e) {
			 log.error("获取领取记录失败", e);
			 return Result.error("获取失败：" + e.getMessage());
		 }
	 }

 }
