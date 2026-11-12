import { createRouter, createWebHashHistory } from 'vue-router'
import Dashboard from '../pages/Dashboard.vue'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: Dashboard
  },
  {
    // 无后端演示副本：全部接口数据由 mockDashboardAdapter 提供假数据
    path: '/dashboard1',
    name: 'dashboard1',
    component: () => import('../pages/Dashboard1.vue')
  },
  {
    path: '/operations',
    name: 'Operations',
    component: () => import('../pages/Operations.vue')
  },
  {
    // 设备运维Agent 演示副本：配合 /dashboard1 使用，全部数据为本地假数据
    path: '/operations1',
    name: 'Operations1',
    component: () => import('../pages/Operations1.vue')
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('../pages/ConfigLayout.vue'),
    redirect: '/config/voice',
    children: [
      {
        path: 'zone',
        name: 'ZoneConfig',
        component: () => import('../pages/config/ZoneConfig.vue')
      },
      {
        path: 'voice',
        name: 'VoiceConfig',
        component: () => import('../pages/config/VoiceConfig.vue')
      },
      {
        path: 'agent',
        name: 'AgentConfig',
        component: () => import('../pages/config/AgentConfig.vue')
      },
      {
        path: 'task',
        name: 'TaskList',
        component: () => import('../pages/config/TaskList.vue')
      },
      {
        path: 'tts-records',
        name: 'TtsRecords',
        component: () => import('../pages/config/TtsRecords.vue')
      },
      {
        path: 'stream-chat-records',
        name: 'StreamChatRecords',
        component: () => import('../pages/config/StreamChatRecords.vue')
      },
      {
        path: 'diagnosis',
        name: 'DiagnosisRecords',
        component: () => import('../pages/config/DiagnosisRecords.vue')
      }
    ]
  },
  // Legacy redirects for backward compatibility
  {
    path: '/zone-admin',
    redirect: '/config/zone'
  },
  {
    path: '/agent-admin',
    redirect: '/config/agent'
  },
  {
    path: '/task-admin',
    redirect: '/config/task'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
