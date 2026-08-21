package com.wanzhongxiang.mapper;

import com.github.pagehelper.Page;
import com.wanzhongxiang.annotation.AutoFill;
import com.wanzhongxiang.dto.SetmealPageQueryDTO;
import com.wanzhongxiang.entity.Setmeal;
import com.wanzhongxiang.entity.SetmealDish;
import com.wanzhongxiang.enumeration.OperationType;
import com.wanzhongxiang.vo.DishItemVO;
import com.wanzhongxiang.vo.SetmealVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询组合商品的数量
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    // 插入组合商品数据
    @AutoFill(value = OperationType.INSERT)
    void insert(Setmeal setmeal);

    // 组合商品分页查询
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    // 根据 id 查询组合商品
    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    // 根据 id 删除组合商品
    @Delete("delete from setmeal where id = #{id}")
    void deleteById(Long setmealId);

    // 根据 id 查询关联商品
    @Select("select * from setmeal_dish where id = #{id}")
    List<SetmealDish> getBySetmealId(Long id);

    // 修改商品数据
    @AutoFill(OperationType.UPDATE)
    void update(Setmeal setmeal);

    // 根据分类id查询组合商品
    List<Setmeal> list(Setmeal setmeal);

    // 根据组合商品id查询包含的商品列表
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 根据条件统计组合商品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

}
