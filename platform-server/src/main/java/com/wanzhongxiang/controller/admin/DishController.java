package com.wanzhongxiang.controller.admin;

import com.github.pagehelper.Page;
import com.wanzhongxiang.dto.DishDTO;
import com.wanzhongxiang.dto.DishPageQueryDTO;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.DishService;
import com.wanzhongxiang.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

// 商品管理
@RestController
@RequestMapping("/admin/product")
@Api(tags = "商品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    // 新增商品
    @PostMapping
    @ApiOperation("新增商品")
    public Result save(@RequestBody DishDTO dishDTO){ // 返回 data 非必须，可以不写泛型
        log.info("新增商品：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);

        // 新增后需要清理缓存数据
        String key = "dish_" + dishDTO.getCategoryId();
//        redisTemplate.delete(key);
        clanCache(key);

        return Result.success();
    }

    // 分页查询
    @GetMapping("/page")
    @ApiOperation("商品分页查询")
    public Result<PageResult> Page(DishPageQueryDTO dishPageQueryDTO){
        log.info("商品分页查询：{}",dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    // 商品删除
    @DeleteMapping
    @ApiOperation("商品批量删除")
    public Result delete(@RequestParam List<Long> ids){ // 动态解析字符串，string -> List   1,2,3 -> {1,2,3}
        log.info("商品批量删除：{}",ids);
        dishService.deleteBatch(ids);

        // 批量删除可能涉及多个分类，所以清除所有商品分类的缓存（dish_ 开头的 key）
//        Set keys = redisTemplate.keys("dish_*"); // 获取所有 dish_ 开头的 key
//        redisTemplate.delete(keys); // 支持传进来集合
        clanCache("dish_*");

        return Result.success();
    }

    // 根据 id 查询商品
    @GetMapping("/{id}")
    @ApiOperation("根据 id 查询商品")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据 id 查询商品：{}",id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    // 修改商品
    @PutMapping
    @ApiOperation("修改商品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改商品：{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);

        // 修改商品时修改了分类，可能影响两份商品分类数据，统一清除所有商品分类缓存
//        Set keys = redisTemplate.keys("dish_*"); // 获取所有 dish_ 开头的 key
//        redisTemplate.delete(keys); // 支持传进来集合
        clanCache("dish_*");

        return Result.success();
    }

    // 根据分类 id 查询商品
    @GetMapping("list")
    @ApiOperation("根据分类 id 查询商品")
    public Result<List<Dish>> list(Long categoryId){
        log.info("根据分类 id 查询商品：{}",categoryId);
        List<Dish> list = dishService.QueryByCategoryId(categoryId);
        return Result.success(list);
    }

    // 商品启售停售
    @PostMapping("/status/{status}")
    @ApiOperation("商品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, Long id){
        dishService.startOrStop(status,id);

        // 启售停售也修改了对应的组合商品，可能影响多份商品分类数据，统一清除所有商品分类缓存
//        Set keys = redisTemplate.keys("dish_*"); // 获取所有 dish_ 开头的 key
//        redisTemplate.delete(keys); // 支持传进来集合
        clanCache("dish_*");

        return Result.success();
    }

    // 抽取清理缓存的方法
    private void clanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);// 根据模式查出对应的 key
        redisTemplate.delete(keys);
    }

}
