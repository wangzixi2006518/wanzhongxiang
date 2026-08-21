package com.wanzhongxiang.mapper;

import com.wanzhongxiang.annotation.AutoFill;
import com.wanzhongxiang.entity.SetmealDish;
import com.wanzhongxiang.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    // 根据商品 id 查询对应的组合商品 id
    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    // 保存组合商品和商品的关联关系
    void insertBatch(List<SetmealDish> setmealDishes);

    // 根据组合商品id删除组合商品和商品的关联关系
    @Delete("delete from setmeal_dish where setmeal_id = #{setmealId}")
    void deleteBySetmealId(Long setmealId);
}
