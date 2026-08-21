package com.wanzhongxiang.service;


import com.wanzhongxiang.dto.SetmealDTO;
import com.wanzhongxiang.dto.SetmealPageQueryDTO;
import com.wanzhongxiang.entity.Setmeal;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.vo.DishItemVO;
import com.wanzhongxiang.vo.SetmealVO;

import java.util.List;

public interface SetmealService {

    // 新增组合商品
    void insert(SetmealDTO setmealDTO);

    // 组合商品分页查询
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    // 批量删除组合商品
    void deleteDatch(List<Long> ids);

    // 根据 id 查询组合商品回显
    SetmealVO getByIdWithDish(Long id);

    // 修改组合商品
    void update(SetmealDTO setmealDTO);

    // 组合商品启售停售
    void startOrStop(Integer status, Long id);

    // 根据分类id查询组合商品
    List<Setmeal> list(Setmeal setmeal);

    // 根据组合商品id查询包含的商品列表
    List<DishItemVO> getDishItemById(Long id);
}
