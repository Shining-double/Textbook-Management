package org.jeecg.modules.demo.zbu.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return super.importExcel(request, response, TClass.class);
    }

}
