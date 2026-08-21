package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.dto.SetmealDTO;
import com.wanzhongxiang.dto.SetmealPageQueryDTO;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.SetmealService;
import com.wanzhongxiang.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/bundle")
@Api(tags = "组合商品相关接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @PostMapping
    @ApiOperation("新增组合商品")
    @CacheEvict(cacheNames = "setmealCache",key = "#setmealDTO.categoryId") // key：setmealCache::categoryId
    public Result insert(@RequestBody SetmealDTO setmealDTO){
        log.info("新增组合商品：{}",setmealDTO);
        setmealService.insert(setmealDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("组合商品分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO){
        log.info("组合商品分页查询：{}",setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("批量删除")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true) // 清理所有缓存
    public Result delete(@RequestParam List<Long> ids){
        log.info("批量删除：{}",ids);
        setmealService.deleteDatch(ids);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("根据 id 查询组合商品回显")
    public Result<SetmealVO> getById(@PathVariable Long id){
        log.info("根据 id 查询组合商品回显：{}",id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    @PutMapping
    @ApiOperation("修改组合商品")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true) // 清理所有缓存
    public Result update(@RequestBody SetmealDTO setmealDTO){
        log.info("修改组合商品：{}",setmealDTO);
        setmealService.update(setmealDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("组合商品启售停售")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true) // 清理所有缓存
    public Result startOrStop(@PathVariable Integer status,Long id){
        setmealService.startOrStop(status,id);
        return Result.success();
    }

}
