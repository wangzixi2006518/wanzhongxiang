package com.wanzhongxiang.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.context.BaseContext;
import com.wanzhongxiang.dto.*;
import com.wanzhongxiang.entity.*;
import com.wanzhongxiang.exception.AddressBookBusinessException;
import com.wanzhongxiang.exception.OrderBusinessException;
import com.wanzhongxiang.exception.ShoppingCartBusinessException;
import com.wanzhongxiang.mapper.*;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.service.OrderService;
import com.wanzhongxiang.utils.WeChatPayUtil;
import com.wanzhongxiang.vo.OrderPaymentVO;
import com.wanzhongxiang.vo.OrderStatisticsVO;
import com.wanzhongxiang.vo.OrderSubmitVO;
import com.wanzhongxiang.vo.OrderVO;
import com.wanzhongxiang.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    // 用户下单
    @Override
    @Transactional // 确保原子性
    public OrderSubmitVO submitService(OrdersSubmitDTO ordersSubmitDTO) {
        // 处理各种业务异常
        // 1.地址簿为空
        // 查询地址簿情况
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        // 判断
        if(addressBook == null){
            // 抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        // 2.购物车为空
        // 查询购物车情况
        Long userId = BaseContext.getCurrentId();
        // .list() 需要传入一个 ShoppingCart 对象
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        // 判断
        if(shoppingCartList == null || shoppingCartList.size() == 0){
            // 抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 向订单表插入 1 条数据
        Orders orders = new Orders();
        // 设置属性
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        // 调用插入方法
        orderMapper.insert(orders);

        // 向订单明细表插入 n 条数据（商品数量等决定）
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail(); // 订单明细对象
            BeanUtils.copyProperties(cart,orderDetail); // 除 OrderId 外属性都相同，直接拷贝
            orderDetail.setOrderId(orders.getId()); // 设置当前订单明细关联的 OrderId，需写主键回显
            orderDetailList.add(orderDetail);
        }
        // 批量插入
        orderDetailMapper.insertBatch(orderDetailList);

        // 下单成功清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 封装 VO 返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "万众享平台订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 通过 websocket 向客户端推送消息 type   orderId   content
        Map map = new HashMap<>();
        map.put("type",1); // 1 -> 来单提醒   2 -> 客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号：" + outTradeNo);

        // 转 json 格式
        String json = JSON.toJSONString(map);
        // 推送
        webSocketServer.sendToAllClient(json);

    }

    // 查询订单列表
    @Override
    public PageResult pageQueryUser(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId()); // 获取用户 id
        ordersPageQueryDTO.setStatus(status); // 设置状态

        // 分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();

        // 查询出订单明细，封装进 OrderVO 进行响应
        if(page != null && page.getTotal() > 0){
            log.info("进入循环");
            for (Orders orders : page) {
                Long ordersId = orders.getId(); // 订单 id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(ordersId);

                // 返回封装
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(),list);
    }

    // 查询订单详情
    @Override
    public OrderVO details(Long id) {
        // 根据 id 查询订单
        Orders orders = orderMapper.getById(id);

        // 根据订单查询商品
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 封装到 VO 并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    // 取消订单
    @Override
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if(ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND); // "订单不存在"
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if(ordersDB.getStatus() > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR); // 订单状态错误
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(), //商户订单号
//                    ordersDB.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
//
            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    // 再来一单
    @Override
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据 id 查找订单详情
         List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            // 将原订单转换为购物车对象，并补充属性
            BeanUtils.copyProperties(x, shoppingCart, "id"); // 拷贝每个订单 x 时忽略 id 属性，防止插入数据库冲突
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart; // 每创建一个订单详情，返回一个购物车对象
        }).collect(Collectors.toList());// 把map产生的结果重新收集成List

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    // 商家端订单搜索
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单商品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(),orderVOList);
    }

    // 各个状态的订单数量统计
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);// 待接单
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);// 待派送
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);// 派送中

        // 将查询出的数据封装到 orderStatisticsVO 中返回
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    // 接单
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    // 拒单
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR); // 订单状态错误
        }

        // 如果用户已支付需要退款
        Integer payStatus = ordersDB.getPayStatus();
        if(payStatus == Orders.PAID){ // 已支付
            // 退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
            log.info("申请退款");
        }

        // 根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED); // 取消状态
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    // 取消订单
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        // 管理端取消订单需要退款
        // 支付状态
        Integer payStatus = ordersDB.getStatus();
        if(payStatus == 1){
            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
            log.info("申请退款");
        }

        // 根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    // 派送
    @Override
    public void delivery(Long id) {
        // 根据 id 查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单状态,状态转为派送中
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    // 完成订单
    @Override
    public void complete(Long id) {
        // 根据 id 查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if(ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单状态,状态转为完成
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    // 客户催单
    @Override
    public void reminder(Long id) {
        // 根据 id 查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if(ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap<>();
        map.put("type",2); // 1 -> 来单提醒   2 -> 客户催单
        map.put("orderId",id);
        map.put("content","订单号" + ordersDB.getNumber());
        String json = JSON.toJSONString(map);

        // 通过 websocket 向客户端浏览器推送消息
        webSocketServer.sendToAllClient(json);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page){
        // 需要返回订单商品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        // 对分页数据的每一个进行 VO 封装
        List<Orders> ordersList = page.getResult();
        if(!CollectionUtils.isEmpty(ordersList)){ // 如果结果不为空
            for (Orders orders : ordersList) {
                OrderVO orderVO = new OrderVO();
                // 将公共字段拷贝到 VO
                BeanUtils.copyProperties(orders,orderVO);
                // 由于 orders 中没有商品信息，因此需要查询商品信息再额外赋值
                String orderDishes = getOrderDishes(orders);
                orderVO.setOrderDishes(orderDishes);
                // 添加进集合中准备返回
                orderVOList.add((orderVO));
            }
        }
        return orderVOList;
    }

    private String getOrderDishes(Orders orders){
        // 查询订单商品详情（商品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单的商品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + "；";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有商品信息拼接在一起
        return String.join("",orderDishList);
    }

}
