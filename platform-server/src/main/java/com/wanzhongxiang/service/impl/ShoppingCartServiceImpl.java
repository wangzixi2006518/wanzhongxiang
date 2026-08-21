package com.wanzhongxiang.service.impl;

import com.wanzhongxiang.context.BaseContext;
import com.wanzhongxiang.dto.ShoppingCartDTO;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.entity.Setmeal;
import com.wanzhongxiang.entity.ShoppingCart;
import com.wanzhongxiang.mapper.DishMapper;
import com.wanzhongxiang.mapper.SetmealMapper;
import com.wanzhongxiang.mapper.ShoppingCartMapper;
import com.wanzhongxiang.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    // 添加购物车
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 判断当前加入的商品在购物车是否存在
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart); // 属性拷贝
        Long userId = BaseContext.getCurrentId(); // 从 ThreadLocal 拿到用户 id
        shoppingCart.setUserId(userId); // 设置进去

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        if(list != null && list.size() > 0){
            // 存在，只需数量更新 + 1
            ShoppingCart cart = list.get(0); // 取出第一（唯一）条数据
            cart.setNumber(cart.getNumber() + 1); // 将数量 + 1
            shoppingCartMapper.updateNumberById(cart);
        }else{
            // 不存在，需要插入购物车数据
            // 判断添加到购物车的是商品还是组合商品
            Long dishId = shoppingCartDTO.getDishId();
            if(dishId != null){
                // 添加到购物车的是商品
                Dish dish = dishMapper.getById(dishId);
                // 将商品的属性交给购物车
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }else {
                // 添加到购物车的是组合商品
                Long setmealId = shoppingCartDTO.getSetmealId();
                Setmeal setmeal = setmealMapper.getById(setmealId);
                // 将商品的属性交给购物车
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            // 相同代码提取到 if else 外面
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            // 插入到数据库
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    // 查看购物车
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long currentId = BaseContext.getCurrentId(); // 获取当前用户的 id
        // 构造一个符合传参条件的对象
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(currentId)
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }

    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 将属性交给 shoppingCart
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        //设置查询条件，查询当前登录用户的购物车数据
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        if(list != null && list.size() > 0){
            shoppingCart = list.get(0);// 取出第一（唯一）条数据
            Integer number = shoppingCart.getNumber();
            if(number == 1){ // 当数量等于 1 时直接删除
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }else{ // 数量- 1
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }
        }
    }

    // 清空购物车
    @Override
    public void cleanShoppingCart() {
        // 获得当前用户 id
        Long userId = BaseContext.getCurrentId();
        // 根据用户 id 清空购物车
        shoppingCartMapper.deleteByUserId(userId);
    }
}
