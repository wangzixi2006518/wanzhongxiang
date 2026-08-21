package com.wanzhongxiang.mapper;

import com.wanzhongxiang.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    // 批量插入规格数据
    void insertBatch(List<DishFlavor> flavors);

    // 根据 id 删除商品关联的规格数据
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);

    // 根据商品 id 查询对应规格数据
    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);

    // 根据商品 id 集合批量删除关联的规格数据
    void deleteByDishIds(List<Long> dishIds);
}
