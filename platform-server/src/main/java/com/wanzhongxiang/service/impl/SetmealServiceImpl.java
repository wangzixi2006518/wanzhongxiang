package com.wanzhongxiang.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.constant.StatusConstant;
import com.wanzhongxiang.dto.SetmealDTO;
import com.wanzhongxiang.dto.SetmealPageQueryDTO;
import com.wanzhongxiang.entity.Setmeal;
import com.wanzhongxiang.entity.SetmealDish;
import com.wanzhongxiang.exception.DeletionNotAllowedException;
import com.wanzhongxiang.mapper.DishMapper;
import com.wanzhongxiang.mapper.SetmealDishMapper;
import com.wanzhongxiang.mapper.SetmealMapper;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.service.SetmealService;
import com.wanzhongxiang.vo.DishItemVO;
import com.wanzhongxiang.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    @Override
    @Transactional // 确保原子性
    public void insert(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        // 向组合商品表插入数据
        setmealMapper.insert(setmeal);

        // 获取插入的组合商品 id
        Long setmealId = setmeal.getId();

        // 给组合商品内的每个商品附上组合商品 id
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        // 保存组合商品和商品的关联关系
        setmealDishMapper.insertBatch(setmealDishes);

    }

    // 分页查询
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);

        long total = page.getTotal();
        List<SetmealVO> result = page.getResult();

        return new PageResult(total,result);
    }

    // 批量删除
    @Override
    @Transactional
    public void deleteDatch(List<Long> ids) {
        // 判断当前批量删除中的组合商品能否删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            // 如果包含启售的组合商品 -> 删除失败
            if(setmeal.getStatus() == StatusConstant.ENABLE){ // 1 启售
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE); // 启售组合商品不能删除
            }

            ids.forEach(setmealId ->{
                setmealMapper.deleteById(setmealId);
                setmealDishMapper.deleteBySetmealId(setmealId);
            });

        }
    }

    @Override
    public SetmealVO getByIdWithDish(Long id) {
        // 查询基本信息
        Setmeal setmeal = setmealMapper.getById(id);
        // 查询关联商品
        List<SetmealDish> setmealDishes = setmealMapper.getBySetmealId(id);

        // 组装返回对象
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO); // 将 setmeal 复制公共属性给 setmealVO
        setmealVO.setSetmealDishes(setmealDishes); // 补充setmealDishes

        return setmealVO;
    }

    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        // 1.修改组合商品表
        setmealMapper.update(setmeal);

        // 2.删除组合商品和商品关系
        Long setmealId = setmealDTO.getId();
        setmealDishMapper.deleteBySetmealId(setmealId);

        // 3.重新建立新的组合商品和商品关系
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        // 4.批量插入新的关联商品
        setmealDishMapper.insertBatch(setmealDishes);
    }

    // 组合商品启售停售
    @Override
    public void startOrStop(Integer status, Long id) {
        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }

    // 根据分类id查询组合商品
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    // 根据组合商品id查询包含的商品列表
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}
