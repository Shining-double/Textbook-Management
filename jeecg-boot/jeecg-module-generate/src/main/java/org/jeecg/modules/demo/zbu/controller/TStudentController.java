package org.jeecg.modules.demo.zbu.controller;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.demo.zbu.entity.*;
import org.jeecg.modules.demo.zbu.service.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.common.util.Md5Util;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.entity.SysUserRole;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.jeecg.modules.system.service.ISysRoleService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
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
 * @Description: 学生表
 * @Author: jeecg-boot
 * @Date:   2026-01-19
 * @Version: V1.0
 */
@Tag(name="学生表")
@RestController
@RequestMapping("/zbu/tStudent")
@Slf4j
public class TStudentController extends JeecgController<TStudent, ITStudentService> {
	@Autowired
	private ITStudentService tStudentService;
	 @Autowired
	 private ISysUserService sysUserService;
	 @Autowired
	 private ISysRoleService sysRoleService;
	 @Autowired
	 private ISysUserRoleService sysUserRoleService;
	 @Autowired
	 private BaseCommonService baseCommonService;
	 // 在TStudentController的@Autowired区域新增以下注入
	 @Autowired
	 private ITTextbookSelectionService tTextbookSelectionService;
	 @Autowired
	 private ITSubscriptionService tSubscriptionService;
	 @Autowired
	 private ITReceiveService tReceiveService;
	 @Autowired
	 private IStudentBillService studentBillService;
	 @Autowired
	 private ITTextbookService tTextbookService;
	 @Autowired
	 private ITMajorService tMajorService;

	 // 学生角色编码
	 private static final String STUDENT_ROLE_CODE = "student";

	/**
	 * 分页列表查询
	 *
	 * @param tStudent
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "学生表-分页列表查询")
	@Operation(summary="学生表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TStudent>> queryPageList(TStudent tStudent,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


        // 自定义查询规则
        Map<String, QueryRuleEnum> customeRuleMap = new HashMap<>();
        // 自定义多选的查询规则为：LIKE_WITH_OR
        customeRuleMap.put("status", QueryRuleEnum.LIKE_WITH_OR);
        QueryWrapper<TStudent> queryWrapper = QueryGenerator.initQueryWrapper(tStudent, req.getParameterMap(),customeRuleMap);

		// 学院模糊查询
		String collegeName = req.getParameter("collegeName");
		if (oConvertUtils.isNotEmpty(collegeName)) {
			queryWrapper.inSql("major_id", "SELECT id FROM t_major WHERE college_id IN (SELECT id FROM t_college WHERE college_name LIKE CONCAT('%', '" + collegeName + "', '%'))");
		}

		Page<TStudent> page = new Page<TStudent>(pageNo, pageSize);

		IPage<TStudent> pageList = tStudentService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tStudent
	 * @return
	 */
	@AutoLog(value = "学生表-添加")
	@Operation(summary="学生表-添加")
	@RequiresPermissions("zbu:t_student:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TStudent tStudent) {
		try {
			String studentNo = tStudent.getStudentId();
			if (oConvertUtils.isEmpty(studentNo)) {
				return Result.error("学生学号不能为空！");
			}

			// 1. 先创建系统用户（sys_user）的登录账号
			SysUser sysUser = new SysUser();
			sysUser.setUsername(studentNo.trim());
			// 姓名去空格
			sysUser.setRealname(tStudent.getStudentName() != null ? tStudent.getStudentName().trim() : "");

			// 2. Jeecg官方逻辑：生成8位随机salt + 原生加密（核心修正）
			String plainPwd = "123456"; // 明文密码写死123456
			String salt = oConvertUtils.randomGen(8); // 生成8位随机salt（和admin/jeecg账号逻辑一致）
			sysUser.setSalt(salt); // 设置随机salt到用户表
			// 用Jeecg原生加密：encrypt(用户名, 明文密码, 随机salt)，和登录验证逻辑完全匹配
			String encryptedPwd = PasswordUtil.encrypt(sysUser.getUsername(), plainPwd, salt);
			sysUser.setPassword(encryptedPwd); // 设置加密后的密码

			// 3. 补充系统默认字段（修复重复设置status问题）
			sysUser.setDepartIds("1"); // 赋值为你系统中存在的部门ID（如根部门ID，多个用,分隔）
			sysUser.setStatus(1); // 账号启用（仅设置一次）
			sysUser.setDelFlag(CommonConstant.DEL_FLAG_0); // 0=未删除
			sysUser.setCreateTime(new Date()); // 创建时间
			sysUser.setLastPwdUpdateTime(new Date()); // 最后修改密码时间
			sysUser.setOrgCode(null); // 对齐系统逻辑，org_code设为null

			// 4. 保存sys_user并校验
			boolean saveSysUserSuccess = sysUserService.save(sysUser);
			if (!saveSysUserSuccess) {
				return Result.error("创建登录账号失败！");
			}
			System.out.println("sys_user保存结果：" + saveSysUserSuccess);

			// 5. 通过角色编码（student）查询角色ID
			QueryWrapper<SysRole> roleWrapper = new QueryWrapper<>();
			roleWrapper.eq("role_code", STUDENT_ROLE_CODE); // 按角色编码查询
			SysRole studentRole = sysRoleService.getOne(roleWrapper); // 获取唯一角色
			if (studentRole == null) {
				return Result.error("未找到编码为【student】的学生角色，请先在角色管理中创建！");
			}

			// 6. 绑定用户-角色关系（核心：给新增用户分配学生角色）
			SysUserRole userRole = new SysUserRole();
			userRole.setUserId(sysUser.getId()); // 新创建的用户ID
			userRole.setRoleId(studentRole.getId()); // 学生角色ID
			sysUserRoleService.save(userRole);

			// 7. 把sys_user的ID关联到学生表并保存
			tStudent.setUserId(sysUser.getId());
			tStudentService.save(tStudent);

			generateStudentSubscription(tStudent);

			return Result.OK("添加成功",encryptedPwd);
		} catch (Exception e) {
			log.error("新增学生失败：", e);
			return Result.error("添加失败：" + e.getMessage());
		}
	}
	/**
	 *  编辑
	 *
	 * @param tStudent
	 * @return
	 */
	@AutoLog(value = "学生表-编辑")
	@Operation(summary="学生表-编辑")
	@RequiresPermissions("zbu:t_student:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TStudent tStudent) {
		tStudentService.updateById(tStudent);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "学生表-通过id删除")
	@Operation(summary="学生表-通过id删除")
	@RequiresPermissions("zbu:t_student:delete")
	@DeleteMapping(value = "/delete")
	@Transactional(rollbackFor = Exception.class)
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		try {
			log.info("开始删除学生：id={}", id);
			// 前置步骤：查询学生获取关联的userId（学生删除特有，其余步骤完全复刻）
			TStudent student = tStudentService.getById(id);
			if (student == null) {
				log.warn("删除失败：学生记录不存在，id={}", id);
				return Result.error("学生记录不存在！");
			}
			log.info("查询到学生信息：{}", JSON.toJSONString(student));

			// ========== 以下完全复刻你提供的单个用户删除逻辑（仅把id换成student.getUserId()） ==========
			if (student.getUserId() != null && !student.getUserId().isEmpty()) {
				String userId = student.getUserId(); // 学生关联的用户ID，替代单个删除的id

				// 步骤1：先删关联数据（和你提供的代码完全一致）
				int delRole = sysUserRoleService.getBaseMapper().delete(
						new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
				);
				// 注意：如果你的项目没有sysUserDepartService，直接注释这两行！！！
				// int delDepart = sysUserDepartService.getBaseMapper().delete(
				//         new LambdaQueryWrapper<SysUserDepart>().eq(SysUserDepart::getUserId, userId)
				// );
				int delDepart = 0; // 无sysUserDepartService时，赋值0避免报错
				log.info("删除关联数据：用户-角色({}条)、用户-部门({}条)", delRole, delDepart);

				// 步骤2：核心修复！用LambdaUpdateWrapper更新del_flag（和你提供的代码完全一致）
				SysUserMapper sysUserMapper = (SysUserMapper) sysUserService.getBaseMapper();
				int updateDelFlag = sysUserMapper.update(
						new SysUser(),
						new LambdaUpdateWrapper<SysUser>()
								.set(SysUser::getDelFlag, "1")
								.eq(SysUser::getId, userId)
				);
				log.info("原生SQL更新del_flag：id={}，影响行数={}", userId, updateDelFlag);

				if (updateDelFlag <= 0) {
					log.error("强制更新del_flag失败：ID={}（可能ID不存在/字段映射错误）", userId);
					// 直接跳过del_flag，执行终极删除（和你提供的代码完全一致）
					log.warn("跳过del_flag标记，直接物理删除");
					int delUser = sysUserMapper.delete(
							new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId)
					);
					if (delUser <= 0) {
						return Result.error("删除失败：用户ID不存在或字段映射错误！");
					}
					baseCommonService.addLog("直接物理删除用户：id=" + userId, CommonConstant.LOG_TYPE_2, 3);
					// 不直接return，继续删除学生表（学生删除特有）
				} else {
					// 步骤3：调用deleteLogicDeleted删除（和你提供的代码完全一致）
					List<String> userIds = Collections.singletonList(userId);
					int delUser = sysUserMapper.deleteLogicDeleted(userIds);
					if (delUser <= 0) {
						// 兜底：再次直接删除（和你提供的代码完全一致）
						delUser = sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId));
					}
					log.info("彻底删除用户成功：id={}，影响行数={}", userId, delUser);
					baseCommonService.addLog("彻底删除用户：id=" + userId, CommonConstant.LOG_TYPE_2, 3);
				}
			}

			// 学生删除特有步骤：物理删除学生表（保持原有校验逻辑）
			int delStudent = tStudentService.getBaseMapper().deleteById(id);
			log.info("物理删除学生表记录：id={}，影响行数={}", id, delStudent);
			if (delStudent <= 0) {
				throw new RuntimeException("学生表记录物理删除失败（无记录被影响）");
			}

			return Result.OK("删除成功! 数据库中已无该学生及关联用户记录");
		} catch (Exception e) {
			log.error("删除学生失败：id={}", id, e);
			return Result.error("删除失败：" + e.getMessage());
		}
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "学生表-批量删除")
	@Operation(summary="学生表-批量删除")
	@RequiresPermissions("zbu:t_student:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	@Transactional(rollbackFor = Exception.class)
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {

		try {
			log.info("开始批量删除学生：ids={}", ids);
			// 1. 拆分批量删除的学生ID（处理空格、空字符串）
			List<String> studentIds = Arrays.stream(ids.split(","))
					.map(String::trim)
					.filter(id -> !id.isEmpty())
					.collect(Collectors.toList());
			if (studentIds.isEmpty()) {
				return Result.error("批量删除的ID不能为空！");
			}
			log.info("处理后待删除的学生ID：{}", studentIds);

			// 2. 批量查询这些学生的userId
			List<TStudent> students = tStudentService.listByIds(studentIds);
			log.info("查询到待删除的学生数量：{}", students.size());
			if (students.isEmpty()) {
				return Result.error("未找到对应学生记录！");
			}

			// 3. 提取所有关联的userId（过滤空值）
			List<String> userIds = students.stream()
					.filter(student -> student.getUserId() != null && !student.getUserId().isEmpty())
					.map(TStudent::getUserId)
					.collect(Collectors.toList());
			log.info("提取到学生关联的用户ID数量：{}", userIds.size());

			// 4. 批量处理关联用户（完全复刻单个用户删除的核心逻辑，适配批量）
			if (!userIds.isEmpty()) {
				// 步骤1：批量删除用户关联数据（角色/部门，和单个删除逻辑一致）
				int delRole = sysUserRoleService.getBaseMapper().delete(
						new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds)
				);
				// 注意：无sysUserDepartService则注释以下两行，赋值delDepart=0
				// int delDepart = sysUserDepartService.getBaseMapper().delete(
				//         new LambdaQueryWrapper<SysUserDepart>().in(SysUserDepart::getUserId, userIds)
				// );
				int delDepart = 0; // 无用户部门表时赋值0，避免报错
				log.info("批量删除用户关联数据：角色({}条)、部门({}条)", delRole, delDepart);

				// 步骤2：批量更新用户del_flag=1（和单个删除的LambdaUpdateWrapper逻辑一致）
				SysUserMapper sysUserMapper = (SysUserMapper) sysUserService.getBaseMapper();
				int updateDelFlag = sysUserMapper.update(
						new SysUser(),
						new LambdaUpdateWrapper<SysUser>()
								.set(SysUser::getDelFlag, "1")
								.in(SysUser::getId, userIds) // 批量匹配ID
				);
				log.info("批量更新用户del_flag=1：待更新数量={}, 实际更新行数={}", userIds.size(), updateDelFlag);

				if (updateDelFlag <= 0) {
					log.error("批量更新用户del_flag失败：无用户被标记为逻辑删除");
					// 兜底：直接批量物理删除用户（和单个删除的兜底逻辑一致）
					log.warn("跳过del_flag标记，直接批量物理删除用户");
					int delUsers = sysUserMapper.delete(
							new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds)
					);
					if (delUsers <= 0) {
						log.warn("批量兜底删除用户失败：无用户记录被删除");
					} else {
						log.info("批量兜底物理删除用户成功：影响行数={}", delUsers);
						baseCommonService.addLog("直接批量物理删除用户：ids=" + userIds, CommonConstant.LOG_TYPE_2, 3);
					}
				} else {
					// 步骤3：调用deleteLogicDeleted批量删除用户（和单个删除逻辑一致）
					int delUser = sysUserMapper.deleteLogicDeleted(userIds);
					log.info("调用deleteLogicDeleted批量删除用户：待删数量={}, 实际删除行数={}", userIds.size(), delUser);

					if (delUser <= 0) {
						// 兜底：再次批量物理删除用户
						log.warn("deleteLogicDeleted批量删除失败，执行兜底删除");
						delUser = sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds));
						log.info("批量兜底删除用户成功：影响行数={}", delUser);
					}
					baseCommonService.addLog("彻底批量删除用户：ids=" + userIds, CommonConstant.LOG_TYPE_2, 3);
				}
			}

			// 5. 批量物理删除学生表（彻底删库记录，替换原逻辑删除）
			int delStudents = tStudentService.getBaseMapper().deleteBatchIds(studentIds);
			log.info("批量物理删除学生表记录：待删数量={}, 实际删除行数={}", studentIds.size(), delStudents);

			if (delStudents <= 0) {
				throw new RuntimeException("学生表批量物理删除失败（无记录被影响）");
			}

			return Result.OK("批量删除成功! 数据库中已无该批学生及关联用户记录");
		} catch (Exception e) {
			log.error("批量删除学生失败：ids={}", ids, e);
			return Result.error("批量删除失败：" + e.getMessage());
		}
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "学生表-通过id查询")
	@Operation(summary="学生表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TStudent> queryById(@RequestParam(name="id",required=true) String id) {
		TStudent tStudent = tStudentService.getById(id);
		if(tStudent==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tStudent);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tStudent
    */
    @RequiresPermissions("zbu:t_student:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TStudent tStudent) {
        return super.exportXls(request, tStudent, TStudent.class, "学生表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:t_student:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
		try {
			// 1. 获取上传的Excel文件
			MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
			Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
			if (fileMap.isEmpty()) {
				return Result.error("请选择要导入的Excel文件！");
			}

			// 2. 基础导入参数配置（不依赖实体类注解校验）
			ImportParams importParams = new ImportParams();
			importParams.setTitleRows(2); // 标题行数量（根据你的Excel模板调整，比如1行标题）
			importParams.setHeadRows(1); // 表头行数量（比如1行表头）
			importParams.setNeedSave(true);

			// 存储有效数据和失败信息
			List<TStudent> validStudentList = new ArrayList<>(); // 仅存合法数据
			List<String> failMsgList = new ArrayList<>();
			int totalRow = 0; // 记录Excel总行数（跳过标题）

			// 3. 遍历解析上传的Excel文件
			for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
				MultipartFile file = entry.getValue();
				if (file.isEmpty()) {
					continue;
				}

				// 4. 解析Excel为学生列表
				List<TStudent> tempList = ExcelImportUtil.importExcel(
						file.getInputStream(),
						TStudent.class,
						importParams
				);

				// 5. 逐行校验+过滤（核心：解决空值问题）
				for (int i = 0; i < tempList.size(); i++) {
					totalRow = i + 2; // Excel行号（标题行1行+表头1行，数据从第2行开始）
					TStudent student = tempList.get(i);

					// ========== 强制空值校验（实体类不改的核心解决方案） ==========
					// 5.1 学号空值校验
					String studentId = student.getStudentId();
					if (oConvertUtils.isEmpty(studentId) || studentId.trim().isEmpty()) {
						failMsgList.add("第" + totalRow + "行：学号为空，跳过导入");
						continue;
					}
					student.setStudentId(studentId.trim()); // 去除学号空格

					// 5.2 学生姓名空值校验（解决核心异常的关键）
					String studentName = student.getStudentName();
					if (oConvertUtils.isEmpty(studentName) || studentName.trim().isEmpty()) {
						failMsgList.add("第" + totalRow + "行：学生姓名为空（学号：" + studentId + "），跳过导入");
						continue;
					}
					student.setStudentName(studentName.trim()); // 去除姓名空格

					// 6. 校验学号是否已存在（避免重复创建账号）
					SysUser existUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
							.eq(SysUser::getUsername, student.getStudentId())
							.eq(SysUser::getDelFlag, CommonConstant.DEL_FLAG_0));
					if (existUser != null) {
						failMsgList.add("第" + totalRow + "行：学号【" + studentId + "】已存在系统账号，关联已有账号");
						student.setUserId(existUser.getId());
						validStudentList.add(student);
						continue;
					}

					// 7. 预查询学生角色（只查一次，提升性能）
					SysRole studentRole = sysRoleService.getOne(new LambdaQueryWrapper<SysRole>()
							.eq(SysRole::getRoleCode, STUDENT_ROLE_CODE));
					if (studentRole == null) {
						return Result.error("系统未配置【student】角色，请先在角色管理中创建！");
					}

					// 8. 创建系统用户（复刻新增接口逻辑，适配String类型ID）
					SysUser sysUser = new SysUser();
					sysUser.setUsername(student.getStudentId()); // 学号作为登录名
					sysUser.setRealname(student.getStudentName()); // 姓名作为真实名
					// 密码加密（和新增接口一致）
					String salt = oConvertUtils.randomGen(8);
					String plainPwd = "123456";
					String encryptedPwd = PasswordUtil.encrypt(sysUser.getUsername(), plainPwd, salt);
					sysUser.setSalt(salt);
					sysUser.setPassword(encryptedPwd);
					// 补充系统用户字段（适配你的业务）
					sysUser.setDepartIds("1"); // 根部门ID，根据你的系统调整
					sysUser.setStatus(1); // 账号启用
					sysUser.setDelFlag(CommonConstant.DEL_FLAG_0);
					sysUser.setCreateTime(new Date());
					sysUser.setLastPwdUpdateTime(new Date());
					sysUser.setOrgCode(null);

					// 9. 保存系统用户
					boolean saveUserOk = sysUserService.save(sysUser);
					if (!saveUserOk) {
						failMsgList.add("第" + totalRow + "行：学号【" + studentId + "】创建系统账号失败");
						continue;
					}

					// 10. 绑定用户-角色关系
					SysUserRole userRole = new SysUserRole();
					userRole.setUserId(sysUser.getId()); // SysUser的ID是String类型
					userRole.setRoleId(studentRole.getId()); // SysRole的ID适配String
					sysUserRoleService.save(userRole);

					// 11. 关联用户ID到学生表
					student.setUserId(sysUser.getId());
					// 补充创建时间（可选）
					student.setCreateTime(new Date());
					// 加入有效列表
					validStudentList.add(student);
				}
			}

			// 12. 批量保存有效数据（核心：只存过滤后的数据，避免空值）
			if (!validStudentList.isEmpty()) {
				tStudentService.saveBatch(validStudentList);

				for (TStudent student : validStudentList) {
					generateStudentSubscription(student);
				}
			}

			// 13. 构建返回结果
			StringBuilder result = new StringBuilder();
			result.append("导入完成！成功导入【").append(validStudentList.size()).append("】条有效数据");
			if (!failMsgList.isEmpty()) {
				result.append("；失败【").append(failMsgList.size()).append("】条数据，原因：");
				// 只展示前10条失败信息，避免返回内容过长
				List<String> showFailMsg = failMsgList.size() > 10 ? failMsgList.subList(0, 10) : failMsgList;
				result.append(String.join("；", showFailMsg));
				if (failMsgList.size() > 10) {
					result.append("；还有").append(failMsgList.size() - 10).append("条失败信息未展示");
				}
			}

			// 无有效数据时返回错误，有则返回成功（含失败提示）
			if (validStudentList.isEmpty()) {
				return Result.error(result.toString());
			} else {
				return Result.OK(result.toString());
			}

		} catch (Exception e) {
			log.error("Excel导入学生数据失败", e);
			return Result.error("导入失败：" + e.getMessage());
		}
    }


	 /**
	  * 登录后获取当前用户的学生信息
	  */
	 @Operation(summary="获取当前登录学生的信息")
	 @GetMapping("/getCurrentStudent")
	 public Result<TStudent> getCurrentStudent() {
		 // 1. 获取Shiro当前登录的Subject（核心）
		 Subject subject = org.apache.shiro.SecurityUtils.getSubject();
		 if (subject == null || !subject.isAuthenticated()) {
			 return Result.error("用户未登录，请先登录");
		 }

		 // 2. 直接从Principal获取SysUser（绕过LoginUser，万能写法）
		 Object principal = subject.getPrincipal();
		 if (principal == null) {
			 return Result.error("未获取到当前登录用户信息");
		 }

		 // 3. 适配不同版本：如果Principal是LoginUser，转成SysUser；如果直接是SysUser，直接用
		 SysUser currentUser = null;
		 if (principal instanceof SysUser) {
			 // 版本1：Principal直接是SysUser（部分JeecgBoot版本）
			 currentUser = (SysUser) principal;
		 } else {
			 // 版本2：Principal是LoginUser，用反射/强制转换获取SysUser（兼容所有版本）
			 try {
				 // 尝试调用getUser()（替代getSysUser()，多数版本用这个）
				 Method getUserMethod = principal.getClass().getMethod("getUser");
				 currentUser = (SysUser) getUserMethod.invoke(principal);
			 } catch (Exception e) {
				 // 兜底：如果getUser()也没有，提示版本适配问题
				 return Result.error("当前JeecgBoot版本的LoginUser无getSysUser/getUser方法，请核对类结构");
			 }
		 }

		 // 4. 根据sys_user.id查询学生表
		 if (currentUser == null) {
			 return Result.error("未解析到当前登录的系统用户信息");
		 }
		 TStudent student = tStudentService.lambdaQuery()
				 .eq(TStudent::getUserId, currentUser.getId())
				 .one();

		 if (student == null) {
			 return Result.error("当前登录用户不是学生，未找到对应信息");
		 }
		 return Result.OK(student);
	 }


	 /**
	  * 按学号查询学生信息
	  */
	 @GetMapping(value = "/queryByStudentId")
	 public Result<TStudent> queryByStudentId(@RequestParam(name="studentId") String studentId) {
		 QueryWrapper<TStudent> wrapper = new QueryWrapper<>();
		 wrapper.eq("student_id", studentId);
		 TStudent student = tStudentService.getOne(wrapper);
		 if(student == null) {
			 return Result.error("未找到对应学生");
		 }
		 return Result.OK(student);
	 }

	 /**
	  * 按学号查询学生信息
	  */
	 @Operation(summary="按学号查询学生信息")
	 @GetMapping(value = "/queryByNo")
	 public Result<TStudent> queryByNo(@RequestParam(name="studentNo") String studentNo) {
		 // 核心：查询条件用数据库字段student_id，参数名用studentNo（匹配前端）
		 QueryWrapper<TStudent> wrapper = new QueryWrapper<>();
		 wrapper.eq("student_id", studentNo);
		 TStudent student = tStudentService.getOne(wrapper);
		 if(student == null) {
			 return Result.error("未找到对应学生");
		 }
		 return Result.OK(student);
	 }

	 /**
	  * 为单个学生生成征订/领取/账单记录（适配教材选用表已有记录时新增学生的场景）
	  * @param student 新增/导入的学生对象
	  */
	 private void generateStudentSubscription(TStudent student) {
		 try {
			 // 1. 校验学生核心字段（班级ID不能为空）
			 if (oConvertUtils.isEmpty(student.getClassId())) {
				 log.warn("学生{}（学号{}）无班级信息，跳过征订记录生成", student.getId(), student.getStudentId());
				 return;
			 }

			 // 2. 查询该学生班级的有效教材选用记录（生效状态=1）
			 QueryWrapper<TTextbookSelection> selectionWrapper = new QueryWrapper<>();
			 selectionWrapper.eq("class_id", student.getClassId())
					 .eq("selection_status", "1"); // 仅处理生效的教材选用记录
			 List<TTextbookSelection> selectionList = tTextbookSelectionService.list(selectionWrapper);
			 if (selectionList.isEmpty()) {
				 log.info("学生{}（学号{}）所在班级{}无有效教材选用记录，跳过征订记录生成",
						 student.getId(), student.getStudentId(), student.getClassId());
				 return;
			 }

			 // 3. 遍历教材选用记录，为该学生生成关联记录
			 for (TTextbookSelection selection : selectionList) {
				 // 3.1 防重复：检查该学生+该教材+该学年学期是否已存在征订记录
				 QueryWrapper<TSubscription> subExistWrapper = new QueryWrapper<>();
				 subExistWrapper.eq("student_id", student.getId())
						 .eq("textbook_id", selection.getTextbookId())
						 .eq("subscription_year", selection.getSchoolYear())
						 .eq("subscription_semester", selection.getSemester());
				 if (tSubscriptionService.count(subExistWrapper) > 0) {
					 log.warn("学生{}（学号{}）已存在教材{}的征订记录，跳过",
							 student.getId(), student.getStudentId(), selection.getTextbookId());
					 continue;
				 }

				 // ========== 生成征订记录 ==========
				 TSubscription subscription = new TSubscription();
				 subscription.setStudentId(student.getId()); // 学生主键ID
				 subscription.setTextbookId(selection.getTextbookId()); // 教材ID
				 subscription.setSelectionId(selection.getId()); // 关联教材选用记录ID
				 subscription.setMajorId(selection.getMajorId()); // 专业ID
				 subscription.setSubscriptionYear(selection.getSchoolYear()); // 征订学年
				 subscription.setSubscriptionSemester(selection.getSemester()); // 征订学期
				 subscription.setSubscribeStatus("未设置"); // 初始征订状态
				 subscription.setRemark("");
				 subscription.setCreateTime(new Date());
				 subscription.setUpdateTime(new Date());
				 tSubscriptionService.save(subscription);
				 log.info("为学生{}生成征订记录：{}", student.getStudentId(), subscription.getId());


			 }
		 } catch (Exception e) {
			 log.error("为学生{}生成征订记录失败", student.getStudentId(), e);
			 throw new RuntimeException("学生新增成功，但生成教材征订相关记录失败：" + e.getMessage());
		 }
	 }

}
