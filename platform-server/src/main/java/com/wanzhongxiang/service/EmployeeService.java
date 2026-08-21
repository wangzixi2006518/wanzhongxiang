package com.wanzhongxiang.service;

import com.wanzhongxiang.dto.EmployeeDTO;
import com.wanzhongxiang.dto.EmployeeLoginDTO;
import com.wanzhongxiang.dto.EmployeePageQueryDTO;
import com.wanzhongxiang.entity.Employee;
import com.wanzhongxiang.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    // 新增员工
    void save(EmployeeDTO employeeDTO);

    // 分页查询
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    // 启用禁用员工账号
    void startOrStop(Integer status, Long id);

    // 根据 id 查询员工信息
    Employee getById(Long id);

    // 编辑员工信息
    void update(EmployeeDTO employeeDTO);
}
