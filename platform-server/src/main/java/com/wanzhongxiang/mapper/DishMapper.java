package com.wanzhongxiang.mapper;

import com.github.pagehelper.Page;
import com.wanzhongxiang.annotation.AutoFill;
import com.wanzhongxiang.dto.DishPageQueryDTO;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.enumeration.OperationType;
import com.wanzhongxiang.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询商品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    // 新增商品数据
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    // 商品分页查询
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    // 根据主键查询商品信息
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    // 根据主键查删除商品信息
    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);

    // 根据商品 id 集合批量删除商品
    void deleteByIds(List<Long> ids);

    // 修改基本信息（不等于 null 时再修改，需动态 sql）
    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    // 动态条件查询商品
    List<Dish> queryByCategoryId(Dish dish);

    /**
     * 根据条件统计商品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

}
