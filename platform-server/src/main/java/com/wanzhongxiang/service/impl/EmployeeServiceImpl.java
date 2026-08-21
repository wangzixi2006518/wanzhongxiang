package com.wanzhongxiang.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.constant.PasswordConstant;
import com.wanzhongxiang.constant.StatusConstant;
import com.wanzhongxiang.context.BaseContext;
import com.wanzhongxiang.dto.EmployeeDTO;
import com.wanzhongxiang.dto.EmployeeLoginDTO;
import com.wanzhongxiang.dto.EmployeePageQueryDTO;
import com.wanzhongxiang.entity.Employee;
import com.wanzhongxiang.exception.AccountLockedException;
import com.wanzhongxiang.exception.AccountNotFoundException;
import com.wanzhongxiang.exception.PasswordErrorException;
import com.wanzhongxiang.mapper.EmployeeMapper;
import com.wanzhongxiang.result.PageResult;
import com.wanzhongxiang.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        // 对前端传过来的明文密码进行 MD5 加密处理
        password = DigestUtils.md5DigestAsHex(password.getBytes()); // MD5 加密处理
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    // 新增员工
    @Override
    public void save(EmployeeDTO employeeDTO) {
        System.err.println("当前线程 id：" + Thread.currentThread().getId());
        Employee employee = new Employee();

        // employee 将没有涉及到的数据再进行一遍封装，同时拿到 employeeDTO 内的数据，插入数据库
//        employee.setName(employeeDTO.getName()); // 繁琐
        BeanUtils.copyProperties(employeeDTO,employee); // 对象属性拷贝简化代码，顺序：(拷谁的，拷到谁)，属性名要一致

        // 设置账号状态，默认正常，1 -> 正常   0 -> 锁定
        employee.setStatus(StatusConstant.ENABLE); // 1

        // 设置密码，默认密码 123456
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes())); // 对 123456 进行 MD5 加密

//        // 设置当前记录的创建时间和修改时间
//        employee.setCreateTime(LocalDateTime.now());
//        employee.setUpdateTime(LocalDateTime.now());
//
//        // 设置当前记录创建人 id 和修改人 id
//        employee.setCreateUser(BaseContext.getCurrentId()); // 从线程的存储空间中取出拦截器存的 id
//        employee.setUpdateUser(BaseContext.getCurrentId());

        // 插入数据
        employeeMapper.insert(employee);

    }

    // 分页查询
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // select * from employee limit 0,10
        // 开始分页查询 参数：(页码, 每页记录数)
        PageHelper.startPage(employeePageQueryDTO.getPage(),employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        // 将 page 转为 pageResult 返回
        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total,records); // PageResult 的有参构造方法
    }

    // 启用禁用员工账号
    @Override
    public void startOrStop(Integer status, Long id) {
        // update employee set status = #{status} where id = #{id}

//        Employee employee = new Employee();
//        employee.setStatus(status);
//        employee.setId(id);
        // 用构建器设置构建器对象属性
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee); // 传实体类更合适
    }

    // 根据 id 查询员工信息
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("****"); // 不显示加密后的密码
        return employee;
    }

    // 编辑员工信息
    @Override
    public void update(EmployeeDTO employeeDTO) {
        // update 要 employee 数据，需要把 employeeDTO 转换成 employee
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO,employee); // 属性拷贝

//        // 补全其他属性
//        employee.setUpdateTime(LocalDateTime.now());
//        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.update(employee);
    }

}
