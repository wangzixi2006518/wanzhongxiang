<template>
  <div class="login">
    <div class="login-box">
      <div class="login-visual">
        <div class="visual-mark">
          万
        </div>
        <p class="visual-title">
          万众享平台
        </p>
        <p class="visual-subtitle">
          综合交易与商业服务运营中心
        </p>
        <div class="visual-tags">
          <span>商品管理</span>
          <span>交易订单</span>
          <span>数据洞察</span>
        </div>
      </div>
      <div class="login-form">
        <el-form ref="loginForm" :model="loginForm" :rules="loginRules">
          <div class="login-form-title">
            <span class="title-label">欢迎进入运营中心</span>
          </div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="账号"
              prefix-icon="iconfont icon-user"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="iconfont icon-lock"
              @keyup.enter.native="handleLogin"
            />
          </el-form-item>
          <el-form-item style="width: 100%">
            <el-button
              :loading="loading"
              class="login-btn"
              size="medium"
              type="primary"
              style="width: 100%"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import { Route } from 'vue-router'
import { Form as ElForm, Input } from 'element-ui'
import { UserModule } from '@/store/modules/user'
import { isValidUsername } from '@/utils/validate'

@Component({
  name: 'Login',
})
export default class extends Vue {
  private validateUsername = (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入用户名'))
    } else {
      callback()
    }
  }
  private validatePassword = (rule: any, value: string, callback: Function) => {
    if (value.length < 6) {
      callback(new Error('密码必须在6位以上'))
    } else {
      callback()
    }
  }
  private loginForm = {
    username: 'admin',
    password: '123456',
  } as {
    username: String
    password: String
  }

  loginRules = {
    username: [{ validator: this.validateUsername, trigger: 'blur' }],
    password: [{ validator: this.validatePassword, trigger: 'blur' }],
  }
  private loading = false
  private redirect?: string

  @Watch('$route', { immediate: true })
  private onRouteChange(route: Route) {}

  // 登录
  private handleLogin() {
    (this.$refs.loginForm as ElForm).validate(async (valid: boolean) => {
      if (valid) {
        this.loading = true
        await UserModule.Login(this.loginForm as any)
          .then((res: any) => {
            if (String(res.code) === '1') {
              this.$router.push('/')
            } else {
              // this.$message.error(res.msg)
              this.loading = false
            }
          })
          .catch(() => {
            // this.$message.error('用户名或密码错误！')
            this.loading = false
          })
      } else {
        return false
      }
    })
  }
}
</script>

<style lang="scss">
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background: #0f172a;
}

.login-box {
  width: min(960px, 92vw);
  min-height: 520px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(2, 8, 23, 0.32);
  display: flex;
}

.login-visual {
  width: 56%;
  padding: 72px 64px;
  color: #f8fafc;
  background:
    linear-gradient(145deg, rgba(20, 184, 166, 0.2), transparent 52%),
    #123044;
  display: flex;
  flex-direction: column;
  justify-content: center;
  .visual-mark {
    width: 58px;
    height: 58px;
    border: 1px solid #5eead4;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #5eead4;
    font-size: 30px;
    font-weight: 700;
  }
  .visual-title {
    margin: 28px 0 10px;
    font-size: 34px;
    font-weight: 700;
  }
  .visual-subtitle {
    margin: 0;
    color: #cbd5e1;
    font-size: 15px;
  }
  .visual-tags {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 48px;
    span {
      border: 1px solid rgba(204, 251, 241, 0.35);
      border-radius: 999px;
      padding: 8px 13px;
      color: #ccfbf1;
      font-size: 12px;
    }
  }
}

.title {
  margin: 0px auto 10px auto;
  text-align: left;
  color: #707070;
}

.login-form {
  background: #ffffff;
  width: 44%;
  border-radius: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  .el-form {
    width: 214px;
    height: 307px;
  }
  .el-form-item {
    margin-bottom: 30px;
  }
  .el-form-item.is-error .el-input__inner {
    border: 0 !important;
    border-bottom: 1px solid #fd7065 !important;
    background: #fff !important;
  }
  .input-icon {
    height: 32px;
    width: 18px;
    margin-left: -2px;
  }
  .el-input__inner {
    border: 0;
    border-bottom: 1px solid #e9e9e8;
    border-radius: 0;
    font-size: 12px;
    font-weight: 400;
    color: #333333;
    height: 32px;
    line-height: 32px;
  }
  .el-input__prefix {
    left: 0;
  }
  .el-input--prefix .el-input__inner {
    padding-left: 26px;
  }
  .el-input__inner::placeholder {
    color: #aeb5c4;
  }
  .el-form-item--medium .el-form-item__content {
    line-height: 32px;
  }
  .el-input--medium .el-input__icon {
    line-height: 32px;
  }
}

.login-btn {
  border-radius: 17px;
  padding: 11px 20px !important;
  margin-top: 10px;
  font-weight: 500;
  font-size: 12px;
  border: 0;
  font-weight: 500;
  color: #333333;
  // background: #09a57a;
  background-color: #0f766e;
  &:hover,
  &:focus {
    background-color: #115e59;
    color: #ffffff;
  }
}
.login-form-title {
  height: 36px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 40px;
  .title-label {
    font-weight: 500;
    font-size: 19px;
    color: #172033;
  }
}

@media (max-width: 720px) {
  .login-box {
    min-height: 0;
  }
  .login-visual {
    display: none;
  }
  .login-form {
    width: 100%;
    min-height: 460px;
    border-radius: 12px;
  }
}
</style>
