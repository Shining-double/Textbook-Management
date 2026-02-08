package org.jeecg.modules.demo.zbu.controller;

import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.demo.zbu.entity.*;
import org.jeecg.modules.demo.zbu.mapper.TStudentMapper;
import org.jeecg.modules.demo.zbu.service.*;

import org.jeecg.common.system.vo.LoginUser;
import org.apache.shiro.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.modules.system.entity.SysUser;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @Description: 征订表
 * @Author: jeecg-boot
 * @Date:   2026-01-19
 * @Version: V1.0
 */
@Tag(name="征订表")
@RestController
@RequestMapping("/zbu/tSubscription")
@Slf4j
public class TSubscriptionController extends JeecgController<TSubscription, ITSubscriptionService> {
	@Autowired
	private ITSubscriptionService tSubscriptionService;
	 @Autowired
	 private ITStudentService tStudentService;
	 @Autowired
	 private ITReceiveService tReceiveService;
	 @Autowired
	 private IStudentBillService studentBillService;
	 @Autowired
	 private ITTextbookService tTextbookService;
	 @Autowired
	 private ITClassService tClassService;
	 @Autowired
	 private ITCounselorService tCounselorService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tSubscription
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "征订表-分页列表查询")
	@Operation(summary="征订表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TSubscription>> queryPageList(TSubscription tSubscription,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


        // 自定义查询规则
        Map<String, QueryRuleEnum> customeRuleMap = new HashMap<>();
        // 自定义多选的查询规则为：LIKE_WITH_OR
        customeRuleMap.put("subscriptionSemester", QueryRuleEnum.LIKE_WITH_OR);
        customeRuleMap.put("subscribeStatus", QueryRuleEnum.LIKE_WITH_OR);
        QueryWrapper<TSubscription> queryWrapper = QueryGenerator.initQueryWrapper(tSubscription, req.getParameterMap(),customeRuleMap);
		Page<TSubscription> page = new Page<TSubscription>(pageNo, pageSize);
		IPage<TSubscription> pageList = tSubscriptionService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tSubscription
	 * @return
	 */
	@AutoLog(value = "征订表-添加")
	@Operation(summary="征订表-添加")
	@RequiresPermissions("zbu:t_subscription:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TSubscription tSubscription) {
		// 1. 保存征订表数据
		tSubscriptionService.save(tSubscription);

		// 2. 自动创建对应的领取表记录
		TReceive tReceive = new TReceive();
		// 设置关联的征订表ID（核心关联字段）
		tReceive.setSubscriptionId(tSubscription.getId());
		tReceive.setReceiveOperator(tSubscription.getStudentId());
		// 设置默认领取状态（根据你的字典配置，这里假设0=未领取，可根据实际调整）
		tReceive.setReceiveStatus("0");

		// 设置创建/更新时间
		tReceive.setCreateTime(new Date());
		tReceive.setUpdateTime(new Date());

		// 3. 保存领取表数据
		tReceiveService.save(tReceive);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tSubscription
	 * @return
	 */
	@AutoLog(value = "征订表-编辑")
	@Operation(summary="征订表-编辑")
	@RequiresPermissions("zbu:t_subscription:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TSubscription tSubscription) {
		tSubscriptionService.updateById(tSubscription);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "征订表-通过id删除")
	@Operation(summary="征订表-通过id删除")
	@RequiresPermissions("zbu:t_subscription:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tSubscriptionService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "征订表-批量删除")
	@Operation(summary="征订表-批量删除")
	@RequiresPermissions("zbu:t_subscription:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tSubscriptionService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "征订表-通过id查询")
	@Operation(summary="征订表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TSubscription> queryById(@RequestParam(name="id",required=true) String id) {
		TSubscription tSubscription = tSubscriptionService.getById(id);
		if(tSubscription==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tSubscription);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tSubscription
    */
    @RequiresPermissions("zbu:t_subscription:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TSubscription tSubscription) {
        return super.exportXls(request, tSubscription, TSubscription.class, "征订表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:t_subscription:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TSubscription.class);
    }


	 /**
	  * 获取当前登录学生的征订记录（核心接口）
	  */
	 @AutoLog(value = "征订表-获取我的征订记录")
	 @Operation(summary="获取当前登录学生的征订记录", description="仅返回当前登录学生本人的征订记录")
	 @GetMapping(value = "/getMySubscription")
	 public Result<List<TSubscription>> getMySubscription() {
		 try {
			 // 1. 获取当前登录用户（LoginUser）
			 Subject subject = SecurityUtils.getSubject();
			 if (subject == null || !subject.isAuthenticated()) {
				 log.warn("用户未登录，无法获取征订记录");
				 return Result.error("用户未登录，无法获取征订记录");
			 }
			 LoginUser loginUser = (LoginUser) subject.getPrincipal();
			 if (loginUser == null) {
				 log.warn("未获取到当前登录用户信息");
				 return Result.error("未获取到当前登录用户信息");
			 }

			 log.info("当前登录用户: {}，角色码: {}", loginUser.getUsername(), loginUser.getRoleCode());

			 // 2. 解析角色编码，判断用户类型（admin > 辅导员 > 学生）
			 String roleCodeStr = loginUser.getRoleCode();
			 boolean isAdmin = false;
			 boolean isCounselor = false;
			 // 角色判断（兼容多角色逗号分隔的情况）
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
			 // 管理员用户名兜底判断
			 if (!isAdmin && "admin".equals(loginUser.getUsername())) {
				 isAdmin = true;
			 }

			 List<TSubscription> subList = new ArrayList<>();
			 if (isAdmin) {
				 // 管理员：查询所有征订记录
				 QueryWrapper<TSubscription> adminWrapper = new QueryWrapper<>();
				 adminWrapper.orderByDesc("create_time");
				 subList = tSubscriptionService.list(adminWrapper);
				 log.info("管理员模式，查询到{}条征订记录", subList.size());
			 } else if (isCounselor) {
				 // ========== 新增：辅导员逻辑 ==========
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
					 log.info("辅导员{}暂无管理的班级，无征订记录", counselor.getCounselorName());
					 return Result.OK("你暂无管理的班级，无征订记录", subList);
				 }
				 // 提取班级ID列表
				 List<String> classIds = classList.stream().map(TClass::getId).collect(Collectors.toList());

				 // 步骤3：查询这些班级下的所有学生
				 QueryWrapper<TStudent> studentWrapper = new QueryWrapper<>();
				 studentWrapper.in("class_id", classIds); // t_student的classId关联t_class.id
				 List<TStudent> studentList = tStudentService.list(studentWrapper);
				 if (studentList.isEmpty()) {
					 log.info("辅导员{}管理的班级暂无学生，无征订记录", counselor.getCounselorName());
					 return Result.OK("你管理的班级暂无学生，无征订记录", subList);
				 }
				 // 提取学生ID列表（t_subscription的studentId关联t_student.id）
				 List<String> studentIds = studentList.stream().map(TStudent::getId).collect(Collectors.toList());

				 // 步骤4：查询这些学生的所有征订记录
				 QueryWrapper<TSubscription> counselorSubWrapper = new QueryWrapper<>();
				 counselorSubWrapper.in("student_id", studentIds)
						 .orderByDesc("create_time");
				 subList = tSubscriptionService.list(counselorSubWrapper);
				 log.info("辅导员{}模式，查询到管理班级下{}条征订记录", counselor.getCounselorName(), subList.size());
			 } else {
				 // 学生逻辑（保持原有不变）
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

				 // 查询该学生的征订记录
				 QueryWrapper<TSubscription> subWrapper = new QueryWrapper<>();
				 subWrapper.eq("student_id", student.getId())
						 .orderByDesc("subscribe_time");
				 subList = tSubscriptionService.list(subWrapper);
				 log.info("学生模式，查询到{}条征订记录", subList.size());
			 }

			 return Result.OK("", subList);
		 } catch (Exception e) {
			 log.error("获取征订记录失败", e);
			 return Result.error("获取失败：" + e.getMessage());
		 }
	 }

	 /**
	  * 核心新增：批量修改征订状态（适配前端“是否已全部征订”按钮）
	  * @param params 请求参数（ids: 征订记录ID数组, subscribeStatus: 征订状态, studentId: 学生ID）
	  * @return Result<String>
	  */
	 @Transactional(rollbackFor = Exception.class)
	 @AutoLog(value = "征订表-批量修改征订状态")
	 @Operation(summary="批量修改征订状态", description="学生只能修改自己的征订记录，管理员可修改所有")
	 @PostMapping(value = "/batchUpdateSubscribeStatus")
	 public Result<String> batchUpdateSubscribeStatus(@RequestBody Map<String, Object> params) {
		 try {
			 // 1. 解析参数
			 List<String> ids = (List<String>) params.get("ids");
			 String subscribeStatus = (String) params.get("subscribeStatus"); // 征订表数字状态：0/1
			 String studentId = (String) params.get("studentId"); // 学生表主键ID

			 // 2. 参数校验
			 if (ids == null || ids.isEmpty()) {
				 log.warn("批量修改征订状态失败：未传入需要修改的记录ID");
				 return Result.error("未传入需要修改的记录ID");
			 }
			 if (oConvertUtils.isEmpty(subscribeStatus)) {
				 log.warn("批量修改征订状态失败：未传入征订状态");
				 return Result.error("未传入征订状态");
			 }
			 if (oConvertUtils.isEmpty(studentId)) {
				 log.warn("批量修改征订状态失败：未传入学生ID");
				 return Result.error("未传入学生ID");
			 }

			 // 3. 获取当前登录用户，校验权限
			 Subject subject = SecurityUtils.getSubject();
			 LoginUser loginUser = (LoginUser) subject.getPrincipal();
			 boolean isAdmin = false;
			 String roleCodeStr = loginUser.getRoleCode();
			 if (roleCodeStr != null && !roleCodeStr.isEmpty()) {
				 String[] roleCodes = roleCodeStr.split(",");
				 for (String code : roleCodes) {
					 if ("admin".equals(code.trim())) {
						 isAdmin = true;
						 break;
					 }
				 }
			 }
			 // 管理员用户名兜底判断
			 if (!isAdmin && "admin".equals(loginUser.getUsername())) {
				 isAdmin = true;
			 }

			 // 4. 非管理员：校验只能修改自己的记录
			 if (!isAdmin) {
				 QueryWrapper<TSubscription> wrapper = new QueryWrapper<>();
				 wrapper.in("id", ids)
						 .ne("student_id", studentId);
				 List<TSubscription> noAuthRecords = tSubscriptionService.list(wrapper);
				 if (!noAuthRecords.isEmpty()) {
					 log.warn("学生{}尝试修改他人征订记录，非法ID: {}", studentId, noAuthRecords.stream().map(TSubscription::getId).collect(Collectors.joining(",")));
					 return Result.error("你只能修改自己的征订记录，无法操作他人记录！");
				 }
			 }

			 // 5. 批量更新征订表状态
			 List<TSubscription> updateList = new ArrayList<>();
			 for (String id : ids) {
				 TSubscription subscription = new TSubscription();
				 subscription.setId(id);
				 subscription.setSubscribeStatus(subscribeStatus);
				 subscription.setUpdateTime(new Date()); // 更新修改时间
				 updateList.add(subscription);
			 }
			 boolean subUpdateSuccess = tSubscriptionService.updateBatchById(updateList);
			 if (!subUpdateSuccess) {
				 log.warn("征订表状态修改失败：无匹配的征订记录（ids={}）", ids);
				 return Result.error("征订表状态修改失败：无匹配的记录！");
			 }

			 // ========== 修复核心：同步更新个人账单表的征订状态（对齐领取表逻辑） ==========
			 // 6.1 征订状态值映射（数字→文本，和账单表保持一致）
			 Map<String, String> statusMap = new HashMap<>();
			 statusMap.put("1", "已征订");
			 statusMap.put("0", "未征订");
			 String billSubscribeStatus = statusMap.getOrDefault(subscribeStatus, subscribeStatus);

			 // 6.2 通过征订表ID查询征订记录（获取关联信息）
			 QueryWrapper<TSubscription> subQuery = new QueryWrapper<>();
			 subQuery.in("id", ids);
			 List<TSubscription> subList = tSubscriptionService.list(subQuery);
			 if (subList.isEmpty()) {
				 log.warn("【同步账单失败】未查询到征订记录（ids={}）", ids);
				 return Result.OK("征订表状态修改成功，但未同步到账单（无匹配征订记录）！");
			 }

			 // 6.3 遍历每条征订记录，精准更新对应账单（核心：逐条匹配，避免批量错误）
			 int billUpdateCount = 0;
			 for (TSubscription subscription : subList) {
				 // 6.3.1 获取学生业务学号（关键：账单表存储的是学号，不是学生表主键）
				 String studentTableId = subscription.getStudentId(); // 征订表的studentId是学生表主键
				 TStudent student = tStudentService.getById(studentTableId);
				 if (student == null) {
					 log.warn("【同步账单失败】征订记录ID={} 的学生ID={} 不存在", subscription.getId(), studentTableId);
					 continue;
				 }
				 String studentNo = student.getStudentId(); // 学生业务学号（账单表的student_id）

				 // 6.3.2 获取教材名称（精准匹配账单的关键维度）
				 String textbookName = "未知教材";
				 if (subscription.getTextbookId() != null) {
					 TTextbook textbook = tTextbookService.getById(subscription.getTextbookId());
					 if (textbook != null) {
						 textbookName = textbook.getTextbookName();
					 }
				 }

				 // 6.3.3 精准构造账单更新条件（学号+学年+学期+教材名称，4维度匹配）
				 QueryWrapper<StudentBill> billWrapper = new QueryWrapper<>();
				 billWrapper.eq("student_id", studentNo) // 匹配账单的业务学号
						 .eq("subscription_year", subscription.getSubscriptionYear())
						 .eq("subscription_semester", subscription.getSubscriptionSemester())
						 .eq("textbook_name", textbookName); // 新增教材名称维度

				 // 6.3.4 构造账单更新对象
				 StudentBill billUpdate = new StudentBill();
				 billUpdate.setSubscribeStatus(billSubscribeStatus); // 同步映射后的征订状态
				 billUpdate.setUpdateTime(new Date()); // 更新账单修改时间

				 // 6.3.5 执行单条账单更新，并统计成功数
				 boolean singleBillSuccess = studentBillService.update(billUpdate, billWrapper);
				 if (singleBillSuccess) {
					 billUpdateCount++;
					 log.info("【同步账单成功】征订记录ID={} → 账单（学号={}，教材={}）征订状态改为{}",
							 subscription.getId(), studentNo, textbookName, billSubscribeStatus);
				 } else {
					 log.warn("【同步账单失败】征订记录ID={} → 账单（学号={}，教材={}）无匹配记录或状态未变更",
							 subscription.getId(), studentNo, textbookName);
				 }
			 }

			 // 6.4 同步结果日志
			 log.info("同步更新个人账单结果：{}，共尝试更新{}条，成功{}条，征订状态改为{}",
					 billUpdateCount > 0 ? "成功" : "失败", subList.size(), billUpdateCount, billSubscribeStatus);

			 // 7. 返回最终结果
			 String msg = String.format("成功修改%d条征订记录状态（同步更新%d条账单记录）！", ids.size(), billUpdateCount);
			 return Result.OK(msg);

		 } catch (Exception e) {
			 log.error("批量修改征订状态失败", e);
			 // 区分权限错误和通用错误
			 if (e.getMessage() != null && e.getMessage().contains("无权限")) {
				 return Result.error("你只能修改自己的征订记录，无法操作他人记录！");
			 } else {
				 return Result.error("征订状态修改失败：" + e.getMessage());
			 }
		 }
	 }





}
