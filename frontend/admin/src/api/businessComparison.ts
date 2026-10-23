import request from '@/utils/request'

export interface BusinessComparisonDates {
  currentBegin: string
  currentEnd: string
  baselineBegin: string
  baselineEnd: string
}

export interface BusinessComparison extends BusinessComparisonDates {
  currentValidOrderCount: number
  baselineValidOrderCount: number
  currentTurnover: number
  baselineTurnover: number
  currentUnitPrice: number
  baselineUnitPrice: number
  turnoverDifference: number
  turnoverChangeRate: number | null
}

export function getBusinessComparison(params: BusinessComparisonDates) {
  return request({ url: '/report/businessComparison', method: 'get', params })
}
