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
import org.jeecg.modules.demo.zbu.entity.TMajor;
import org.jeecg.modules.demo.zbu.service.ITMajorService;

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
 * @Description: 专业表
 * @Author: jeecg-boot
 * @Date:   2026-01-19
 * @Version: V1.0
 */
@Tag(name="专业表")
@RestController
@RequestMapping("/zbu/tMajor")
@Slf4j
public class TMajorController extends JeecgController<TMajor, ITMajorService> {
	@Autowired
	private ITMajorService tMajorService;
	
	/**
	 * 分页列表查询
	 *
	 * @param tMajor
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "专业表-分页列表查询")
	@Operation(summary="专业表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<TMajor>> queryPageList(TMajor tMajor,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {


        QueryWrapper<TMajor> queryWrapper = QueryGenerator.initQueryWrapper(tMajor, req.getParameterMap());
		Page<TMajor> page = new Page<TMajor>(pageNo, pageSize);
		IPage<TMajor> pageList = tMajorService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param tMajor
	 * @return
	 */
	@AutoLog(value = "专业表-添加")
	@Operation(summary="专业表-添加")
	@RequiresPermissions("zbu:t_major:add")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody TMajor tMajor) {
		tMajorService.save(tMajor);

		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param tMajor
	 * @return
	 */
	@AutoLog(value = "专业表-编辑")
	@Operation(summary="专业表-编辑")
	@RequiresPermissions("zbu:t_major:edit")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody TMajor tMajor) {
		tMajorService.updateById(tMajor);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "专业表-通过id删除")
	@Operation(summary="专业表-通过id删除")
	@RequiresPermissions("zbu:t_major:delete")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		tMajorService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "专业表-批量删除")
	@Operation(summary="专业表-批量删除")
	@RequiresPermissions("zbu:t_major:deleteBatch")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.tMajorService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "专业表-通过id查询")
	@Operation(summary="专业表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<TMajor> queryById(@RequestParam(name="id",required=true) String id) {
		TMajor tMajor = tMajorService.getById(id);
		if(tMajor==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(tMajor);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param tMajor
    */
    @RequiresPermissions("zbu:t_major:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, TMajor tMajor) {
        return super.exportXls(request, tMajor, TMajor.class, "专业表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequiresPermissions("zbu:t_major:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, TMajor.class);
    }

}
