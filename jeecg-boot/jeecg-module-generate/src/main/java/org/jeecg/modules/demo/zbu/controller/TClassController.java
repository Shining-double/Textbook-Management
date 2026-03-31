package org.jeecg.modules.demo.zbu.controller;

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
import org.jeecg.modules.demo.zbu.entity.TClass;
import org.jeecg.modules.demo.zbu.service.ITClassService;

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
 * @Description: 班级表
 * @Author: jeecg-boot
 * @Date:   2026-01-26
 * @Version: V1.0
 */
@Tag(name="班级表")
@RestController
@RequestMapping("/zbu/tClass")
@Slf4j
public class TClassController extends JeecgController<TClass, ITClassService> {
	@Autowired
	private ITClassService tClassService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tClass
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "班级表-分页列表查询")
	@Operation(summary="班级表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TClass>> queryPageList(TClass tClass,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


        QueryWrapper<TClass> queryWrapper = QueryGenerator.initQueryWrapper(tClass, req.getParameterMap());
		Page<TClass> page = new Page<TClass>(pageNo, pageSize);
		IPage<TClass> pageList = tClassService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tClass
	 * @return
	 */
	@AutoLog(value = "班级表-添加")
	@Operation(summary="班级表-添加")
	@RequiresPermissions("zbu:t_class:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TClass tClass) {

		// 检查班级编码是否已存在
		QueryWrapper<TClass> codeWrapper = new QueryWrapper<>();
		codeWrapper.eq("class_code", tClass.getClassCode());
		if (tClassService.count(codeWrapper) > 0) {
			return Result.error("添加失败：班级编码已存在！");
		}

		// 检查班级名称是否已存在
		QueryWrapper<TClass> nameWrapper = new QueryWrapper<>();
		nameWrapper.eq("class_name", tClass.getClassName());
		if (tClassService.count(nameWrapper) > 0) {
			return Result.error("添加失败：班级名称已存在！");
		}

		tClassService.save(tClass);

		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tClass
	 * @return
	 */
	@AutoLog(value = "班级表-编辑")
	@Operation(summary="班级表-编辑")
	@RequiresPermissions("zbu:t_class:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TClass tClass) {
		tClassService.updateById(tClass);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "班级表-通过id删除")
	@Operation(summary="班级表-通过id删除")
	@RequiresPermissions("zbu:t_class:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tClassService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "班级表-批量删除")
	@Operation(summary="班级表-批量删除")
	@RequiresPermissions("zbu:t_class:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tClassService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "班级表-通过id查询")
	@Operation(summary="班级表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TClass> queryById(@RequestParam(name="id",required=true) String id) {
		TClass tClass = tClassService.getById(id);
		if(tClass==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tClass);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tClass
    */
    @RequiresPermissions("zbu:t_class:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TClass tClass) {
        return super.exportXls(request, tClass, TClass.class, "班级表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:t_class:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
		try {
			// 1. 获取上传的Excel文件
			MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
			Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
			if (fileMap.isEmpty()) {
				return Result.error("请选择要导入的Excel文件！");
			}

			// 2. 基础导入参数配置
			ImportParams importParams = new ImportParams();
			importParams.setTitleRows(2); // 标题行数量
			importParams.setHeadRows(1); // 表头行数量
			importParams.setNeedSave(false);

			// 存储有效数据和失败信息
			List<TClass> validClassList = new ArrayList<>();
			List<String> failMsgList = new ArrayList<>();
			int totalRow = 0;

			// 3. 遍历解析Excel文件
			for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
				MultipartFile file = entry.getValue();
				if (file.isEmpty()) {
					continue;
				}

				// 4. 解析Excel为班级列表
				List<TClass> tempList = ExcelImportUtil.importExcel(
						file.getInputStream(),
						TClass.class,
						importParams);

				// 5. 逐行校验+过滤
				for (int i = 0; i < tempList.size(); i++) {
					totalRow = i + 2; // Excel行号（标题+表头后从第2行开始）
					TClass clazz = tempList.get(i);

					// 5.1 班级编码空值校验
					String classCode = clazz.getClassCode();
					if (oConvertUtils.isEmpty(classCode) || classCode.trim().isEmpty()) {
						failMsgList.add("第" + totalRow + "行：班级编码为空，跳过导入");
						continue;
					}
					clazz.setClassCode(classCode.trim());

					// 5.2 班级名称空值校验
					String className = clazz.getClassName();
					if (oConvertUtils.isEmpty(className) || className.trim().isEmpty()) {
						failMsgList.add("第" + totalRow + "行：班级名称为空（编码：" + classCode + "），跳过导入");
						continue;
					}
					clazz.setClassName(className.trim());

					// 5.3 所属专业空值校验
					if (oConvertUtils.isEmpty(clazz.getMajorId())) {
						failMsgList.add("第" + totalRow + "行：所属专业为空（编码：" + classCode + "），跳过导入");
						continue;
					}

					// 5.4 辅导员空值校验
					if (oConvertUtils.isEmpty(clazz.getCounselorId())) {
						failMsgList.add("第" + totalRow + "行：辅导员为空（编码：" + classCode + "），跳过导入");
						continue;
					}

					// 6. 检查班级编码是否已存在
					QueryWrapper<TClass> codeWrapper = new QueryWrapper<>();
					codeWrapper.eq("class_code", clazz.getClassCode());
					if (tClassService.count(codeWrapper) > 0) {
						failMsgList.add("第" + totalRow + "行：班级编码【" + classCode + "】已存在，跳过导入");
						continue;
					}

					// 7. 检查班级名称是否已存在
					QueryWrapper<TClass> nameWrapper = new QueryWrapper<>();
					nameWrapper.eq("class_name", clazz.getClassName());
					if (tClassService.count(nameWrapper) > 0) {
						failMsgList.add("第" + totalRow + "行：班级名称【" + className + "】已存在，跳过导入");
						continue;
					}

					// 8. 加入有效列表
					clazz.setCreateTime(new Date());
					validClassList.add(clazz);
				}
			}

			// 9. 批量保存有效数据
			if (!validClassList.isEmpty()) {
				tClassService.saveBatch(validClassList);
			}

			// 10. 构建返回结果
			StringBuilder result = new StringBuilder();
			result.append("导入完成！成功导入【").append(validClassList.size()).append("】条有效数据");
			if (!failMsgList.isEmpty()) {
				result.append("；失败【").append(failMsgList.size()).append("】条数据，原因：");
				List<String> showFailMsg = failMsgList.size() > 10 ? failMsgList.subList(0, 10) : failMsgList;
				result.append(String.join("；", showFailMsg));
				if (failMsgList.size() > 10) {
					result.append("；还有").append(failMsgList.size() - 10).append("条失败信息未展示");
				}
			}

			if (validClassList.isEmpty()) {
				return Result.error(result.toString());
			} else {
				return Result.OK(result.toString());
			}

		} catch (Exception e) {
			log.error("Excel导入班级数据失败", e);
			return Result.error("导入失败：" + e.getMessage());
		}
    }

}
