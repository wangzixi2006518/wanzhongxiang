package com.wanzhongxiang.controller.user;

import com.wanzhongxiang.constant.StatusConstant;
import com.wanzhongxiang.entity.Dish;
import com.wanzhongxiang.result.Result;
import com.wanzhongxiang.service.DishService;
import com.wanzhongxiang.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/product")
@Slf4j
@Api(tags = "C端-商品浏览接口")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询商品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询商品")
    public Result<List<DishVO>> list(Long categoryId) {
        // 构造 redis 中的 key，规则：dish_分类id
        String key = "dish_" + categoryId;

        // 查询 redis 中是否存在商品数据（放进去什么类型，取出来什么类型）
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if(list != null && list.size() > 0){
            // 如果存在，直接返回，不用查询数据库
            return Result.success(list);
        }

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的商品

        // 如果不存在，查询数据库，并将查询到的数据放入 redis
//        List<DishVO> list = dishService.listWithFlavor(dish);
        list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key,list);

        return Result.success(list);
    }

}
