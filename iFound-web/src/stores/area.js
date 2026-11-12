import { defineStore } from 'pinia'
import { iotRequest } from '@/utils/request'

export const useAreaStore = defineStore('area', {
  state: () => ({
    areaList: [],
    loading: false
  }),
  getters: {
    areaMap: (state) => {
      const map = {}
      state.areaList.forEach(item => {
        map[item.id] = item
      })
      return map
    }
  },
  actions: {
    async fetchAreaList() {
      if (this.areaList.length > 0) return this.areaList
      this.loading = true
      try {
        const res = await iotRequest.get('/subarea/list', {
          params: { org_id: 3 }
        })
        if (res.Status === 'Ok' && res.data) {
          this.areaList = res.data
        }
      } catch (err) {
        console.error('获取区域列表失败:', err)
      } finally {
        this.loading = false
      }
      return this.areaList
    }
  },
  persist: {
    key: 'area-store',
    storage: localStorage,
    paths: ['areaList']
  }
})
