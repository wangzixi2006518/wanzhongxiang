import request from '@/utils/request'
// 修改密码
export const editPassword = (data: any) =>
  request({
    'url': '/employee/editPassword',
    'method': 'put',
    data
  })
  // 获取服务状态
  export const getStatus = () =>
  request({
    'url': `/merchant/status`,
    'method': 'get'
  })
    // 设置服务状态
    export const setStatus = (data:any) =>
    request({
      'url': `/merchant/`+data,
      'method': 'put',
      'data':data
    })
