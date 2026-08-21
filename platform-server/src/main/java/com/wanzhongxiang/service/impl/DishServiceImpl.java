package com.wanzhongxiang.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.constant.StatusConstant;
import com.wanzhongxiang.dto.DishDTO;
import com.wanzhongxiang.dto.DishPageQueryDTO;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.entity.DishFlavor;
import com.wanzhongxiang.entity.Setmeal;
import com.wanzhongxiang.exception.DeletionNotAllowedException;
import com.wanzhongxiang.mapper.DishFlavorMapper;
import com.wanzhongxiang.mapper.DishMapper;
import com.wanzhongxiang.mapper.SetmealDishMapper;
import com.wanzhongxiang.mapper.SetmealMapper;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.service.DishService;
import com.wanzhongxiang.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    // 新增商品和规格
    @Override
    @Transactional // 事务注解 -> 多个数据表操作保证数据一致性（原子性：要么全成功，要么全失败）
    public void saveWithFlavor(DishDTO dishDTO) {

        // 实体对象
        Dish dish = new Dish();
        // 通过属性拷贝给 dish 赋值
        BeanUtils.copyProperties(dishDTO,dish); // 将 dishDTO 拷给 dish

        // 向商品表插入 1 条数据
        dishMapper.insert(dish); // 不用传 dishDTO，因为里面包含了规格数据，只需要传个实体对象

        // 获取 insert 语句生成的主键值 id
        Long dishId = dish.getId();

        // 向规格表插入 n 条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();// 先取出集合数据
        if(flavors != null && flavors.size() > 0){ // 确保填写了规格
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId); // 给 DishFlavor 里的每一个 dishId 属性赋值
            });
            // 向规格表插入 n 条数据
            dishFlavorMapper.insertBatch(flavors); // 批量插入
        }

    }

    // 商品分页查询
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize()); // 开始分页
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO); // 后端传前端 VO
        return new PageResult(page.getTotal(),page.getResult());
    }

    // 商品批量删除
    @Override
    @Transactional // 事务保证代码一致性
    public void deleteBatch(List<Long> ids) {
        // 1.判断当前商品是否能够删除 -> 是否存在启售中的商品
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){ // 1 启售
                // 当前商品处于启售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE); // "启售中的商品不能删除"
            }
        }

        // 2.判断当前商品是否能够删除 -> 当前商品是否被组合商品关联
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(setmealIds != null && setmealIds.size() > 0){ // 存在关联组合商品
            // 当前商品被组合商品关联，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL); // "当前商品关联了组合商品,不能删除"
        }

//        // 3.删除商品表中的商品数据
//        for (Long id : ids) {
//            dishMapper.deleteById(id);
//            // 4.根据 id 删除商品关联的规格数据
//            dishFlavorMapper.deleteByDishId(id);
//        }
        // 优化
        // 根据商品 id 集合批量删除商品数据
        // sql：delete from dish where id in( ? )
        dishMapper.deleteByIds(ids);
        // 根据商品 id 集合批量删除关联的规格数据
        // sql：delete from dish_flavor where dish_id in( ? )
        dishFlavorMapper.deleteByDishIds(ids);
    }

    // 根据 id 查询商品和对应的规格数据
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 根据 id 查询商品数据
        Dish dish = dishMapper.getById(id);

        // 根据商品 id 查询规格数据（多个用集合）
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        // 将查询到的数据封装到 VO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO); // 对象属性拷贝
        dishVO.setFlavors(dishFlavors); // 设置规格

        return dishVO; // 返回给 Controller
    }

    // 根据 id 修改商品信息和规格信息
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        // 修改基本信息
        // 不修改规格信息，所以直接传 dishDTO 不合适
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish); // 属性拷贝到没有规格信息的 dish 上去
        dishMapper.update(dish);

        // 修改规格信息
        // 1.删除原有规格数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 2.重新插入规格信息
        List<DishFlavor> flavors = dishDTO.getFlavors(); // 拿到前端传过来的规格数据
        if(flavors != null && flavors.size() > 0){ // 确保填写了规格
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId()); // 给 DishFlavor 里的每一个 dishId 属性赋值
            });
            // 向规格表插入 n 条数据
            dishFlavorMapper.insertBatch(flavors); // 批量插入
        }

    }

    @Override
    public List<Dish> QueryByCategoryId(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE) // 1
                .build();

        return dishMapper.queryByCategoryId(dish);
    }

    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.queryByCategoryId(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据商品id查询对应的规格
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);

        if (status == StatusConstant.DISABLE) {
            // 如果是停售操作，还需要将包含当前商品的组合商品也停售
            List<Long> dishIds = new ArrayList<>(); // 为了兼容批量删除的查询 sql 语句，用 List 接收
            dishIds.add(id);
            // select setmeal_id from setmeal_dish where dish_id in (?,?,?)
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) { // 如果停售的商品在大于0个组合商品
                // 将对应的组合商品全部停售
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .build();
                    setmealMapper.update(setmeal);
                }
            }
        }
    }

}
