package com.wanzhongxiang.service;

import com.wanzhongxiang.dto.DishDTO;
import com.wanzhongxiang.dto.DishPageQueryDTO;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.vo.DishVO;

import java.util.List;

public interface DishService {

    // 新增商品和规格
    public void saveWithFlavor(DishDTO dishDTO);

    // 商品分页查村
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    // 商品批量删除
    void deleteBatch(List<Long> ids);

    // 根据 id 查询商品和对应的规格数据
    DishVO getByIdWithFlavor(Long id);

    // 根据 id 修改商品信息和规格信息
    void updateWithFlavor(DishDTO dishDTO);

    // 根据分类 id 查询商品
    List<Dish> QueryByCategoryId(Long categoryId);

    // 条件查询商品规格
    List<DishVO> listWithFlavor(Dish dish);

    // 商品启售停售
    void startOrStop(Integer status, Long id);
}
