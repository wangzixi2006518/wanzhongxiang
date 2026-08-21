package com.wanzhongxiang.dto;

import com.wanzhongxiang.entity.SetmealDish;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SetmealDTO implements Serializable {

    private Long id;

    //分类id
    private Long categoryId;

    //组合商品名称
    private String name;

    //组合商品价格
    private BigDecimal price;

    //状态 0:停用 1:启用
    private Integer status;

    //描述信息
    private String description;

    //图片
    private String image;

    //组合商品明细关系
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}
