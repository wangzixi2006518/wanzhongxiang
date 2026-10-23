<template>
  <section class="business-comparison">
    <h2>经营区间对比</h2>
    <p class="comparison-note">
      统计已完成订单，按下单时间归属。两段均为完整历史日期，各1～31天、天数相同，基准区间早于当前区间。
    </p>
    <el-form :inline="true" class="comparison-form">
      <el-form-item label="当前区间">
        <el-date-picker
          v-model="currentRange"
          type="daterange"
          value-format="yyyy-MM-dd"
          range-separator="至"
          start-placeholder="当前开始日期"
          end-placeholder="当前结束日期"
          :disabled="loading"
        />
      </el-form-item>
      <el-form-item label="基准区间">
        <el-date-picker
          v-model="baselineRange"
          type="daterange"
          value-format="yyyy-MM-dd"
          range-separator="至"
          start-placeholder="基准开始日期"
          end-placeholder="基准结束日期"
          :disabled="loading"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="compare">
          查询对比
        </el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-if="result" class="comparison-result">
      <p class="comparison-periods">
        当前：{{ result.currentBegin }} 至 {{ result.currentEnd }}；
        基准：{{ result.baselineBegin }} 至 {{ result.baselineEnd }}
      </p>
      <el-table :data="metrics" border>
        <el-table-column prop="name" label="指标" />
        <el-table-column prop="current" label="当前区间" />
        <el-table-column prop="baseline" label="基准区间" />
      </el-table>
      <div class="comparison-changes">
        <span>营业额差额：<strong>{{ money(result.turnoverDifference) }} 元</strong></span>
        <span>营业额变化率：<strong>{{ changeRate }}</strong></span>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import moment from 'moment'
import { BusinessComparison, getBusinessComparison } from '@/api/businessComparison'

@Component({ name: 'BusinessComparison' })
export default class extends Vue {
  private currentRange: string[] | null = []
  private baselineRange: string[] | null = []
  private loading = false
  private error = ''
  private result: BusinessComparison | null = null

  created() {
    const today = moment().utcOffset(8).startOf('day')
    this.currentRange = [today.clone().subtract(7, 'days').format('YYYY-MM-DD'), today.clone().subtract(1, 'days').format('YYYY-MM-DD')]
    this.baselineRange = [today.clone().subtract(14, 'days').format('YYYY-MM-DD'), today.clone().subtract(8, 'days').format('YYYY-MM-DD')]
    this.compare()
  }

  private money(value: number): string {
    return Number(value).toFixed(2)
  }

  private get metrics() {
    if (!this.result) return []
    return [
      { name: '已完成订单数', current: String(this.result.currentValidOrderCount), baseline: String(this.result.baselineValidOrderCount) },
      { name: '营业额（元）', current: this.money(this.result.currentTurnover), baseline: this.money(this.result.baselineTurnover) },
      { name: '平均客单价（元）', current: this.money(this.result.currentUnitPrice), baseline: this.money(this.result.baselineUnitPrice) }
    ]
  }

  private get changeRate(): string {
    if (!this.result || this.result.turnoverChangeRate === null) return '无可比基数'
    return (Number(this.result.turnoverChangeRate) * 100).toFixed(2) + '%'
  }

  private async compare() {
    if (this.loading) return
    this.result = null
    this.error = ''
    if (!this.currentRange || this.currentRange.length !== 2 || !this.baselineRange || this.baselineRange.length !== 2) {
      this.error = '请选择完整的当前区间和基准区间'
      return
    }
    this.loading = true
    try {
      const response = await getBusinessComparison({
        currentBegin: this.currentRange[0],
currentEnd: this.currentRange[1],
        baselineBegin: this.baselineRange[0],
baselineEnd: this.baselineRange[1]
      })
      if (response.data.code !== 1) throw new Error(response.data.msg || '经营对比查询失败')
      this.result = response.data.data
    } catch (error) {
      const body = error && error.response && error.response.data
      this.error = (body && body.msg) || (error && error.message) || '经营对比查询失败，请稍后重试'
    } finally {
      this.loading = false
    }
  }
}
</script>

<style scoped lang="scss">
.business-comparison {
  margin: 20px 0;
  padding: 24px;
  border-radius: 8px;
  background: #fff;
  h2 { margin: 0 0 12px; font-size: 20px; }
}
.comparison-note, .comparison-periods { color: #606266; line-height: 1.7; }
.comparison-form { margin-top: 20px; }
.comparison-result { margin-top: 18px; }
.comparison-changes {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-top: 20px;
  strong { font-size: 18px; }
}
</style>
