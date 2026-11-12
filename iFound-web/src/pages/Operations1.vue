<template>
  <div class="operations-container">
    <Toast
      v-if="toastConfig.show"
      :type="toastConfig.type"
      :title="toastConfig.title"
      :description="toastConfig.description"
      @close="toastConfig.show = false"
    />
    <!-- Head层 -->
    <header class="head">
      <div class="head-title">
        <span>设备运维Agent</span>
      </div>
      <button class="page-close-btn" @click="goBack" aria-label="返回首页">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </button>
    </header>

    <!-- Head-KPI层 -->
    <section class="head-kpi">
      <div class="kpi-card">
        <div class="kpi-icon red"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 01-3.46 0"/></svg></div>
        <div class="kpi-content">
          <span class="kpi-label">设备累计告警</span>
          <span class="kpi-value">{{ tunnelStats.totalCount.toLocaleString() }} <small>次</small></span>
        </div>
          <div class="kpi-text-stats" style="flex-direction:row">
            <div class="kpi-text-item fault">
              <span class="kpi-text-label">紧急</span>
              <span class="kpi-text-value">{{ tunnelStats.faultCount }} <small>次</small></span>
            </div>
            <div class="kpi-text-item alarm">
              <span class="kpi-text-label">重要</span>
              <span class="kpi-text-value">{{ tunnelStats.alarmCount }} <small>次</small></span>
            </div>
          </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon orange"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg></div>
        <div class="kpi-content">
          <span class="kpi-label">今日新增报警</span>
          <span class="kpi-value">{{ todayAlarmStats.totalCount }} <small>次</small></span>
          <span class="kpi-trend" :class="todayAlarmStats.totalTrend <= 0 ? 'green' : 'red'">
            较昨日 {{ todayAlarmStats.totalTrend <= 0 ? '↓' : '↑' }} {{ Math.abs(todayAlarmStats.totalTrend) }}%
          </span>
        </div>
        <div class="kpi-chart" ref="aiTrendChartRef"></div>
      </div>
      
      <div class="kpi-card">
        <div class="kpi-icon green"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22,12 18,12 15,21 9,3 6,12 2,12"/></svg></div>
        <div class="kpi-content">
          <span class="kpi-label">设备健康率</span>
          <span class="kpi-value">{{ healthRate.value }}% </span>
        </div>
        <div class="kpi-chart" ref="healthChartRef"></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon blue"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
          <path d="M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0"/>
          <path d="M12 6v2M12 16v2M6 12h2M16 12h2"/>
          <path d="M9 9l1.5 1.5M13.5 13.5L15 15M9 15l1.5-1.5M13.5 10.5L15 9"/>
        </svg></div>
        <div class="kpi-content">
          <span class="kpi-label">累计AI处理</span>
          <span class="kpi-value">{{ kpiStats.totalDiagnosis + kpiStats.totalDispatch }} <small>次</small></span>
        </div>
          <div class="kpi-text-stats">
            <div class="kpi-text-row">
              <div class="kpi-text-item diagnosis">
                <span class="kpi-text-label">AI诊断</span>
                <span class="kpi-text-value">{{ kpiStats.totalDiagnosis }} <small>次</small></span>
              </div>
              <div class="kpi-text-item dispatch">
                <span class="kpi-text-label">AI派单</span>
                <span class="kpi-text-value">{{ kpiStats.totalDispatch }} <small>次</small></span>
              </div>
            </div>
            <div class="kpi-text-row rate-row">
              <span class="kpi-text-label">采纳率</span>
              <span class="kpi-text-value">{{ totalAdoptionRate }}<small>%</small></span>
            </div>
          </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon green"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
          <path d="M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0"/>
          <path d="M12 6v2M12 16v2M6 12h2M16 12h2"/>
          <path d="M9 9l1.5 1.5M13.5 13.5L15 15M9 15l1.5-1.5M13.5 10.5L15 9"/>
        </svg></div>
        <div class="kpi-content">
          <span class="kpi-label">今日AI处理</span>
          <span class="kpi-value">{{ kpiStats.todayDiagnosis + kpiStats.todayDispatch }} <small>次</small></span>
        </div>
          <div class="kpi-text-stats">
            <div class="kpi-text-row">
              <div class="kpi-text-item diagnosis">
                <span class="kpi-text-label">AI诊断</span>
                <span class="kpi-text-value">{{ kpiStats.todayDiagnosis }} <small>次</small></span>
              </div>
              <div class="kpi-text-item dispatch">
                <span class="kpi-text-label">AI派单</span>
                <span class="kpi-text-value">{{ kpiStats.todayDispatch }} <small>次</small></span>
              </div>
            </div>
            <div class="kpi-text-row rate-row">
              <span class="kpi-text-label">采纳率</span>
              <span class="kpi-text-value">{{ todayAdoptionRate }}<small>%</small></span>
            </div>
          </div>
      </div>
    </section>
    <!-- Bottom层 -->
    <footer class="bottom">
      <!-- 资产状态统计 -->
      <div class="bottom-card">
        <div class="card-header">
          <svg class="card-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a10 10 0 100 20 10 10 0 000-20z"/><path d="M12 6v6l4 2"/></svg>
          <span>资产状态统计</span>
        </div>
        <div class="device-stats">
          <div class="chart-placeholder sankey-chart" ref="deviceSankeyRef"></div>
        </div>
      </div>
      <!-- AI诊断总览 -->
      <div class="bottom-card">
        <div class="card-header">
          <svg class="panel-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
            <path d="M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0"/>
            <path d="M12 6v2M12 16v2M6 12h2M16 12h2"/>
            <path d="M9 9l1.5 1.5M13.5 13.5L15 15M9 15l1.5-1.5M13.5 10.5L15 9"/>
          </svg>
          <span>AI诊断趋势</span>
        </div>
        <div class="card-charts">
          <div class="chart-box full-width">
            <div class="chart-title"></div>
            <div class="chart-loading" v-if="diagnosisTrendLoading">
              <div class="thinking-dots"><span></span><span></span><span></span></div>
              <span class="loading-text">加载趋势数据中...</span>
            </div>
            <div class="chart-placeholder" ref="diagnosisTrendRef" v-show="!diagnosisTrendLoading"></div>
          </div>
        </div>
      </div>


      <!-- 设备历史告警信息 -->
      <div class="bottom-card">
        <div class="card-header">
          <svg class="card-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14,2 14,8 20,8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          <span>维修工单统计</span>
          <button class="more-btn" :class="{ 'flash-animation': isOrderBtnFlashing }" @click="showOrderMore">工单列表</button>
        </div>
        <div class="order-chart-container" ref="orderChartRef"></div>
      </div>
    </footer>

    <!-- Ops-Main层 -->
    <main class="ops-main">
      <!-- 左侧：告警设备列表 -->
      <div class="alarm-panel">
        <div class="panel-header">
          <svg class="panel-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/></svg>
          <div class="panel-view-tabs">
            <span :class="['panel-view-tab', { active: viewMode === 'alarm' }]" @click="switchViewMode('alarm')">告警设备</span>
<!--            <span :class="['panel-view-tab', { active: viewMode === 'issue' }]" @click="switchViewMode('issue')">待处理问题</span>-->
            <span :class="['panel-view-tab', { active: viewMode === 'pendingTask' }]" @click="switchViewMode('pendingTask')">协作记录</span>
          </div>
        </div>
        <div v-if="viewMode === 'alarm'" class="alarm-view-wrapper">
          <div class="alarm-tabs">
          <span :class="['tab', { active: selectedAlarmTab === 'danger' }]" @click="switchTab('danger')"><span style="color: red;">紧急</span> <b>{{ dangerCount }}</b></span>
          <span :class="['tab', { active: selectedAlarmTab === 'warning' }]" @click="switchTab('warning')"><span style="color: orange;">重要</span> <b>{{ warningCount }}</b></span>
          <select class="area-select" v-model="selectedAreaId" @change="onAreaChange">
            <option value="">全部区域</option>
            <option v-for="area in areaStore.areaList" :key="area.id" :value="area.id">{{ area.name }}</option>
          </select>
        </div>
        <div class="alarm-table">
          <div class="table-header device-header">
            <span>设备名称</span>
            <span>告警描述</span>
            <span>告警时间</span>
            <span>状态</span>
            <span>等级</span>
            <span>AI诊断</span>
          </div>
          <div class="table-body">
            <div class="table-row" v-for="(item, i) in filteredAlarmList" :key="i">
              <span class="device-name" :title="item.device_name"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>{{ item.device_name }}</span>
              <span class="event-info" :title="item.type">{{ item.type }}</span>
              <span :title="item.happen_time">{{ item.happen_time }}</span>
              <span :title="item.status"><span :class="`status ${item.statusClass}`">{{ item.status }}</span></span>
              <span :title="item.severity"><span :class="`severity ${item.severityClass}`">{{ item.severity }}</span></span>
              <span class="ai-diagnosis"><span class="ai-orb small" @click="fetchDeviceData(item)">分析</span></span>
            </div>
          </div>
        </div>
        <div class="table-footer">
          <span>共 {{ totalCount }} 条</span>
          <div class="pagination">
            <button @click="changePage(currentPage - 1)" :disabled="currentPage <= 1">&lt;</button>
            <template v-for="(p, idx) in visiblePages" :key="idx">
              <span v-if="p === '...'" class="pagination-ellipsis">…</span>
              <button v-else :class="{ active: p === currentPage }" @click="changePage(p)">{{ p }}</button>
            </template>
            <button @click="changePage(currentPage + 1)" :disabled="currentPage >= totalPages">&gt;</button>
          </div>
          <span>前往 <input type="text" :value="currentPage" @keyup.enter="goToPage($event)" /> 页</span>
        </div>
        </div>
        <!-- 待处理问题列表 -->
        <div v-else-if="viewMode === 'issue'" class="alarm-view-wrapper">
          <div class="alarm-table">
            <div class="table-header issue-header">
              <span>问题</span>
              <span>位置</span>
<!--              <span>问题描述</span>-->
              <span>发现时间</span>
              <span>状态</span>
              <span>等级</span>
              <span>AI诊断</span>
            </div>
            <div class="table-body">
              <div class="table-row issue-header" v-for="(item, i) in issueList" :key="i">
<span class="device-name" :title="item.issueTitle"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>{{ item.issueTitle }}</span>
<span :title="item.location">{{ item.location }}</span>
<!--                <span class="event-info" :title="item.issueContent">{{ item.issueContent }}</span>-->
<span :title="item.foundTime">{{ item.foundTime }}</span>
<span :title="item.status"><span :class="`status ${item.statusClass}`">{{ item.status }}</span></span>
<span :title="issueLevelText(item.issueLevel)"><span :class="`severity ${item.issueLevelClass}`">{{ issueLevelText(item.issueLevel) }}</span></span>
                <span class="ai-diagnosis"><span class="ai-orb small" @click="handleIssueDiagnosis(item)">AI</span></span>
              </div>
              <div class="empty-hint" v-if="issueList.length === 0">暂无待处理问题</div>
            </div>
          </div>
          <div class="table-footer">
            <span>共 {{ issueTotalCount }} 条</span>
            <div class="pagination">
              <button @click="changePage(currentPage - 1)" :disabled="currentPage <= 1">&lt;</button>
              <template v-for="(p, idx) in visiblePages" :key="idx">
              <span v-if="p === '...'" class="pagination-ellipsis">…</span>
              <button v-else :class="{ active: p === currentPage }" @click="changePage(p)">{{ p }}</button>
            </template>
              <button @click="changePage(currentPage + 1)" :disabled="currentPage >= totalPages">&gt;</button>
            </div>
            <span>前往 <input type="text" :value="currentPage" @keyup.enter="goToPage($event)" /> 页</span>
          </div>
        </div>
        <!-- 运维Agent待处理任务列表 -->
        <div v-else class="alarm-view-wrapper">
          <div class="alarm-tabs">
            <input class="task-search-input" type="text" placeholder="搜索任务标题..." v-model="pendingTaskSearch" @blur="onPendingTaskFilterChange" />
            <select class="area-select" v-model="pendingTaskAreaId" @change="onPendingTaskFilterChange">
              <option value="">全部区域</option>
              <option v-for="area in areaStore.areaList" :key="area.id" :value="area.id">{{ area.name }}</option>
            </select>

            <select class="area-select" v-model="pendingTaskStatus" @change="onPendingTaskFilterChange">
              <option value="0">未完成</option>
              <option value="1">完成</option>
            </select>
          </div>
          <div class="alarm-table">
            <div class="table-header pending-task-header">
              <span>任务标题</span>
              <span>业务域</span>
              <span>问题描述</span>
              <span>创建时间</span>
              <span>等级</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div class="table-body">
              <div class="table-row pending-task-row" v-for="(item, i) in pendingTaskList" :key="i">
                <span class="device-name" :title="item.decisionContent || '-'"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>{{ item.decisionContent || '-' }}</span>
                <span :title="item.businessDomain || '-'">{{ item.businessDomain || '-' }}</span>
                <span class="event-info" :title="item.problemDesc">{{ item.problemDesc || '-' }}</span>
                <span :title="item.createTime || '-'">{{ item.createTime || '-' }}</span>
                <span :title="item.levelText"><span :class="`severity ${item.levelClass}`">{{ item.levelText }}</span></span>
                <span :title="item.resultExecuteStatus === '1' ? '已完成' : '未完成'"><span :class="`status ${item.resultExecuteStatus === '1' ? '' : 'warning'}`">{{ item.resultExecuteStatus === '1' ? '已完成' : '未完成' }}</span></span>
                <span class="ai-diagnosis">
                  <span class="ai-orb small" @click="handlePendingTaskAction(item)">{{ item.resultExecuteStatus === '1' ? '查看' : '派发' }}</span>
                  <span v-if="item.resultExecuteStatus !== '1'" class="ai-orb small feedback-btn" @click="openFeedbackModal(item)">回填</span>
                </span>
              </div>
              <div class="empty-hint" v-if="pendingTaskList.length === 0">暂无待处理任务</div>
            </div>
          </div>
          <div class="table-footer">
            <span>共 {{ pendingTaskTotalCount }} 条</span>
            <div class="pagination">
              <button @click="changePage(currentPage - 1)" :disabled="currentPage <= 1">&lt;</button>
              <template v-for="(p, idx) in visiblePages" :key="idx">
              <span v-if="p === '...'" class="pagination-ellipsis">…</span>
              <button v-else :class="{ active: p === currentPage }" @click="changePage(p)">{{ p }}</button>
            </template>
              <button @click="changePage(currentPage + 1)" :disabled="currentPage >= totalPages">&gt;</button>
            </div>
            <span>前往 <input type="text" :value="currentPage" @keyup.enter="goToPage($event)" /> 页</span>
          </div>
        </div>
      </div>

      <!-- 右侧：AI诊断与派单 -->
      <div class="diagnosis-panel">
        <div class="panel-header">
          <!-- <svg class="panel-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a10 10 0 100 20 10 10 0 000-20z"/><path d="M12 6v6l4 2"/></svg> -->
                    <svg class="panel-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
            <path d="M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0"/>
            <path d="M12 6v2M12 16v2M6 12h2M16 12h2"/>
            <path d="M9 9l1.5 1.5M13.5 13.5L15 15M9 15l1.5-1.5M13.5 10.5L15 9"/>
          </svg>
          <span>AI诊断与派单</span>
            <div class="update-time">05-16 10:15:30</div>
            <button class="zoom-btn" @click="showDiagnosisZoom" aria-label="放大显示">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
              </svg>
            </button>
        </div>
        <div class="diagnosis-content">
          <div class="device-overview">
            <div class="overview-title">设备概况</div>
            <div class="device-3d">
              <div class="device-3d-top">
                <div class="model-placeholder" :class="{ scanning: isScanning }">
                  <svg viewBox="0 0 120 100" width="120" height="100" fill="none" stroke="currentColor" stroke-width="1.5">
                    <ellipse cx="60" cy="70" rx="40" ry="15"/>
                    <rect x="35" y="30" width="50" height="40" rx="4"/>
                    <circle cx="60" cy="50" r="12"/>
                    <path d="M45 30V20h30v10"/>
                    <line x1="60" y1="20" x2="60" y2="10"/>
                    <circle cx="60" cy="8" r="3"/>
                  </svg>
                </div>
                <div class="device-info">
                  <h4>{{ selectedDevice.device_name || '未选择设备' }}</h4>
                  <p>区域：{{ selectedDevice.subarea_name || '' }}</p>
                  <p>{{ selectedDevice.location? '位置：' + selectedDevice.location : '' }}</p>

                </div>
                  <div class="device-status-row">
                    <span class="device-severity">
                      <span class="meta-label">等级：</span>
                      <span :class="`severity-badge ${selectedDevice.severityClass}`">{{ selectedDevice.severity || '-' }}</span>
                    </span>
                    <span class="device-status">
                      <span class="meta-label">设备状态：</span>
                      <span :class="`status-badge`">{{ selectedDevice.status || '-' }}</span>
                    </span>
                  </div>
              </div>
              <div class="device-alert-info">
                <div class="device-meta">
                  <span class="meta-label">告警描述：</span>
                  <span class="meta-value">{{ selectedDevice.type || '-' }}</span>
                </div>
                <div class="device-meta">
                  <span class="meta-label">告警时间：</span>
                  <span class="meta-value">{{ selectedDevice.happen_time || '-' }}</span>
                </div>
              </div>
            </div>
            <div class="health-bar">
              <span>设备健康度</span>
              <div class="bar-track"><div class="bar-fill" :style="{ width: healthScore + '%' }"></div></div>
              <span class="health-value" :class="healthScore >= 80 ? 'green-text' : healthScore >= 60 ? 'warning-text' : 'danger-text'">{{ healthScore }}%</span>
            </div>
            <div class="realtime-data">
              <div class="data-item" v-for="(item, i) in realtimeData" :key="i">
                <div class="data-label">{{ item.name }}</div>
                <div class="data-value large" :class="item.state === 1 && item.eventLevel >= 3 ? 'danger' : item.state === 1 && item.eventLevel >= 2 ? 'warning' : ''">{{ item.value }}</div>
                <!-- <div v-if="item.eventTypeName" class="data-status" :class="item.eventLevel >= 3 ? 'danger' : item.eventLevel >= 2 ? 'warning' : ''">{{ item.eventTypeName }}</div> -->
              </div>
              <div class="empty-hint" v-if="realtimeData.length === 0">无实时数据</div>
            </div>
          </div>

          <div class="diagnosis-result" v-if="isScanning">
            <div class="result-title">AI 智能诊断分析</div>
            <div class="thinking-loader">
              <div class="thinking-dots">
                <span></span><span></span><span></span>
              </div>
              <p class="thinking-text">AI 正在分析诊断中...</p>
            </div>
          </div>
          <div class="diagnosis-result" v-else-if="diagnosisResult">
            <div class="result-title">
              <span>AI 智能诊断分析</span>
              <span class="confidence-badge">置信度：{{ diagnosisResult['置信度'] }}%</span>
            </div>
            <div class="result-section">
              <h5>诊断结论</h5>
              <div class="conclusion-item">
                <!-- <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg> -->
                <span><b>{{ diagnosisResult['AI诊断结论']?.['结论'] || '-' }}</b></span>
              </div>
              <p class="conclusion-desc">{{ diagnosisResult['AI诊断结论']?.['补充说明'] || '-' }}</p>
            </div>
            <div class="result-section">
              <h5>根因分析</h5>
              <div class="cause-item danger">
                <!-- <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> -->
                <span><b>{{ diagnosisResult['根因分析']?.['主要原因'] || '-' }}</b></span>
              </div>
              <p class="cause-desc">{{ diagnosisResult['根因分析']?.['分析说明'] || '-' }}</p>
            </div>
            <div class="result-section">
              <h5>维修建议</h5>
              <ul class="suggestion-list">
                <li v-for="(s, i) in (diagnosisResult['维修建议'] || [])" :key="i">{{ s }}</li>
              </ul>
            </div>
          </div>
          <div class="diagnosis-result" v-else>
            <div class="result-title">AI 智能诊断分析</div>
            <div class="empty-hint">上报设备后获取AI智能诊断分析</div>
          </div>

          <div class="dispatch-info" v-if="isScanning">
            <div class="dispatch-title">派单信息</div>
            <div class="thinking-loader">
              <div class="thinking-dots">
                <span></span><span></span><span></span>
              </div>
              <p class="thinking-text">AI 正在生成派单方案...</p>
            </div>
          </div>
          <div class="dispatch-info" v-else-if="dispatchResult">
            <div class="dispatch-title">派单信息</div>
            <div class="dispatch-row" v-if="orderInfo">
              <span class="label">工单标题</span>
              <span class="order-title">{{ orderInfo.orderTitle || '-' }}</span>
            </div>
            <div class="dispatch-row" v-if="orderInfo">
              <span class="label">工单编号</span>
              <span class="order-no">{{ orderInfo.orderNo || '-' }}</span>
            </div>
            <div class="dispatch-row" v-if="orderInfo">
              <span class="label">工单状态</span>
              <span class="order-status-badge">{{ orderInfo.status }}</span>
            </div>
            <div class="dispatch-row">
              <span class="label">指派维修负责人</span>
              <div class="engineer">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                <div>
                  <b>{{ dispatchResult['维修负责人姓名'] || '-' }}</b>
                </div>
                <button class="switch-engineer-btn" @click="openEngineerModal" v-if="!dispatched">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 014-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>
                </button>
              </div>
            </div>
            <div class="dispatch-row" v-if="matchedEngineerSkill">
              <span class="label">技能描述</span>
              <span class="skill-desc">{{ matchedEngineerSkill }}</span>
            </div>
            <div class="dispatch-row">
              <span class="label">所需工具</span>
              <span class="tools-list">{{ dispatchResult['所需工具'] || '-' }}</span>
            </div>
            <div class="dispatch-row">
              <span class="label">优先级</span>
              <span class="priority-badge" :class="dispatchResult['优先级'] === 'HIGH' ? 'danger' : dispatchResult['优先级'] === 'MEDIUM' ? 'warning' : ''">{{ priorityText(dispatchResult['优先级']) }}</span>
            </div>
            <div class="dispatch-row">
              <span class="label">预计到达时间</span>
              <div style="display: flex;flex-flow: row nowrap;">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>
                <span>{{ dispatchResult['预计到达时间'] || '-' }}</span>
              </div>
            </div>
            <div class="dispatch-row">
              <span class="label">预计完成时间</span>
              <div style="display: flex;flex-flow: row nowrap;">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>
                <span>{{ dispatchResult['预计完成时间'] || '-' }}</span>
              </div>
            </div>
            <button class="dispatch-btn" @click="handleDispatch" v-if="!dispatched">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22,2 15,22 11,13 2,9"/></svg>
              <div>
                <b>一键派单</b>
                <small>立即派单并通知维修负责人</small>
              </div>
            </button>
            <div class="dispatched-hint" v-else>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              <span>已派单</span>
            </div>
          </div>
          <div class="dispatch-info" v-else>
            <div class="dispatch-title">派单信息</div>
            <div class="empty-hint">上报设备后获取派单信息</div>
          </div>
        </div>
      </div>
    </main>

    <!-- AI诊断与派单放大弹窗 -->
    <div class="diagnosis-zoom-overlay" v-if="showDiagnosisModal" @click.self="closeDiagnosisZoom">
      <div class="diagnosis-zoom-content">
        <div class="diagnosis-zoom-inner">
          <div class="diagnosis-panel zoomed">
            <div class="panel-header">
              <svg class="panel-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
                <path d="M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0"/>
                <path d="M12 6v2M12 16v2M6 12h2M16 12h2"/>
                <path d="M9 9l1.5 1.5M13.5 13.5L15 15M9 15l1.5-1.5M13.5 10.5L15 9"/>
              </svg>
              <span>AI诊断与派单</span>
              <div class="update-time">05-16 10:15:30</div>
              <button class="zoom-close" @click="closeDiagnosisZoom">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <div class="diagnosis-content">
              <div class="device-overview">
                <div class="overview-title">设备概况</div>
                <div class="device-3d">
                  <div class="device-3d-top">
                    <div class="model-placeholder" :class="{ scanning: isScanning }">
                      <svg viewBox="0 0 120 100" width="120" height="100" fill="none" stroke="currentColor" stroke-width="1.5">
                        <ellipse cx="60" cy="70" rx="40" ry="15"/>
                        <rect x="35" y="30" width="50" height="40" rx="4"/>
                        <circle cx="60" cy="50" r="12"/>
                        <path d="M45 30V20h30v10"/>
                        <line x1="60" y1="20" x2="60" y2="10"/>
                        <circle cx="60" cy="8" r="3"/>
                      </svg>
                    </div>
                    <div class="device-info">
                      <h4>{{ selectedDevice.device_name || '未选择设备' }}</h4>
                      <p>区域：{{ selectedDevice.subarea_name || '' }}</p>
                      <p>{{ selectedDevice.location? '位置：' + selectedDevice.location : '' }}</p>
                    </div>
                    <div class="device-status-row">
                      <span class="device-severity">
                        <span :class="`severity-badge ${selectedDevice.severityClass}`">{{ selectedDevice.severity || '-' }}</span>
                      </span>
                      <span class="device-status">
                        <span class="meta-label">设备状态：</span>
                        <span :class="`status-badge`">{{ selectedDevice.status || '-' }}</span>
                      </span>
                    </div>
                  </div>
                  <div class="device-alert-info">
                    <div class="device-meta">
                      <span class="meta-label">告警描述：</span>
                      <span class="meta-value">{{ selectedDevice.type || '-' }}</span>
                    </div>
                    <div class="device-meta">
                      <span class="meta-label">告警时间：</span>
                      <span class="meta-value">{{ selectedDevice.happen_time || '-' }}</span>
                    </div>
                  </div>
                </div>
                <div class="health-bar">
                  <span>设备健康度</span>
                  <div class="bar-track"><div class="bar-fill" :style="{ width: healthScore + '%' }"></div></div>
                  <span class="health-value" :class="healthScore >= 80 ? 'green-text' : healthScore >= 60 ? 'warning-text' : 'danger-text'">{{ healthScore }}%</span>
                </div>
                <div class="realtime-data">
                  <div class="data-item" v-for="(item, i) in realtimeData" :key="i">
                    <div class="data-label">{{ item.name }}</div>
                    <div class="data-value large" :class="item.state === 1 && item.eventLevel >= 3 ? 'danger' : item.state === 1 && item.eventLevel >= 2 ? 'warning' : ''">{{ item.value }}</div>
                  </div>
                  <div class="empty-hint" v-if="realtimeData.length === 0">无实时数据</div>
                </div>
              </div>

              <div class="diagnosis-result" v-if="isScanning">
                <div class="result-title">AI 智能诊断分析</div>
                <div class="thinking-loader">
                  <div class="thinking-dots">
                    <span></span><span></span><span></span>
                  </div>
                  <p class="thinking-text">AI 正在分析诊断中...</p>
                </div>
              </div>
              <div class="diagnosis-result" v-else-if="diagnosisResult">
                <div class="result-title">
                  <span>AI 智能诊断分析</span>
                  <span class="confidence-badge">置信度：{{ diagnosisResult['置信度'] }}%</span>
                </div>
                <div class="result-section">
                  <h5>诊断结论</h5>
                  <div class="conclusion-item">
                    <span><b>{{ diagnosisResult['AI诊断结论']?.['结论'] || '-' }}</b></span>
                  </div>
                  <p class="conclusion-desc">{{ diagnosisResult['AI诊断结论']?.['补充说明'] || '-' }}</p>
                </div>
                <div class="result-section">
                  <h5>根因分析</h5>
                  <div class="cause-item danger">
                    <span><b>{{ diagnosisResult['根因分析']?.['主要原因'] || '-' }}</b></span>
                  </div>
                  <p class="cause-desc">{{ diagnosisResult['根因分析']?.['分析说明'] || '-' }}</p>
                </div>
                <div class="result-section">
                  <h5>维修建议</h5>
                  <ul class="suggestion-list">
                    <li v-for="(s, i) in (diagnosisResult['维修建议'] || [])" :key="i">{{ s }}</li>
                  </ul>
                </div>
              </div>
              <div class="diagnosis-result" v-else>
                <div class="result-title">AI 智能诊断分析</div>
                <div class="empty-hint">上报设备后获取AI智能诊断分析</div>
              </div>

              <div class="dispatch-info" v-if="isScanning">
                <div class="dispatch-title">派单信息</div>
                <div class="thinking-loader">
                  <div class="thinking-dots">
                    <span></span><span></span><span></span>
                  </div>
                  <p class="thinking-text">AI 正在生成派单方案...</p>
                </div>
              </div>
              <div class="dispatch-info" v-else-if="dispatchResult">
                <div class="dispatch-title">派单信息</div>
                <div class="dispatch-row" v-if="orderInfo">
                  <span class="label">工单标题</span>
                  <span class="order-title">{{ orderInfo.orderTitle || '-' }}</span>
                </div>
                <div class="dispatch-row" v-if="orderInfo">
                  <span class="label">工单编号</span>
                  <span class="order-no">{{ orderInfo.orderNo || '-' }}</span>
                </div>
                <div class="dispatch-row" v-if="orderInfo">
                  <span class="label">工单状态</span>
                  <span class="order-status-badge">{{ orderInfo.status }}</span>
                </div>
                <div class="dispatch-row">
                  <span class="label">指派维修负责人</span>
                  <div class="engineer">
                    <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                    <div>
                      <b>{{ dispatchResult['维修负责人姓名'] || '-' }}</b>
                    </div>
                  </div>
                </div>
                <div class="dispatch-row" v-if="matchedEngineerSkill">
                  <span class="label">技能描述</span>
                  <span class="skill-desc">{{ matchedEngineerSkill }}</span>
                </div>
                <div class="dispatch-row">
                  <span class="label">所需工具</span>
                  <span class="tools-list">{{ dispatchResult['所需工具'] || '-' }}</span>
                </div>
                <div class="dispatch-row">
                  <span class="label">优先级</span>
                  <span class="priority-badge" :class="dispatchResult['优先级'] === 'HIGH' ? 'danger' : dispatchResult['优先级'] === 'MEDIUM' ? 'warning' : ''">{{ priorityText(dispatchResult['优先级']) }}</span>
                </div>
                <div class="dispatch-row">
                  <span class="label">预计到达时间</span>
                  <div style="display: flex;flex-flow: row nowrap;">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>
                    <span>{{ dispatchResult['预计到达时间'] || '-' }}</span>
                  </div>
                </div>
                <div class="dispatch-row">
                  <span class="label">预计完成时间</span>
                  <div style="display: flex;flex-flow: row nowrap;">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/></svg>
                    <span>{{ dispatchResult['预计完成时间'] || '-' }}</span>
                  </div>
                </div>
                <button class="dispatch-btn" @click="handleDispatch" v-if="!dispatched">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22,2 15,22 11,13 2,9"/></svg>
                  <div>
                    <b>一键派单</b>
                    <small>立即派单并通知维修负责人</small>
                  </div>
                </button>
                <div class="dispatched-hint" v-else>
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                  <span>已派单</span>
                </div>
              </div>
              <div class="dispatch-info" v-else>
                <div class="dispatch-title">派单信息</div>
                <div class="empty-hint">上报设备后获取派单信息</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>


    <!-- 全屏iframe弹窗 -->
    <div class="iframe-overlay" v-if="showIframeModal">
      <div class="iframe-container">
        <button class="iframe-close" @click="closeIframeModal">&times;</button>
        <iframe :src="iframeUrl" frameborder="0"></iframe>
      </div>
    </div>

    <!-- 工单详情弹窗 -->
    <div class="order-detail-overlay" v-if="showOrderDetail" @click.self="closeOrderDetail">
      <div class="order-detail-modal">
        <div class="order-detail-header">
          <span>工单详情</span>
          <button class="modal-close" @click="closeOrderDetail">&times;</button>
        </div>
        <div class="order-detail-body">
          <div class="detail-row">
            <span class="detail-label">工单标题</span>
            <span class="detail-value">{{ currentOrder.orderTitle || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">工单编号</span>
            <span class="detail-value">{{ currentOrder.orderNo || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">资产名称</span>
            <span class="detail-value">{{ currentOrder.assetName || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">问题描述</span>
            <span class="detail-value">{{ currentOrder.issueContent || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ currentOrder.createTime || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">执行人</span>
            <span class="detail-value">{{ currentOrder.executeUserName || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <span :class="['detail-value', 'order-status', 'status-' + currentOrder.status]">{{ orderStatusText(currentOrder.status) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 工单轨迹弹窗 -->
    <ProgressFlowDialog
      :visible="showOrderProgressModal"
      :progress="orderProgressData"
      :resultDesc="orderProgressResultDesc"
      @close="closeOrderProgressModal"
    />

    <!-- 用户回填弹窗 -->
    <div class="feedback-overlay" v-if="showFeedbackModal" @click.self="closeFeedbackModal">
      <div class="feedback-modal">
        <div class="feedback-header">
          <span>处理结果回填</span>
          <button class="modal-close" @click="closeFeedbackModal">&times;</button>
        </div>
        <div class="feedback-body">
          <div class="feedback-row">
            <label class="feedback-label">处理结果</label>
            <select class="feedback-select" v-model="feedbackForm.resultExecuteStatus">
              <option value="1">处理完成</option>
              <option value="2">处理失败</option>
            </select>
          </div>
          <div class="feedback-row">
            <label class="feedback-label">反馈内容</label>
            <textarea class="feedback-textarea" v-model="feedbackForm.userFeedback" placeholder="请输入反馈内容..." rows="4"></textarea>
          </div>
        </div>
        <div class="feedback-footer">
          <button class="feedback-btn-cancel" @click="closeFeedbackModal">取消</button>
          <button class="feedback-btn-confirm" @click="submitFeedback" :disabled="feedbackSubmitting">{{ feedbackSubmitting ? '提交中...' : '确认' }}</button>
        </div>
      </div>
    </div>

    <!-- 维修负责人选择弹窗 -->
    <div class="engineer-modal" v-if="isEngineerModalOpen" @click.self="closeEngineerModal">
      <div class="modal-content">
        <div class="modal-header">
          <span>选择维修负责人</span>
          <button class="modal-close" @click="closeEngineerModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="engineer-list">
            <div class="engineer-item" v-for="(eng, i) in engineerList" :key="i" @click="selectEngineer(eng)" :class="{ selected: selectedEngineer.userId === eng.userId }">
              <div class="engineer-avatar">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              </div>
              <div class="engineer-details">
                <div class="engineer-name">{{ eng.name }}</div>
                <div class="engineer-skill">{{ eng.skillsDesc || '-' }}</div>
                <div class="engineer-contact">
                  <span>📱 {{ eng.mobile || '-' }}</span>
                  <span>✉️ {{ eng.email || '-' }}</span>
                </div>
              </div>
              <div class="engineer-check" v-if="selectedEngineer.id === eng.id">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#10b981" stroke-width="3"><polyline points="20,6 9,17 4,12"/></svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ============================================================
// Operations1：无后端环境演示副本（访问路径 /operations1）
// 与 Operations.vue（设备运维Agent）页面结构完全一致，所有接口
// 数据由 src/utils/mockDashboardAdapter.js 提供本地假数据。
// 作为 /dashboard1 页面的跳转目标；返回键回到 /dashboard1。
// ============================================================
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { iotRequest, aiRequest,posRequest  } from '@/utils/request'
import { listDeviceStatePage } from '@/service/deviceApi'
import { useAreaStore } from '@/stores/area'
import { useUserStore } from '@/stores/user'
import { installDashboardMock } from '@/utils/mockDashboardAdapter'
import Toast from '@/components/Toast.vue'
import ProgressFlowDialog from '@/components/ProgressFlowDialog.vue'

// Toast 状态
const toastConfig = ref({ show: false, type: 'info', title: '', description: '' })
function showToast(type, title, description = '') {
  toastConfig.value = { show: true, type, title, description }
}

const router = useRouter()
const route = useRoute()
const areaStore = useAreaStore()
const userStore = useUserStore()

// 无后端演示模式：拦截全部请求返回假数据，卸载页面时恢复
const uninstallDashboardMock = installDashboardMock()

function goBack() {
  router.push('/dashboard1')
}

const diagnosisTrendRef = ref(null)
const diagnosisTrendLoading = ref(false)
const alarmTypeRef = ref(null)
const alarmTrendRef = ref(null)
const tunnelStatsChartRef = ref(null)
const todayAlarmChartRef = ref(null)
const healthChartRef = ref(null)
const aiTrendChartRef = ref(null)

let diagnosisTrendChart = null
let alarmTypeChart = null
let alarmTrendChart = null
let tunnelStatsChart = null
let todayAlarmChart = null
let healthChart = null
let aiTrendChart = null
let deviceSankeyChart = null

const deviceSankeyRef = ref(null)

const selectedAlarmTab = ref('danger')
const viewMode = ref('pendingTask') // 'alarm' | 'issue' | pendingTask

const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)

const alarmList = ref([])
const dangerListCount = ref(0)
const warningListCount = ref(0)
const selectedAreaId = ref('')

// 待处理问题
const issueList = ref([])
const issueTotalCount = ref(0)

// 运维Agent待处理任务
const pendingTaskList = ref([])
const pendingTaskTotalCount = ref(0)
const pendingTaskStatus = ref('0')   // '0'-未完成, '1'-完成
const pendingTaskSearch = ref('')    // decisionContent 模糊搜索
const pendingTaskAreaId = ref('')    // 区域选择（存 area.id）

const tunnelStats = ref({ totalCount: 0, faultCount: 0, alarmCount: 0 })
const todayAlarmStats = ref({ faultCount: 0, alarmCount: 0, yesterdayFault: 0, yesterdayAlarm: 0, faultTrend: 0, alarmTrend: 0 })
const healthRate = ref({ value: 100})

async function fetchDeviceCount() {
  try {
    const res = await listDeviceStatePage({
      page: 1,
      limit: 1,
      org_id: userStore.org_id
    })
    if (res.Status === 'Ok' && res.count !== undefined) {
      const totalDevices = res.count
      const alarmCount = dangerListCount.value + warningListCount.value
      if (totalDevices > 0 && alarmCount > 0) {
        healthRate.value.value = Number(((1 - alarmCount / totalDevices) * 100).toFixed(1))
      }
    }
  } catch (err) {
    console.error('获取设备总数失败:', err)
  }
}

async function fetchTunnelStats() {
  try {
    const res = await iotRequest.get('/tunnel/statistics/getTunnelsFaultCount')
    if (res.Status === 'Ok' && res.data) {
      let totalFault = 0
      let totalAlarm = 0
      Object.values(res.data).forEach(item => {
        totalFault += item.fault_count || 0
        totalAlarm += item.alarm_count || 0
      })
      tunnelStats.value = {
        totalCount: totalFault + totalAlarm,
        faultCount: totalFault,
        alarmCount: totalAlarm
      }
      nextTick(() => {
        initTunnelStatsChart()
      })
    }
  } catch (err) {
    console.error('获取隧道故障统计失败:', err)
  }
}

async function fetchTodayAlarmStats() {
  const today = new Date()
  const todayStr = today.toISOString().slice(0, 10).replace(/-/g, '')
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayStr = yesterday.toISOString().slice(0, 10).replace(/-/g, '')

  try {
    const [todayRes, yesterdayRes] = await Promise.all([
      iotRequest.get('/tunnel/statistics/getTunnelsFaultCount', { params: { startDate: todayStr,endDate:todayStr } }),
      iotRequest.get('/tunnel/statistics/getTunnelsFaultCount', { params: { startDate: yesterdayStr, endDate: yesterdayStr } })
    ])

    let todayFault = 0, todayAlarm = 0, yesterdayFault = 0, yesterdayAlarm = 0

    if (todayRes.Status === 'Ok' && todayRes.data) {
      Object.values(todayRes.data).forEach(item => {
        todayFault += item.fault_count || 0
        todayAlarm += item.alarm_count || 0
      })
    }

    if (yesterdayRes.Status === 'Ok' && yesterdayRes.data) {
      Object.values(yesterdayRes.data).forEach(item => {
        yesterdayFault += item.fault_count || 0
        yesterdayAlarm += item.alarm_count || 0
      })
    }

    const faultTrend = yesterdayFault > 0 ? ((todayFault - yesterdayFault) / yesterdayFault * 100).toFixed(0) : 0
    const alarmTrend = yesterdayAlarm > 0 ? ((todayAlarm - yesterdayAlarm) / yesterdayAlarm * 100).toFixed(0) : 0
    const todayTotal = todayFault + todayAlarm
    const yesterdayTotal = yesterdayFault + yesterdayAlarm
    const totalTrend = yesterdayTotal > 0 ? ((todayTotal - yesterdayTotal) / yesterdayTotal * 100).toFixed(0) : 0

    todayAlarmStats.value = {
      faultCount: todayFault,
      alarmCount: todayAlarm,
      totalCount: todayTotal,
      yesterdayFault,
      yesterdayAlarm,
      yesterdayTotal,
      faultTrend: Number(faultTrend),
      alarmTrend: Number(alarmTrend),
      totalTrend: Number(totalTrend)
    }
    nextTick(() => {
      initTodayAlarmChart()
      initAiTrendChart()
    })
  } catch (err) {
    console.error('获取今日告警统计失败:', err)
  }
}

function initTodayAlarmChart() {
  if (!todayAlarmChartRef.value) return
  todayAlarmChart = echarts.init(todayAlarmChartRef.value)
  todayAlarmChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 10, top: 15, bottom: 20 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['昨日', '今日'],
      axisLine: { lineStyle: { color: '#2a3a4a' } },
      axisLabel: { color: 'white', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1a2a3a' } },
      axisLabel: { color: 'white', fontSize: 10 }
    },
    series: [
      {
        name: '告警数',
        type: 'line',
        data: [todayAlarmStats.value.yesterdayAlarm, todayAlarmStats.value.alarmCount],
        smooth: true,
        lineStyle: { color: '#ef4444' },
        itemStyle: { color: '#ef4444' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(239,68,68,0.3)' }, { offset: 1, color: 'rgba(239,68,68,0)' }]
          }
        }
      },
      {
        name: '故障数',
        type: 'line',
        data: [todayAlarmStats.value.yesterdayFault, todayAlarmStats.value.faultCount],
        smooth: true,
        lineStyle: { color: '#f59e0b' },
        itemStyle: { color: '#f59e0b' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(245,158,11,0.3)' }, { offset: 1, color: 'rgba(245,158,11,0)' }]
          }
        }
      }
    ]
  })
}

function initAiTrendChart() {
  if (!aiTrendChartRef.value) return
  aiTrendChart = echarts.init(aiTrendChartRef.value)
  aiTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        const p = params[0]
        return `${p.name} 报警数: <b>${p.value}</b>`
      }
    },
    grid: { left: 0, right: 0, top: 10, bottom: 0 },
    xAxis: {
      type: 'category',
      show: false,
      data: ['昨日', '今日']
    },
    yAxis: {
      type: 'value',
      show: false
    },
    series: [
      {
        type: 'line',
        data: [todayAlarmStats.value.yesterdayTotal, todayAlarmStats.value.totalCount],
        smooth: true,
        showSymbol: false,
        lineStyle: { color: '#58D9F9', width: 2 },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(88,217,249,0.3)' }, { offset: 1, color: 'rgba(88,217,249,0)' }]
          }
        }
      }
    ]
  })
}

function initHealthChart() {
  if (!healthChartRef.value) return
  healthChart = echarts.init(healthChartRef.value)
  healthChart.setOption({
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        center: ['50%', '75%'],
        radius: '90%',
        min: 0,
        max: 100,
        splitNumber: 8,
        axisLine: {
          lineStyle: {
            width: 6,
            color: [
              [0.25, '#FF6E76'],
              [0.5, '#FDDD60'],
              [0.75, '#58D9F9'],
              [1, '#7CFFB2']
            ]
          }
        },
        pointer: {
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '12%',
          width: 20,
          offsetCenter: [0, '-60%'],
          itemStyle: {
            color: 'auto'
          }
        },
        axisTick: {
          show: false
        },
        splitLine: {
          show: false
        },
        axisLabel: {
          show: false
        },
        title: {
          show: false
        },
        detail: {
          fontSize: 28,
          offsetCenter: [0, '0%'],
          valueAnimation: true,
          formatter: function (value) {
            let level = '差'
            if (value >= 75) level = '优'
            else if (value >= 50) level = '良'
            else if (value >= 25) level = '中'
            return level
          },
          color: '#fff'
        },
        data: [
          {
            value: healthRate.value.value,
            name: ''
          }
        ]
      }
    ]
  })
}

async function initDeviceSankeyChart() {
  if (!deviceSankeyRef.value) return

  // 获取机电设备总数、故障数、报警数
  let deviceTotal = 0, faultCount = 0, alarmCount = 0
  try {
    const res = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id })
    if (res.Status === 'Ok') deviceTotal = res.count || 0
  } catch (e) { console.error('获取设备总数失败:', e) }

  try {
    const faultRes = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id, fault_state: 1 })
    if (faultRes.Status === 'Ok') faultCount = faultRes.count || 0
  } catch (e) {}

  try {
    const alarmRes = await listDeviceStatePage({ page: 1, limit: 1, org_id: userStore.org_id, alarm_state: 1 })
    if (alarmRes.Status === 'Ok') alarmCount = alarmRes.count || 0
  } catch (e) {}

  const normalCount = Math.max(0, deviceTotal - faultCount - alarmCount)

  // 获取资产总数（各状态）
  let assetTotal = 0
  const usageCounts = {}
  const statusList = [10, 20, 30, 40, 50, 60]
  const statusNames = { 10: '备用', 20: '在用', 30: '闲置', 40: '停用', 50: '维修', 60: '报废' }

  try {
    const totalRes = await posRequest.get('/asset-info', { params: { current: 1, page: 1, limit: 1 } })
    if (totalRes.Status === 'Ok') assetTotal = totalRes.count || 0
  } catch (e) {}

  for (const s of statusList) {
    try {
      const res = await posRequest.get('/asset-info', { params: { current: 1, page: 1, limit: 1, usageStatuses: s } })
      usageCounts[s] = res.count || 0
    } catch { usageCounts[s] = 0 }
  }

  // 设备台账 = 各使用状态数量之和
  const otherAssetCount = Object.values(usageCounts).reduce((sum, v) => sum + v, 0)

  // 构建桑基图数据
  const nodes = [
    { name: `资产总数(${assetTotal})`, itemStyle: { color: '#6366f1' } },
    { name: `机电设备(${deviceTotal})`, itemStyle: { color: '#3b82f6' } },
    { name: `设备台账(${otherAssetCount})`, itemStyle: { color: '#8b5cf6' } },
    { name: `正常(${normalCount})`, itemStyle: { color: '#10b981' } },
    { name: `故障(${faultCount})`, itemStyle: { color: '#ef4444' } },
    { name: `报警(${alarmCount})`, itemStyle: { color: '#f59e0b' } }
  ]

  const links = [
    { source: `资产总数(${assetTotal})`, target: `机电设备(${deviceTotal})`, value: deviceTotal },
    { source: `资产总数(${assetTotal})`, target: `设备台账(${otherAssetCount})`, value: otherAssetCount },
    { source: `机电设备(${deviceTotal})`, target: `正常(${normalCount})`, value: normalCount },
    { source: `机电设备(${deviceTotal})`, target: `故障(${faultCount})`, value: faultCount },
    { source: `机电设备(${deviceTotal})`, target: `报警(${alarmCount})`, value: alarmCount }
  ]

  // 只有当设备台账数量大于0时，才显示使用状态分支
  if (otherAssetCount > 0) {
    nodes.push(
      { name: `在用(${usageCounts[20]})`, itemStyle: { color: '#10b981' } },
      { name: `备用(${usageCounts[10]})`, itemStyle: { color: '#3b82f6' } },
      { name: `维修(${usageCounts[50]})`, itemStyle: { color: '#f59e0b' } },
      { name: `闲置(${usageCounts[30]})`, itemStyle: { color: '#6b7280' } },
      { name: `停用(${usageCounts[40]})`, itemStyle: { color: '#ef4444' } },
      { name: `报废(${usageCounts[60]})`, itemStyle: { color: '#374151' } }
    )

    links.push(
      { source: `设备台账(${otherAssetCount})`, target: `在用(${usageCounts[20]})`, value: usageCounts[20] },
      { source: `设备台账(${otherAssetCount})`, target: `备用(${usageCounts[10]})`, value: usageCounts[10] },
      { source: `设备台账(${otherAssetCount})`, target: `维修(${usageCounts[50]})`, value: usageCounts[50] },
      { source: `设备台账(${otherAssetCount})`, target: `闲置(${usageCounts[30]})`, value: usageCounts[30] },
      { source: `设备台账(${otherAssetCount})`, target: `停用(${usageCounts[40]})`, value: usageCounts[40] },
      { source: `设备台账(${otherAssetCount})`, target: `报废(${usageCounts[60]})`, value: usageCounts[60] }
    )
  }

  if (!deviceSankeyChart) {
    deviceSankeyChart = echarts.init(deviceSankeyRef.value)
  }

  deviceSankeyChart.setOption({
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey',
      layout: 'none',
      emphasis: { focus: 'adjacency' },
      nodeWidth: 20,
      nodeGap: 12,
      label: { color: '#c0d0e0', fontSize: 10 },
      lineStyle: { color: 'gradient', opacity: 0.3 },
      data: nodes,
      links
    }]
  })
}

function onAreaChange() {
  currentPage.value = 1
  fetchAlarmList()
}

const realtimeData = ref([])
const healthScore = ref(100)
const selectedDevice = ref({ id: '', device_name: '', location: '', subarea_cid: '', subarea_name: '', type: '', happen_time: '', raw_happen_time: '', severity: '', severityClass: '', status: '', statusClass: '' })

const engineerList = ref([])
const selectedEngineer = ref({})
const isEngineerModalOpen = ref(false)

const isScanning = ref(false)
const diagnosisResult = ref(null)
const dispatchResult = ref(null)
const orderId = ref('')
const dispatched = ref(false)
const orderInfo = ref(null)
const diagnosisRecordId = ref('')
const diagnosisSourceType = ref('ALARM')
const diagnosisSourceData = ref(null)
const diagnosisDispatchTaskId = ref('')

async function fetchOrderInfo(id) {
  if (!id) return
  try {
    const res = await posRequest.get(`/repair/orders/${id}`)
    if (res.Status === 'Ok' && res.data) {
      const statusMap = { 10: '未派发', 20: '已派发', 30: '处理中', 40: '已完成', 50: '已关闭' }
      orderInfo.value = {
        orderTitle: res.data.orderTitle || '',
        orderNo: res.data.orderNo || '',
        status: statusMap[res.data.status] || res.data.status || '-'
      }
    }
  } catch (err) {
    console.error('获取工单信息失败:', err)
  }
}

// 维修工单列表
const orderChartRef = ref(null)
let orderChart = null

const orderStatusData = ref({
  '待派单': 0,
  '待确认': 0,
  '处理中': 0,
  '待验收': 0,
  '已完成': 0
})

async function fetchOrderStatusChart() {
  try {
    const statusMap = {
      '待派单': 10,
      '待确认': 20,
      '处理中': 30,
      '待验收': 40,
      '已完成': 50
    }
    
    const promises = Object.entries(statusMap).map(async ([name, status]) => {
      const res = await posRequest.get('/repair/orders', {
        params: { size: 1, current: 1, status }
      })
      return { name, count: res.count || 0 }
    })
    
    const results = await Promise.all(promises)
    results.forEach(({ name, count }) => {
      orderStatusData.value[name] = count
    })
    
    renderOrderChart()
  } catch (e) {
    console.error('获取工单状态统计失败:', e)
  }
}

function renderOrderChart() {
  if (!orderChartRef.value) return
  
  if (!orderChart) {
    orderChart = echarts.init(orderChartRef.value)
  }
  
  const names = Object.keys(orderStatusData.value)
  const counts = Object.values(orderStatusData.value)
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: '{b}: {c} 单'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        color: '#8b9dc3',
        fontSize: 11
      },
      axisLine: {
        lineStyle: { color: 'rgba(255,255,255,0.1)' }
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#8b9dc3',
        fontSize: 11
      },
      splitLine: {
        lineStyle: { color: 'rgba(255,255,255,0.05)' }
      },
      axisLine: {
        lineStyle: { color: 'rgba(255,255,255,0.1)' }
      }
    },
    series: [{
      type: 'bar',
      data: counts.map((value, index) => ({
        value,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de'][index] },
            { offset: 1, color: ['#3a4f8a', '#6a9c58', '#c9a040', '#c04040', '#5090b0'][index] }
          ])
        }
      })),
      barWidth: '50%',
      label: {
        show: true,
        position: 'top',
        color: '#fff',
        fontSize: 12,
        formatter: '{c}'
      }
    }]
  }
  
  orderChart.setOption(option)
}

function orderStatusText(status) {
  const map = { 10: '待派单', 20: '待确认', 30: '处理中', 40: '待验收', 50: '已完成' }
  return map[status] || status || '-'
}

const showIframeModal = ref(false)
const iframeUrl = ref('')

function showOrderMore() {
  iframeUrl.value = '/#/ifim/public/workOrder'
  showIframeModal.value = true
}

function closeIframeModal() {
  showIframeModal.value = false
  iframeUrl.value = ''
}

// 工单详情弹窗
const showOrderDetail = ref(false)
const currentOrder = ref({})
const isOrderBtnFlashing = ref(false)
const showDiagnosisModal = ref(false)

// 工单轨迹弹窗
const showOrderProgressModal = ref(false)
const orderProgressData = ref(null)
const orderProgressResultDesc = ref('')

// 用户回填弹窗
const showFeedbackModal = ref(false)
const feedbackSubmitting = ref(false)
const feedbackForm = ref({ id: '', resultExecuteStatus: '1', userFeedback: '' })

function showDiagnosisZoom() {
  showDiagnosisModal.value = true
}

function closeDiagnosisZoom() {
  showDiagnosisModal.value = false
}

function flashOrderButton() {
  isOrderBtnFlashing.value = true
  setTimeout(() => {
    isOrderBtnFlashing.value = false
  }, 1500) // 3次闪烁，每次500ms
}

function openOrderDetail(order) {
  currentOrder.value = order
  // showOrderDetail.value = true
}

function closeOrderDetail() {
  showOrderDetail.value = false
  currentOrder.value = {}
}

// KPI 统计数据
const kpiStats = ref({
  totalDiagnosis: 0,
  totalDispatch: 0,
  todayDiagnosis: 0,
  todayDispatch: 0
})

function getTodayStart() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} 00:00:00`
}

async function fetchKpiStats() {
  try {
    // 累计诊断次数
    const diagTotalRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK' }
    })
    kpiStats.value.totalDiagnosis = diagTotalRes.count || 0

    // 累计派单次数
    const dispatchTotalRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO',status:'20,30,40,50' }
    })
    kpiStats.value.totalDispatch = dispatchTotalRes.count || 0

    // 今日诊断次数
    const todayStart = getTodayStart()
    const diagTodayRes = await posRequest.get('/diagnosis', {
      params: { size: 1, current: 1, source: 'ONE_CLICK', createTimeBegin: todayStart }
    })
    kpiStats.value.todayDiagnosis = diagTodayRes.count || 0

    // 今日派单次数
    const dispatchTodayRes = await posRequest.get('/repair/orders', {
      params: { size: 1, current: 1, dispatchType: 'AUTO',status:'20,30,40,50', dispatchTimeBegin: todayStart }
    })
    kpiStats.value.todayDispatch = dispatchTodayRes.count || 0
  } catch (err) {
    console.error('获取KPI统计数据失败:', err)
  }
}

// 计算属性
const totalAdoptionRate = computed(() => {
  if (kpiStats.value.totalDiagnosis === 0) return 0
  return Math.round((kpiStats.value.totalDispatch / kpiStats.value.totalDiagnosis) * 100)
})

const todayAdoptionRate = computed(() => {
  if (kpiStats.value.todayDiagnosis === 0) return 0
  return Math.round((kpiStats.value.todayDispatch / kpiStats.value.todayDiagnosis) * 100)
})

async function fetchEngineerList() {
  try {
    const res = await posRequest.get('/repair/staff')
    if (res.Status === 'Ok' && res.data) {
      engineerList.value = res.data
      if (res.data.length > 0 && !selectedEngineer.value.id) {
        selectedEngineer.value = res.data[0]
      }
    }
  } catch (err) {
    console.error('获取维修负责人列表失败:', err)
  }
}

function openEngineerModal() {
  // 打开弹窗时，同步当前派单信息中的维修负责人
  if (dispatchResult.value && dispatchResult.value['维修负责人id']) {
    const current = engineerList.value.find(e => e.userId === dispatchResult.value['维修负责人id'])
    if (current) selectedEngineer.value = current
  }
  isEngineerModalOpen.value = true
}

function closeEngineerModal() {
  isEngineerModalOpen.value = false
}

async function selectEngineer(eng) {
  selectedEngineer.value = eng
  isEngineerModalOpen.value = false
  // 同步更新派单信息中的维修负责人
  if (dispatchResult.value) {
    dispatchResult.value = {
      ...dispatchResult.value,
      '维修负责人id': eng.userId,
      '维修负责人姓名': eng.name
    }
  }
  // 有工单时切换维修负责人，调用接口更新
  if (orderId.value) {
    try {
      const res = await posRequest.put(`/repair/orders/${orderId.value}/action/update-staff`, {
        executeOrgCid: userStore.org_cid,
        executeUserId: eng.userId,
        executeUserName: eng.name
      })
      if (res.Status === 'Ok') {
        showToast('success', '维修负责人已更新')
      } else {
        showToast('error', '更新失败', res.msg || '未知错误')
      }
    } catch (err) {
      console.error('更新维修负责人失败:', err)
      showToast('error', '更新维修负责人失败')
    }
  }
}

const filteredAlarmList = computed(() => {
  return alarmList.value
})

const matchedEngineerSkill = computed(() => {
  if (!dispatchResult.value) return ''
  const engId = dispatchResult.value['维修负责人id']
  if (!engId) return ''
  const eng = engineerList.value.find(e => e.userId === engId)
  return eng?.skillsDesc || ''
})

const dangerCount = computed(() => dangerListCount.value)
const warningCount = computed(() => warningListCount.value)

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))

const visiblePages = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  if (cur <= 5) {
    return [1, 2, 3, 4, 5, '...', total]
  }
  if (cur >= total - 1) {
    return [1, '...', total - 2, total - 1, total]
  }
  return [1, '...', cur - 1, cur, cur + 1, '...', total]
})

function formatHappenTime(timeStr) {
  if (timeStr == null) return ''
  timeStr = timeStr.toString()
  console.log("formatHappenTime",typeof timeStr)
  if (!timeStr || timeStr.length < 14) return timeStr
  const year = timeStr.substring(0, 4)
  const month = timeStr.substring(4, 6)
  const day = timeStr.substring(6, 8)
  const hour = timeStr.substring(8, 10)
  const minute = timeStr.substring(10, 12)
  const second = timeStr.substring(12, 14)
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function getStatusText(fault, alarm) {
  if (fault > 0) return '故障'
  if (alarm > 0) return '告警'
  return '正常'
}

function getStatusClass(fault, alarm) {
  if (fault > 0) return 'danger'
  if (alarm > 0) return 'warning'
  return 'normal'
}

async function fetchAlarmList() {
  const isDanger = selectedAlarmTab.value === 'danger'
  const stateParam = isDanger ? 'fault_state' : 'alarm_state'
  const otherStateParam = isDanger ? 'alarm_state' : 'fault_state'
  
  const params = {
    page: currentPage.value,
    limit: pageSize.value,
    org_id: userStore.org_id,
    [stateParam]: 1,
    addChangeStateTime: 1
  }
  
  if (selectedAreaId.value) {
    const area = areaStore.areaMap[selectedAreaId.value]
    if (area) {
      params.subarea_cid = area.cascade_id
    }
  }
  
  const otherParams = {
    page: 1,
    limit: 1,
    org_id: userStore.org_id,
    [otherStateParam]: 1,
    addChangeStateTime: 1
  }
  
  if (selectedAreaId.value) {
    const area = areaStore.areaMap[selectedAreaId.value]
    if (area) {
      otherParams.subarea_cid = area.cascade_id
    }
  }
  
  try {
    const [currentResult, otherResult] = await Promise.all([
      listDeviceStatePage(params),
      listDeviceStatePage(otherParams)
    ])
    
    if (currentResult.Status === 'Ok' && currentResult.data) {
      totalCount.value = currentResult.count || 0
      if (isDanger) {
        dangerListCount.value = currentResult.count || 0
      } else {
        warningListCount.value = currentResult.count || 0
      }
      alarmList.value = currentResult.data.map(item => ({
        id: item.id || '',
        device_name: item.device_name || '',
        location: item.location || '',
        subarea_cid: item.subarea_cid || '',
        type: item.event_info || '',
        happen_time: formatHappenTime(item.happen_time),
        severity: isDanger ? '紧急' : '重要',
        severityClass: isDanger ? 'danger' : 'warning',
        status: getStatusText(item.fault, item.alarm),
        statusClass: getStatusClass(item.fault, item.alarm)
      }))
    }
    
    if (otherResult.Status === 'Ok') {
      if (isDanger) {
        warningListCount.value = otherResult.count || 0
      } else {
        dangerListCount.value = otherResult.count || 0
      }
    }
    
    fetchDeviceCount()
  } catch (error) {
    console.error('获取告警列表失败:', error)
  }
}

function switchTab(tab) {
  selectedAlarmTab.value = tab
  currentPage.value = 1
  fetchAlarmList()
}

function switchViewMode(mode) {
  viewMode.value = mode
  currentPage.value = 1
  totalCount.value = 0
  if (mode === 'alarm') fetchAlarmList()
  else if (mode === 'issue') fetchIssueList()
  else fetchPendingTaskList()
}

function changePage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  if (viewMode.value === 'alarm') fetchAlarmList()
  else if (viewMode.value === 'issue') fetchIssueList()
  else fetchPendingTaskList()
}

function goToPage(event) {
  const page = parseInt(event.target.value)
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    if (viewMode.value === 'alarm') fetchAlarmList()
    else if (viewMode.value === 'issue') fetchIssueList()
    else fetchPendingTaskList()
  }
}

async function fetchIssueList() {
  try {
    const res = await posRequest.get('/repair/issue', {
      params: { size: pageSize.value, current: currentPage.value, status: 'TO_HANDLE' }
    })
    if (res.Status === 'Ok') {
      issueList.value = (res.data || []).map(item => ({
        id: item.id,
        issueTitle: item.issueTitle || '',
        location: (item.asset?.areaName || '') + (item.asset?.location ? ' ' + item.asset.location : ''),
        issueContent: item.issueContent || '',
        foundTime: item.foundTime || '',
        status: item.status === 'TO_HANDLE' ? '未处理' : item.status,
        statusClass: item.status === 'TO_HANDLE' ? 'warning' : '',
        issueLevel: item.issueLevel || '',
        issueLevelClass: item.issueLevel === 'CRITICAL' ? 'danger' : item.issueLevel === 'HIGH' ? 'warning' : '',
        asset: item.asset || null
      }))
      issueTotalCount.value = res.count || 0
      totalCount.value = res.count || 0
    }
  } catch (err) {
    console.error('获取待处理问题失败:', err)
  }
}

async function fetchPendingTaskList() {
  try {
    const params = { targetAgentId: 'ops-agent',taskType:"",resultExecuteStatus: pendingTaskStatus.value, pageNum: currentPage.value, pageSize: pageSize.value }
    if (pendingTaskSearch.value) params.decisionContent = pendingTaskSearch.value
    if (pendingTaskAreaId.value) {
      const area = areaStore.areaMap[pendingTaskAreaId.value]
      if (area) params.subareaCid = area.cascade_id
    }
    const res = await aiRequest.get('/dispatch/list', { params })
    if (res.Status === 'Ok' && res.data) {
      pendingTaskList.value = (res.data.records || []).map(item => {
        const levelMap = { '高': 'danger', '中': 'warning', '低': '' }
        return {
          id: item.id || '',
          decisionContent: item.decisionContent || '',
          businessDomain: item.businessDomain || '',
          problemDesc: item.problemDesc || '',
          createTime: item.createTime || '',
          level: item.level || '',
          levelClass: levelMap[item.level] || '',
          levelText: item.level || '-',
          creatorAgentName: item.creatorAgentName || '',
          problemSummary: item.problemSummary || '',
          dataSnapshot: item.dataSnapshot || '',
          resultExecuteStatus: item.resultExecuteStatus || '0',
          subareaCid: item.subareaCid || '',
          decisionData: item.decisionData || '',
          resultDesc: item.resultDesc || ''
        }
      })
      pendingTaskTotalCount.value = res.data.total || 0
      totalCount.value = res.data.total || 0
    }
  } catch (err) {
    console.error('获取运维Agent待处理任务失败:', err)
  }
}

function onPendingTaskFilterChange() {
  currentPage.value = 1
  fetchPendingTaskList()
}

async function handlePendingTaskDiagnosis(item) {
  diagnosisSourceType.value = 'COLLABORATION'
  diagnosisDispatchTaskId.value = item.id
  diagnosisSourceData.value = {
    id: item.id,
    problemDesc: item.problemDesc,
    thinkingOverview: item.thinkingOverview,
    creatorAgentName: item.creatorAgentName,
    level: item.level,
    createTime: item.createTime
  }

  // 从 dataSnapshot 中解析设备信息
  let deviceId = ''
  let deviceName = ''
  let deviceStatus = ''
  let deviceType = ''
  let snapshotData = null
  let zoneName = ''
  try {
    snapshotData = typeof item.dataSnapshot === 'string' ? JSON.parse(item.dataSnapshot) : item.dataSnapshot
    // 优先从 deviceList 中获取
    const firstDevice = snapshotData?.deviceList?.[0]
    if (firstDevice) {
      deviceId = firstDevice.id || ''
      deviceName = firstDevice.name || ''
      deviceStatus = firstDevice.status || ''
      deviceType = firstDevice.type || ''
    } else if (snapshotData?.deviceId) {
      // 直接从 snapshotData 中获取
      deviceId = snapshotData.deviceId
      deviceName = snapshotData.deviceName || ''
      deviceStatus = snapshotData.processState === 1 ? '故障' : '正常'
    }
    zoneName = snapshotData?.zoneName || ''
  } catch (e) {
    console.warn('解析 dataSnapshot 失败:', e)
  }

  // decisionData 为纯设备ID字符串，优先使用
  selectedDevice.value = {
    id: item.decisionData || deviceId,
    device_name: deviceName || item.thinkingOverview || item.problemSummary || '',
    location: zoneName || '',
    subarea_cid: item.subareaCid || '',
    subarea_name: zoneName || '',
    type: item.problemDesc || '',
    happen_time: item.createTime || '',
    raw_happen_time: item.createTime || '',
    severity: item.level || '-',
    severityClass: item.levelClass || '',
    status: deviceStatus || (item.problemDesc?.includes('故障') ? '故障' : '异常'),
    statusClass: item.levelClass || ''
  }

  // 获取设备实时数据
  if (deviceId) {
    try {
      const result = await iotRequest.get('/base/device/data/listOneDeviceDataOrState', {
        params: { page: 1, limit: 20, device_id: deviceId }
      })
      if (result.Status === 'Ok' && result.data) {
        realtimeData.value = result.data.map(d => ({
          name: d.data_short_name || d.data_name || '',
          value: d.dataValue || '-',
          state: d.state,
          eventLevel: d.event_level,
          eventTypeName: d.event_type_name || ''
        })).sort((a, b) => {
          if (a.state === 1 && b.state !== 1) return -1
          if (a.state !== 1 && b.state === 1) return 1
          return 0
        })
        const abnormalCount = result.data.filter(d => d.state === 1).length
        healthScore.value = Math.max(0, 100 - 5 * abnormalCount)
      }
    } catch (error) {
      console.error('获取设备实时数据失败:', error)
    }
  }

  await handleReport()

  // 上报+诊断成功后，更新任务的采纳状态为已采纳
  if (diagnosisResult.value && item.id) {
    try {
      await aiRequest.post(`/dispatch/adopt/${item.id}`, { adoptStatus: '1', adoptUser: userStore.user_name })
    } catch (e) {
      console.warn('更新任务采纳状态失败:', e)
    }
  }
}

// 设置协作任务的上下文（设备信息、来源数据等）
function setupPendingTaskContext(item) {
  diagnosisSourceType.value = 'COLLABORATION'
  diagnosisDispatchTaskId.value = item.id
  diagnosisSourceData.value = {
    id: item.id,
    problemDesc: item.problemDesc,
    thinkingOverview: item.thinkingOverview,
    creatorAgentName: item.creatorAgentName,
    level: item.level,
    createTime: item.createTime
  }

  let deviceId = ''
  let deviceName = ''
  let deviceStatus = ''
  let zoneName = ''
  try {
    const snapshotData = typeof item.dataSnapshot === 'string' ? JSON.parse(item.dataSnapshot) : item.dataSnapshot
    const firstDevice = snapshotData?.deviceList?.[0]
    if (firstDevice) {
      deviceId = firstDevice.id || ''
      deviceName = firstDevice.name || ''
      deviceStatus = firstDevice.status || ''
    } else if (snapshotData?.deviceId) {
      deviceId = snapshotData.deviceId
      deviceName = snapshotData.deviceName || ''
      deviceStatus = snapshotData.processState === 1 ? '故障' : '正常'
    }
    zoneName = snapshotData?.zoneName || ''
  } catch (e) {
    console.warn('解析 dataSnapshot 失败:', e)
  }

  // decisionData 为纯设备ID字符串，优先使用
  selectedDevice.value = {
    id: item.decisionData || deviceId,
    device_name: deviceName || item.thinkingOverview || item.problemSummary || '',
    location: zoneName || '',
    subarea_cid: item.subareaCid || '',
    subarea_name: zoneName || '',
    type: item.problemDesc || '',
    happen_time: item.createTime || '',
    raw_happen_time: item.createTime || '',
    severity: item.level || '-',
    severityClass: item.levelClass || '',
    status: deviceStatus || (item.problemDesc?.includes('故障') ? '故障' : '异常'),
    statusClass: item.levelClass || ''
  }

  return deviceId
}

// 将 ai_diagnosis_record 的 diagnosisResult 解析并填充到右侧面板
function applyDiagnosisRecord(item, record) {
  // 重置状态
  dispatched.value = record.dispatchStatus === 1
  orderInfo.value = null
  diagnosisRecordId.value = record.id || ''

  // 解析诊断结果
  let d = {}
  try {
    d = typeof record.diagnosisResult === 'string' ? JSON.parse(record.diagnosisResult) : (record.diagnosisResult || {})
  } catch (e) {
    console.warn('解析 diagnosisResult 失败:', e)
  }

  let measures = d.measures || []
  if (typeof measures === 'string') {
    try { measures = JSON.parse(measures) } catch { measures = [] }
  }
  diagnosisResult.value = {
    '置信度': d.confidence || 0,
    '根因分析': { '主要原因': d.reason || '-', '分析说明': d.reasonDetail || '' },
    'AI诊断结论': { '结论': d.conclusion || '-', '补充说明': d.conclusionDetail || '' },
    '维修建议': measures
  }
  dispatchResult.value = {
    '维修负责人id': d.executeUserId || '',
    '维修负责人姓名': d.executeUserName || '-',
    '所需工具': d.tools || '-',
    '优先级': d.priority || '',
    '预计到达时间': d.arrivalTime || '-',
    '预计完成时间': d.completionTime || '-'
  }
  orderId.value = d.orderId || ''
  if (orderId.value) {
    fetchOrderInfo(orderId.value)
  }

  // 恢复设备实时数据快照
  if (record.deviceRealtimeData) {
    try {
      realtimeData.value = JSON.parse(record.deviceRealtimeData)
    } catch (e) {
      realtimeData.value = []
    }
  } else {
    realtimeData.value = []
  }
  healthScore.value = 100
}

// 查询工单轨迹详情
async function fetchOrderProgress(orderId) {
  if (!orderId) return null
  try {
    const res = await posRequest.get(`/repair/orders/${orderId}/flow`)
    if (res.Status === 'Ok' && res.data) {
      return res.data
    }
  } catch (err) {
    console.error('获取工单轨迹失败:', err)
  }
  return null
}

// 工单轨迹状态映射
const orderStatusMap = { 10: '待派单', 20: '待确认', 30: '处理中', 40: '待验收', 50: '已完成', 60: '已关闭' }
const logTypeMap = { CREATE: '创建', DISPATCH: '派单', ACCEPT: '接单', PROCESS: '处理中', FINISH: '维修结束', ACCEPTANCE: '验收', CLOSE: '关闭' }
const repairResultMap = { 10: '故障排除', 20: '无法维修', 30: '报废', 40: '其他' }
const processTypeMap = { REPAIR: '维修', RENOVATION: '更新改造', OVERHAUL: '大修', RECTIFICATION: '整治' }

function closeOrderProgressModal() {
showOrderProgressModal.value = false
}

function openFeedbackModal(item) {
  feedbackForm.value = {
    id: item.id,
    resultExecuteStatus: '1',
    userFeedback: ''
  }
  showFeedbackModal.value = true
}

function closeFeedbackModal() {
  showFeedbackModal.value = false
}

async function submitFeedback() {
  if (!feedbackForm.value.userFeedback.trim()) {
    showToast('info', '请输入反馈内容')
    return
  }
  feedbackSubmitting.value = true
  try {
    const res = await aiRequest.post('/dispatch/user-feedback', {
      id: feedbackForm.value.id,
      username: userStore.user_name,
      userFeedback: feedbackForm.value.userFeedback,
      resultExecuteStatus: feedbackForm.value.resultExecuteStatus
    })
    if (res && (res.code === '200' || res.code === 200)) {
      showToast('success', '回填成功')
      showFeedbackModal.value = false
      await fetchPendingTaskList()
    } else {
      showToast('error', '回填失败', res?.msg || '未知错误')
    }
  } catch (err) {
    console.error('回填失败:', err)
    showToast('error', '回填失败', '请稍后重试')
  } finally {
    feedbackSubmitting.value = false
  }
}

function formatProgressTime(time) {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 19)
}

function formatOrderStatus(status) {
  if (status === null || status === undefined || status === '') return '-'
  return orderStatusMap[String(status)] || String(status)
}

function parseOrderMeasures(measures) {
  if (!measures) return []
  try {
    return typeof measures === 'string' ? JSON.parse(measures) : measures
  } catch {
    return []
  }
}

// 协作记录操作入口：已完成→查看，未完成→派发
async function handlePendingTaskAction(item) {
  if (item.resultExecuteStatus === '1') {
    // 已完成：查看诊断记录
    await viewPendingTaskDiagnosis(item)
  } else {
    // 未完成：派发流程
    await dispatchPendingTask(item)
  }
}

// 查看协作任务的诊断记录
async function viewPendingTaskDiagnosis(item) {
  try {
    const res = await aiRequest.get(`/ai-diagnosis/by-source/${item.id}`)
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      setupPendingTaskContext(item)
      applyDiagnosisRecord(item, res.data)

      // 从诊断结果中提取 orderId，查询工单轨迹
      let orderId = ''
      try {
        const diagData = typeof res.data.diagnosisResult === 'string'
          ? JSON.parse(res.data.diagnosisResult) : res.data.diagnosisResult
        orderId = diagData?.orderId || ''
      } catch (e) { /* ignore */ }
      if (orderId) {
        const progressData = await fetchOrderProgress(orderId)
        if (progressData) {
          orderProgressData.value = progressData
          showOrderProgressModal.value = true
        }
      }
    } else {
      // 无诊断记录，弹窗显示 dispatch_task 的 result_desc
      orderProgressData.value = null
      orderProgressResultDesc.value = item.resultDesc || '暂无处理结果描述'
      showOrderProgressModal.value = true
    }
  } catch (err) {
    console.error('查询诊断记录失败:', err)
    showToast('error', '查询失败', '获取诊断记录失败')
  }
}

// 派发协作任务：先查诊断记录，有则展示，无则走AI诊断流程
async function dispatchPendingTask(item) {
  try {
    const res = await aiRequest.get(`/ai-diagnosis/by-source/${item.id}`)
    if (res && (res.code === '200' || res.code === 200) && res.data) {
      // 已有诊断记录，直接展示
      setupPendingTaskContext(item)
      applyDiagnosisRecord(item, res.data)
    } else {
      // 无诊断记录，走完整AI诊断流程
      await handlePendingTaskDiagnosis(item)
    }
  } catch (err) {
    console.error('查询诊断记录失败，转为AI诊断流程:', err)
    await handlePendingTaskDiagnosis(item)
  }
}

const issueLevelText = (level) => {
  const map = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }
  return map[level] || level || '-'
}

const priorityText = (priority) => {
  const map = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }
  return map[priority] || priority || '-'
}

async function fetchDeviceData(deviceItem) {
  diagnosisSourceType.value = 'ALARM'
  diagnosisDispatchTaskId.value = ''
  diagnosisSourceData.value = {
    id: deviceItem.id,
    device_name: deviceItem.device_name,
    location: deviceItem.location,
    type: deviceItem.type,
    happen_time: deviceItem.happen_time,
    severity: deviceItem.severity,
    status: deviceItem.status
  }

  let locationName = deviceItem.location
  let areaCid = deviceItem.subarea_cid || ''
  let subarea_name = ''
  if (deviceItem.subarea_cid) {
    const area = areaStore.areaList.find(a => a.cascade_id === deviceItem.subarea_cid)
    if (area) subarea_name = area.name
  }
  
  selectedDevice.value = {
    id: deviceItem.id || '',
    device_name: deviceItem.device_name,
    location: locationName,
    subarea_cid: areaCid,
    subarea_name: subarea_name,
    type: deviceItem.type,
    happen_time: deviceItem.happen_time,
    raw_happen_time: deviceItem.raw_happen_time || deviceItem.happen_time || '',
    severity: deviceItem.severity,
    severityClass: deviceItem.severityClass,
    status: deviceItem.status,
    statusClass: deviceItem.statusClass
  }
  
  try {
    const result = await iotRequest.get('/base/device/data/listOneDeviceDataOrState', {
      params: {
        page: 1,
        limit: 20,
        device_id: deviceItem.id
      }
    })
    
    if (result.Status === 'Ok' && result.data) {
      realtimeData.value = result.data.map(item => ({
        name: item.data_short_name || item.data_name || '',
        value: item.dataValue || '-',
        state: item.state,
        eventLevel: item.event_level,
        eventTypeName: item.event_type_name || ''
      })).sort((a, b) => {
        if (a.state === 1 && b.state !== 1) return -1
        if (a.state !== 1 && b.state === 1) return 1
        return 0
      })
      
      const abnormalCount = result.data.filter(item => item.state === 1).length
      healthScore.value = Math.max(0, 100 - 5 * abnormalCount)
    }
  } catch (error) {
    console.error('获取设备实时数据失败:', error)
  }

  // 自动触发上报 + AI诊断
  await handleReport()
}

async function handleIssueDiagnosis(issueItem) {
  diagnosisSourceType.value = 'ISSUE'
  diagnosisDispatchTaskId.value = ''
  diagnosisSourceData.value = {
    issueId: issueItem.issueId,
    issueTitle: issueItem.issueTitle,
    issueContent: issueItem.issueContent,
    issueLevel: issueItem.issueLevel,
    foundTime: issueItem.foundTime,
    asset: issueItem.asset
  }

  const asset = issueItem.asset || {}
  selectedDevice.value = {
    id: asset.id || '',
    device_name: issueItem.issueTitle || asset.assetName || '',
    location: issueItem.location || '',
    subarea_cid: '',
    subarea_name: asset.areaName || '',
    type: issueItem.issueContent || '',
    happen_time: issueItem.foundTime || '',
    raw_happen_time: issueItem.foundTime || '',
    severity: issueLevelText(issueItem.issueLevel),
    severityClass: issueItem.issueLevelClass,
    status: issueItem.status,
    statusClass: issueItem.statusClass
  }

  // 获取设备实时数据
  if (asset.id) {
    try {
      const result = await iotRequest.get('/base/device/data/listOneDeviceDataOrState', {
        params: { page: 1, limit: 20, device_id: asset.id }
      })
      if (result.Status === 'Ok' && result.data) {
        realtimeData.value = result.data.map(item => ({
          name: item.data_short_name || item.data_name || '',
          value: item.dataValue || '-',
          state: item.state,
          eventLevel: item.event_level,
          eventTypeName: item.event_type_name || ''
        })).sort((a, b) => {
          if (a.state === 1 && b.state !== 1) return -1
          if (a.state !== 1 && b.state === 1) return 1
          return 0
        })
        const abnormalCount = result.data.filter(item => item.state === 1).length
        healthScore.value = Math.max(0, 100 - 5 * abnormalCount)
      }
    } catch (error) {
      console.error('获取设备实时数据失败:', error)
    }
  }

  await handleReport()
}

async function handleReport() {
  const device = selectedDevice.value
  if (!device.device_name) {
    showToast('info', '请先选择设备')
    return
  }
  dispatched.value = false
  orderInfo.value = null
  diagnosisRecordId.value = ''
  const issueLevel = device.status === '故障' ? 'CRITICAL' : 'HIGH'
  let foundTime = ''
  if (device.happen_time && device.happen_time.length >= 19) {
    foundTime = device.happen_time.replace('T', ' ')
  } else {
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    foundTime = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  }
  isScanning.value = true
  try {
    const command = {
      issueTitle: device.device_name,
      issueContent: device.type || device.severity || '设备异常',
      issueLevel: issueLevel,
      assetId: device.id || '',
      foundTime,
      userId: userStore.user_id,
      userName: userStore.user_name,
      orgCid: userStore.org_cid
    }
    const commandBlob = new Blob([JSON.stringify(command)], { type: 'application/json' })
    const formData = new FormData()
    formData.append('command', commandBlob, 'command.json')

    // 1. 上报问题
    const res = await posRequest.post('/repair/issue', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      transformRequest: [(data, headers) => {
        delete headers['Content-Type']
        return data
      }]
    })
    if (res.Status !== 'Ok' || !res.data) {
      showToast('error', '上报失败', res.msg || '未知错误')
      return
    }
    const issueId = res.data

    // 2. AI诊断
    const diagParams = new URLSearchParams()
    diagParams.append('issueId', issueId)
    const diagRes = await posRequest.post('/diagnosis/action/create-by-ai', diagParams, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
    if (diagRes.Status === 'Ok' && diagRes.data) {
      const d = diagRes.data
      // 解析 measures（可能是 JSON 字符串数组）
      let measures = d.measures || []
      if (typeof measures === 'string') {
        try { measures = JSON.parse(measures) } catch { measures = [] }
      }
      // 映射到模板期望的结构
      diagnosisResult.value = {
        '置信度': d.confidence || 0,
        '根因分析': { '主要原因': d.reason || '-', '分析说明': d.reasonDetail || '' },
        'AI诊断结论': { '结论': d.conclusion || '-', '补充说明': d.conclusionDetail || '' },
        '维修建议': measures
      }
      dispatchResult.value = {
        '维修负责人id': d.executeUserId || '',
        '维修负责人姓名': d.executeUserName || '-',
        '所需工具': d.tools || '-',
        '优先级': d.priority || '',
        '预计到达时间': d.arrivalTime || '-',
        '预计完成时间': d.completionTime || '-'
      }
      orderId.value = d.orderId || ''
      if (orderId.value) await fetchOrderInfo(orderId.value)

      // 保存AI诊断记录
      try {
        const recordRes = await aiRequest.post('/ai-diagnosis', {
          sourceType: diagnosisSourceType.value,
          sourceDataId: diagnosisSourceData.value?.id || diagnosisSourceData.value?.issueId || '',
          sourceData: JSON.stringify(diagnosisSourceData.value || {}),
          deviceRealtimeData: JSON.stringify((realtimeData.value || []).slice(0, 5)),
          diagnosisResult: JSON.stringify(diagRes.data),
          diagnoseUser: userStore.user_name,
          dispatchStatus: 0
        })
        if (recordRes && (recordRes.code === '200' || recordRes.code === 200)) {
          diagnosisRecordId.value = recordRes.data || ''
        }
      } catch (e) {
        console.warn('保存诊断记录失败:', e)
      }
    } else {
      showToast('error', 'AI诊断失败', diagRes.msg || '未知错误')
    }
  } catch (err) {
    console.error('上报/诊断失败:', err)
    showToast('error', '上报失败，请重试')
  } finally {
    isScanning.value = false
  }
}

async function handleDispatch() {
  if (!orderId.value) {
    showToast('info', '暂无工单，请先上报')
    return
  }
  try {
    const res = await posRequest.put(`/repair/orders/${orderId.value}/action/one-click-dispatch`)
    console.log('一键派单返回:', res)
    if (res.Status === 'Ok' || res.code === '200' || res.code === 200) {
      dispatched.value = true
      await fetchOrderInfo(orderId.value)
      await fetchKpiStats()
      await fetchOrderStatusChart()

      // 更新诊断记录派发状态
      if (diagnosisRecordId.value) {
        try {
          await aiRequest.put(`/ai-diagnosis/dispatch/${diagnosisRecordId.value}`, null, {
            params: { dispatchUser: userStore.user_name }
          })
        } catch (e) {
          console.warn('更新诊断记录派发状态失败:', e)
        }
      }

      // 更新派发任务执行结果
      if (diagnosisDispatchTaskId.value) {
        try {
          const now = new Date()
          const timeStr = now.getFullYear() + '-' +
            String(now.getMonth() + 1).padStart(2, '0') + '-' +
            String(now.getDate()).padStart(2, '0') + ' ' +
            String(now.getHours()).padStart(2, '0') + ':' +
            String(now.getMinutes()).padStart(2, '0') + ':' +
            String(now.getSeconds()).padStart(2, '0')
          const resultDesc = `完成派单：${orderInfo.value?.orderTitle || '工单'}${orderInfo.value?.orderNo || ''}于${timeStr}派发给${dispatchResult.value?.['维修负责人姓名'] || ''}`
          await aiRequest.put(`/dispatch/${diagnosisDispatchTaskId.value}`, {
            completeTime: timeStr,
            resultExecuteUser: userStore.user_name,
            resultExecuteStatus: '1',
            resultDesc: resultDesc
          })
        } catch (e) {
          console.warn('更新派发任务执行结果失败:', e)
        }
      }

      showToast('success', '派单成功', '已通知维修负责人')
      flashOrderButton()
      // 派单成功后弹窗显示工单信息
      // openOrderDetail(orderInfo.value)
    } else {
      showToast('error', '派单失败', res.msg || '未知错误')
    }
  } catch (err) {
    console.error('一键派单失败:', err)
    showToast('error', '派单失败，请重试')
  }
}

function initTunnelStatsChart() {
  if (!tunnelStatsChartRef.value) return
  tunnelStatsChart = echarts.init(tunnelStatsChartRef.value)
  tunnelStatsChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '历史告警',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 16, fontWeight: 'bold', color: '#fff' }
        },
        labelLine: { show: false },
        data: [
          { value: tunnelStats.value.faultCount, name: '故障总数', itemStyle: { color: 'rgba(245, 158, 11, 0.6)' } },
          { value: tunnelStats.value.alarmCount, name: '告警总数', itemStyle: { color: 'rgba(239, 68, 68, 0.6)' } }
        ]
      }
    ]
  })
}

async function initDiagnosisTrendChart() {
  if (!diagnosisTrendRef.value) return
  diagnosisTrendLoading.value = true

  // 生成近7天日期
  const days = []
  const labels = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const pad = (n) => String(n).padStart(2, '0')
    const dateStr = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    const beginStr = `${dateStr} 00:00:00`
    const endStr = `${dateStr} 23:59:59`
    days.push({ begin: beginStr, end: endStr })
    labels.push(`${pad(d.getMonth() + 1)}-${pad(d.getDate())}`)
  }

  // 查询近7天诊断次数
  const diagnosisCounts = []
  for (const day of days) {
    try {
      const res = await posRequest.get('/diagnosis', {
        params: { size: 1, current: 1, source: 'ONE_CLICK', createTimeBegin: day.begin, createTimeEnd: day.end }
      })
      diagnosisCounts.push(res.count || 0)
    } catch { diagnosisCounts.push(0) }
  }

  // 查询近7天派单次数
  const dispatchCounts = []
  for (const day of days) {
    try {
      const res = await posRequest.get('/repair/orders', {
        params: { size: 1, current: 1, dispatchType: 'AUTO',status:'20,30,40,50', dispatchTimeBegin: day.begin, dispatchTimeEnd: day.end }
      })
      dispatchCounts.push(res.count || 0)
    } catch { dispatchCounts.push(0) }
  }

  diagnosisTrendLoading.value = false
  await nextTick()

  if (!diagnosisTrendChart) {
    diagnosisTrendChart = echarts.init(diagnosisTrendRef.value)
  } else {
    diagnosisTrendChart.resize()
  }
  diagnosisTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['AI诊断次数', 'AI派单次数'], textStyle: { color: 'white', fontSize: 10 }, top: 0, right: 0 },
    grid: { left: 30, right: 10, top: 25, bottom: 20 },
    xAxis: { type: 'category', data: labels, axisLine: { lineStyle: { color: '#2a3a4a' } }, axisLabel: { color: 'white', fontSize: 10 } },
    yAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#1a2a3a' } }, axisLabel: { color: 'white', fontSize: 10 } },
    series: [
      { name: 'AI诊断次数', type: 'line', data: diagnosisCounts, smooth: true, lineStyle: { color: '#3b82f6' }, itemStyle: { color: '#3b82f6' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.3)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } } },
      { name: 'AI派单次数', type: 'line', data: dispatchCounts, smooth: true, lineStyle: { color: '#10b981' }, itemStyle: { color: '#10b981' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.2)' }, { offset: 1, color: 'rgba(16,185,129,0)' }] } } }
    ]
  })
}

function initAlarmTypeChart() {
  if (!alarmTypeRef.value) return
  alarmTypeChart = echarts.init(alarmTypeRef.value)
  alarmTypeChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 0, top: 'center', textStyle: { color: 'white', fontSize: 10 }, itemWidth: 10, itemHeight: 10 },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['35%', '50%'],
      data: [
        { value: 248, name: '紧急', itemStyle: { color: '#ef4444' } },
        { value: 1348, name: '重要', itemStyle: { color: '#f59e0b' } },
        { value: 3255, name: '一般', itemStyle: { color: '#3b82f6' } },
        { value: 8095, name: '提示', itemStyle: { color: '#6b7280' } }
      ],
      label: { show: false }
    }]
  })
}

function initAlarmTrendChart() {
  if (!alarmTrendRef.value) return
  alarmTrendChart = echarts.init(alarmTrendRef.value)
  alarmTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 10, top: 10, bottom: 20 },
    xAxis: { type: 'category', data: ['05-10', '05-11', '05-12', '05-13', '05-14', '05-15', '05-16'], axisLine: { lineStyle: { color: '#2a3a4a' } }, axisLabel: { color: 'white', fontSize: 10 } },
    yAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#1a2a3a' } }, axisLabel: { color: 'white', fontSize: 10 } },
    series: [{
      type: 'bar', data: [180, 220, 195, 280, 310, 260, 128],
      itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#3b82f6' }, { offset: 1, color: 'rgba(59,130,246,0.3)' }] }, borderRadius: [4, 4, 0, 0] },
      barWidth: '50%'
    }]
  })
}

// 定时器
let diagnosisTrendTimer = null
let kpiStatsTimer = null
let listTimer = null

onMounted(async () => {
  const areaList = await areaStore.fetchAreaList()
  fetchTunnelStats()
  fetchTodayAlarmStats()
  fetchAlarmList()
  fetchEngineerList()
  fetchKpiStats()
  fetchIssueList()
  fetchPendingTaskList()
  fetchOrderStatusChart()
  nextTick(() => {
    initDiagnosisTrendChart()
    initAlarmTypeChart()
    initAlarmTrendChart()
    initHealthChart()
    initDeviceSankeyChart()
  })

  // 演示模式：默认展示第一条协作记录的「AI诊断与派单」内容
  // （带 autoDiagnosis / autoDispatchTask 参数时由既有的自动流程处理，避免重复触发）
  setTimeout(async () => {
    if (route.query.autoDiagnosis === 'true' || route.query.autoDispatchTask === 'true') return
    if (isScanning.value || diagnosisResult.value) return
    if (pendingTaskList.value.length > 0) {
      await dispatchPendingTask(pendingTaskList.value[0])
    }
  }, 1200)

  // 定时刷新
  diagnosisTrendTimer = setInterval(() => initDiagnosisTrendChart(), 8 * 60 * 1000)
  kpiStatsTimer = setInterval(() => fetchKpiStats(), 10 * 60 * 1000)
  listTimer = setInterval(() => {
    if (viewMode.value === 'alarm') fetchAlarmList()
    else if (viewMode.value === 'issue') fetchIssueList()
    else fetchPendingTaskList()
  }, 60 * 1000)

  // 处理自动AI诊断：从告警页面点击"AI处理"跳转过来时
  if (route.query.autoDiagnosis === 'true') {
    // 切换到待处理问题视图
    switchViewMode('issue')
    // 等待数据加载完成后自动诊断第一条
    await nextTick()
    // 等待fetchIssueList完成（它是async但onMounted里没await，需要等一会儿）
    setTimeout(() => {
      if (issueList.value.length > 0) {
        handleIssueDiagnosis(issueList.value[0])
      }
    }, 1000)
  }

  // 处理从Dashboard"Agent协作"列表派发过来的任务：自动触发协作记录的派发操作
  if (route.query.autoDispatchTask === 'true') {
    // 切换到协作记录视图
    switchViewMode('pendingTask')
    await nextTick()
    // 等待fetchPendingTaskList完成后，根据taskId匹配并自动触发派发
    setTimeout(async () => {
      const taskId = route.query.taskId
      let targetItem = null
      if (taskId) {
        // 优先按taskId精确匹配
        targetItem = pendingTaskList.value.find(item => item.id === taskId)
      }
      // 如果列表中未找到（可能分页或状态不匹配），直接通过接口获取该任务
      if (!targetItem && taskId) {
        try {
          const res = await aiRequest.get('/dispatch/list', { params: { targetAgentId: 'ops-agent', pageNum: 1, pageSize: 100 } })
          if (res && (res.Status === 'Ok' || res.code === '200' || res.code === 200) && res.data) {
            const records = res.data.records || []
            targetItem = records.find(item => item.id === taskId)
            if (targetItem) {
              // 补全 pendingTaskList 并切换到对应页
              targetItem = {
                id: targetItem.id || '',
                decisionContent: targetItem.decisionContent || '',
                businessDomain: targetItem.businessDomain || '',
                problemDesc: targetItem.problemDesc || '',
                createTime: targetItem.createTime || '',
                level: targetItem.level || '',
                levelClass: { '高': 'danger', '中': 'warning', '低': '' }[targetItem.level] || '',
                levelText: targetItem.level || '-',
                creatorAgentName: targetItem.creatorAgentName || '',
                problemSummary: targetItem.problemSummary || '',
                dataSnapshot: targetItem.dataSnapshot || '',
                resultExecuteStatus: targetItem.resultExecuteStatus || '0',
                subareaCid: targetItem.subareaCid || '',
                decisionData: targetItem.decisionData || '',
                resultDesc: targetItem.resultDesc || '',
                thinkingOverview: targetItem.thinkingOverview || ''
              }
            }
          }
        } catch (e) {
          console.error('查询任务详情失败:', e)
        }
      }
      if (targetItem) {
        // 自动触发协作记录的派发操作
        await dispatchPendingTask(targetItem)
      } else {
        showToast('error', '未找到任务', '未找到对应的协作记录')
      }
    }, 1500)
  }
})

onUnmounted(() => {
  uninstallDashboardMock()
  clearInterval(diagnosisTrendTimer)
  clearInterval(kpiStatsTimer)
  clearInterval(listTimer)
  diagnosisTrendChart?.dispose()
  alarmTypeChart?.dispose()
  alarmTrendChart?.dispose()
  tunnelStatsChart?.dispose()
  todayAlarmChart?.dispose()
  healthChart?.dispose()
  aiTrendChart?.dispose()
  deviceSankeyChart?.dispose()
  orderChart?.dispose()
})


// ------- 语音服务订阅 start -------
onMounted(() => {
  window.addEventListener("message", handleVisionVoiceMessage);
});
onUnmounted(() => {
  window.removeEventListener("message", handleVisionVoiceMessage);
});
const handleVisionVoiceMessage = (event) => {
  if (event.source !== window) return;
  if (event.data?.type !== "VISION_VOICE_EVENT") return;
  const { subscribeId, subscribeEvent, subscribeContent, msgId } = event.data.data;

  if (subscribeId === "OpenIframe") {
    if (subscribeEvent === "关闭运维") {
      goBack()
      responseVisionVoice(msgId, "已关闭")
    } else if (subscribeEvent === "打开工单列表") {
      showOrderMore()
      responseVisionVoice(msgId, "已打开")
    } else if (subscribeEvent === "关闭工单列表") {
      closeIframeModal()
      responseVisionVoice(msgId, "已关闭")
    }
  } else if (subscribeId === "AIDiagnosis") {
    if(subscribeEvent === "AI诊断") {
      let nameList = ''
      if (viewMode.value === 'alarm'){
        nameList = filteredAlarmList.value.map(item => item.device_name).join("、")
      } else if (viewMode.value === 'issue'){
        nameList = issueList.value.map(item => item.issueTitle).join("、")
      } else {
        nameList = pendingTaskList.value.map(item => item.decisionContent).join("、")
      }
      responseVisionVoice(msgId, nameList ? "成功获取AI诊断列表": "当前列表没有此元素", { targetNameList: nameList })
    } else if (subscribeEvent === "AI诊断选中元素") {
      let findTarget
      if (viewMode.value === 'alarm'){
        findTarget = filteredAlarmList.value.find(item => item.device_name === subscribeContent);
        if (findTarget) fetchDeviceData(findTarget)
      } else if (viewMode.value === 'issue'){
        findTarget = issueList.value.find(item => item.issueTitle === subscribeContent);
        if (findTarget) handleIssueDiagnosis(findTarget)
      } else {
        findTarget = pendingTaskList.value.find(item => item.decisionContent === subscribeContent);
        if (findTarget) handlePendingTaskDiagnosis(findTarget)
      }
      responseVisionVoice(msgId, findTarget ? "正在为您诊断" : "当前列表没有该元素")
    } else if (subscribeEvent === "一键派单") {
      handleDispatch()
      responseVisionVoice(msgId, "已派单")
    }
  }
};
const responseVisionVoice = (msgId, msg, msgData) => {
  window.postMessage({
    type: "VISION_VOICE_EVENT_ACK",
    msgId,
    msg,
    msgData,
  }, "*");
}
// ------- 语音服务订阅 end -------
</script>

<style scoped>
* { margin: 0; padding: 0; box-sizing: border-box; }

.operations-container {
  width: 100%;
  height: 100vh;
  background: rgb(0, 66, 126);
  /* background: #0a1628; */ 
  color: #e0e8f0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-close-btn {
  position: fixed;
  top: 20px;
  left: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(0, 180, 255, 0.15);
  border: 1px solid rgba(0, 180, 255, 0.3);
  color: #00b4ff;
  cursor: pointer;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.page-close-btn:hover {
  background: rgba(0, 180, 255, 0.3);
  border-color: rgba(0, 180, 255, 0.6);
  transform: scale(1.1);
}

/* Head层 */
.head {
  background-image: url('/static/head.png');
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  height: 70px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border-bottom: 1px solid rgba(80, 120, 160, .2);
}

.head-title {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.head-title > span:first-of-type {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}

.title-icon {
  color: #3b82f6;
  filter: drop-shadow(0 2px 4px rgba(59, 130, 246, 0.5));
}

.title-sub {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 1px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}

/* Head-KPI层 */
.head-kpi {
  height: 16vh;
  min-height: 80px;
  display: flex;
  gap: 12px;
  padding: 30px 20px 14px;
  background: rgb(0, 66, 126);
  /* background: rgba(15, 30, 55, 0.5); */
}

.kpi-card {
  flex: 1;
  display: grid;
  align-items: center;
  gap: 10px;
  grid-template-columns: 1.6fr 2.2fr 3fr;
  padding: 12px 16px;
  /* background: linear-gradient(135deg, rgba(20, 40, 70, 0.8), rgba(15, 30, 55, 0.8)); */
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  transition: all 0.2s;
}

.kpi-card:hover {
  border-color: rgba(59, 130, 246, 0.3);
  transform: translateY(-2px);
}

.kpi-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kpi-icon.red { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.kpi-icon.orange { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.kpi-icon.blue { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.kpi-icon.green { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.kpi-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kpi-text-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.kpi-text-row {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.kpi-text-row.rate-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(59, 130, 246, 0.1);
  position: relative;
  overflow: hidden;
}

.kpi-text-row.rate-row::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 40%;
  height: 100%;
  background: linear-gradient(
    120deg,
    transparent 0%,
    rgba(59, 130, 246, 0.15) 30%,
    rgba(139, 92, 246, 0.2) 50%,
    rgba(59, 130, 246, 0.15) 70%,
    transparent 100%
  );
  animation: rate-sweep 10s ease-in-out infinite;
  pointer-events: none;
}

@keyframes rate-sweep {
  0%, 85% { left: -60%; }
  100%    { left: 120%; }
}

.kpi-text-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.kpi-text-label {
  font-size: 11px;
  color: white;
}

.kpi-text-value {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.kpi-text-item.diagnosis .kpi-text-value {
  color: #58D9F9;
}

.kpi-text-item.dispatch .kpi-text-value {
  color: #7CFFB2;
}

.kpi-text-item.fault .kpi-text-value {
  color: #f59e0b;
}

.kpi-text-item.alarm .kpi-text-value {
  color: #ef4444;
}

.kpi-chart {
  padding: 0;
  width: 80px;
  height: 80px;
  margin-top: -14px;
  overflow: hidden;
}

.kpi-label {
  font-size: 12px;
  color: white;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.kpi-value small {
  font-size: 12px;
  font-weight: 400;
  color: white;
  margin-left: 4px;
}

.kpi-trend {
  font-size: 11px;
}

.kpi-trend.green { color: #10b981; }
.kpi-trend.red { color: #ef4444; }

/* Ops-Main层 */
.ops-main {
  height: 50vh;
  min-height: 300px;
  display: flex;
  gap: 12px;
  padding: 12px 20px;
  overflow: hidden;
}

.alarm-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* background: linear-gradient(135deg, rgba(20, 40, 70, 0.6), rgba(15, 30, 55, 0.6)); */
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  overflow: hidden;
}

.alarm-view-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header-bg{
  /* background: #234497 !important; */
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  /* background: rgba(10, 22, 40, 0.5); */
  /* background: #234497; */
  background: rgba(10, 22, 40, 0.1);
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
  position: relative;
}

.zoom-btn {
  margin-left: auto;
  padding: 4px;
  background: transparent;
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 4px;
  color: #60a5fa;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.zoom-btn:hover {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
  color: #93c5fd;
}

.panel-icon {
  color: #3b82f6;
}

.panel-view-tabs {
  display: flex;
  gap: 4px;
}

.panel-view-tab {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: white;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.panel-view-tab:hover {
  color: #aabbcc;
  background: rgba(59, 130, 246, 0.08);
}

.panel-view-tab.active {
  color: #fff;
  background: rgba(59, 130, 246, 0.2);
}

.alarm-tabs {
  display: flex;
  gap: 8px;
  padding: 8px 14px;
  /* background: rgba(10, 22, 40, 0.3); */
  background: rgba(255, 255, 255, 0.01);
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
}

.tab {
  padding: 4px 10px;
  font-size: 11px;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab b {
  margin-left: 4px;
  font-weight: 600;
}

.tab.active {
  background: rgba(59, 130, 246, 0.2);
}

.tab.danger { color: #ef4444; }
.tab.danger.active { background: rgba(239, 68, 68, 0.2); }
.tab.warning { color: #f59e0b; }
.tab.warning.active { background: rgba(245, 158, 11, 0.2); }

.area-select {
  margin-left: 10px;
  padding: 4px 8px;
  background: rgba(10, 22, 40, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  color: #c0d0e0;
  font-size: 11px;
  outline: none;
  cursor: pointer;
}

.area-select option {
background: #0a1628;
color: #c0d0e0;
}

.task-search-input {
margin-left: 8px;
padding: 4px 8px;
background: rgba(10, 22, 40, 0.1);
border: 1px solid rgba(59, 130, 246, 0.3);
border-radius: 4px;
color: #c0d0e0;
font-size: 11px;
outline: none;
width: 160px;
}

.task-search-input::placeholder {
color: rgba(160, 180, 200, 0.5);
}

.alarm-table {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.table-header {
  display: grid;
  grid-template-columns: 2fr 2fr 1.5fr 0.6fr 0.4fr 0.4fr 0.3fr;
  gap: 8px;
  padding: 8px 14px;
  font-size: 11px;
  color: white;
  /* background: rgba(10, 22, 40, 0.5); */
  background: rgba(255, 255, 255, 0.01);
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
  position: sticky;
  top: 0;
}
.issue-header{
  grid-template-columns: 2fr 0.6fr 1fr 0.6fr 0.4fr 0.4fr  !important;
}
.pending-task-header {
  grid-template-columns: 1.2fr 0.6fr 1.5fr 0.8fr 0.4fr 0.8fr 0.7fr !important;
}

.table-row.pending-task-row {
  grid-template-columns: 1.2fr 0.6fr 1.5fr 0.8fr 0.4fr 0.8fr 0.7fr !important;
}

.table-body {
  display: flex;
  flex-direction: column;
}

.table-row {
  display: grid;
  grid-template-columns: 2fr 2fr 1.5fr 0.6fr 0.4fr 0.4fr 0.3fr;
  gap: 8px;
  padding: 8px 14px;
  font-size: 11px;
  color: #c0d0e0;
  border-bottom: 1px solid rgba(59, 130, 246, 0.05);
  transition: background 0.2s;
  align-items: center;
}

.table-row > span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.event-info {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-diagnosis {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
  position: relative;
  z-index: 1;
  overflow:visible;
}

.ai-orb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1868ff, #00d4ff);
  color: #fff;
  font-size: 10px;
  font-weight: 900;
  box-shadow: 0 0 12px rgba(18, 104, 255, 0.4);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.ai-orb:hover {
transform: scale(1.15);
box-shadow: 0 0 20px rgba(18, 104, 255, 0.6);
}

.ai-orb.small {
width: auto;
min-width: 24px;
padding: 0 8px;
border-radius: 12px;
font-size: 11px;
}

.ai-orb.small.feedback-btn {
margin-left: 6px;
background: linear-gradient(135deg, #0a7d4e, #22c55e);
box-shadow: 0 0 12px rgba(34, 197, 94, 0.4);
}

.ai-orb.small.feedback-btn:hover {
box-shadow: 0 0 20px rgba(34, 197, 94, 0.6);
}

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 80px;
  color: #556677;
  font-size: 12px;
}

/* Thinking loader */
.thinking-loader {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-height: 120px;
}

.thinking-dots {
  display: flex;
  gap: 6px;
}

.thinking-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #3b82f6;
  animation: thinking-bounce 1.4s ease-in-out infinite both;
}

.thinking-dots span:nth-child(1) { animation-delay: 0s; }
.thinking-dots span:nth-child(2) { animation-delay: 0.16s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.32s; }

@keyframes thinking-bounce {
  0%, 80%, 100% {
    transform: scale(0.4);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.thinking-text {
  font-size: 12px;
  color: white;
  letter-spacing: 0.5px;
}

.table-row:hover {
  background: rgba(59, 130, 246, 0.05);
}

.device-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
}

.device-name svg {
  flex-shrink: 0;
}

.severity {
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}

.severity.danger { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.severity.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.severity.info { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }

.status {
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 10px;
}

.status.processing { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.status.pending { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.status.unhandled { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  font-size: 11px;
  color: white;
  /* background: rgba(10, 22, 40, 0.5); */
  background: rgba(255, 255, 255, 0.01);
  border-top: 1px solid rgba(59, 130, 246, 0.1);
}

.pagination {
  display: flex;
  gap: 4px;
}

.pagination button {
  width: 24px;
  height: 24px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  background: transparent;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  transition: all 0.2s;
}

.pagination button.active,
.pagination button:hover {
background: rgba(59, 130, 246, 0.2);
border-color: #3b82f6;
color: #3b82f6;
}

.pagination-ellipsis {
display: inline-flex;
align-items: center;
justify-content: center;
width: 24px;
height: 24px;
color: rgba(255, 255, 255, 0.4);
font-size: 11px;
user-select: none;
}

.table-footer input {
  width: 32px;
  padding: 2px 4px;
  background: rgba(10, 22, 40, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 3px;
  color: #fff;
  text-align: center;
  font-size: 11px;
}

/* 右侧诊断面板 */
.diagnosis-panel {
  flex: 1.5;
  display: flex;
  flex-direction: column;
  /* background: linear-gradient(135deg, rgba(20, 40, 70, 0.6), rgba(15, 30, 55, 0.6)); */
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  overflow: hidden;
}

.diagnosis-content {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px;
  overflow-y: auto;
}

.device-overview {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  /* background: rgba(10, 22, 40, 0.4); */
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 6px;
  overflow: hidden;
}

.overview-title {
  font-size: 12px;
  color: #3b82f6;
  font-weight: 600;
}

.device-3d {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  background: rgba(59, 130, 246, 0.05);
  border-radius: 6px;
}

.device-3d-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.device-alert-info {
  padding-top: 8px;
  border-top: 1px solid rgba(59, 130, 246, 0.15);
}

.model-placeholder {
  width: 80px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
  position: relative;
  overflow: hidden;
  border-radius: 6px;
}

.model-placeholder.scanning::after {
  content: '';
  position: absolute;
  left: 0;
  width: 100%;
  height: 30%;
  background: linear-gradient(to bottom, transparent, rgba(59, 130, 246, 0.35), transparent);
  animation: scan-line 1.5s ease-in-out infinite;
  pointer-events: none;
}

.model-placeholder.scanning {
  border: 1px solid rgba(59, 130, 246, 0.3);
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.15);
}

@keyframes scan-line {
  0% { top: -30%; }
  100% { top: 100%; }
}

.device-info h4 {
  font-size: 14px;
  color: #fff;
  margin-bottom: 4px;
}

.device-info p {
  font-size: 11px;
  color: white;
}

.device-meta {
  font-size: 11px;
  color: white;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta-label {
  color: #c0d0e0;
}

.meta-value {
  color: #c0d0e0;
}

.device-status-row {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  flex-flow: column;
}

.device-status,
.device-severity {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-badge,
.severity-badge {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}

.status-badge.danger { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.status-badge.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.status-badge.normal { background: rgba(16, 185, 129, 0.15); color: #10b981; }

.severity-badge.danger { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.severity-badge.warning { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }

.realtime-data {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap:  8px;
  height: 250px;
  overflow-y: auto;
  overflow-x: hidden;
  align-content: start;
}

.realtime-data::-webkit-scrollbar {
  width: 4px;
}

.realtime-data::-webkit-scrollbar-track {
  background: rgba(10, 22, 40, 0.3);
  border-radius: 2px;
}

.realtime-data::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.3);
  border-radius: 2px;
}

.realtime-data::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.5);
}

.data-item {
  padding: 8px;
  background: rgba(10, 22, 40, 0.1);
  border-radius: 4px;
  text-align: center;
  /* height: 76px; */
  min-height: 60px;
  overflow: hidden;
}

.data-label {
  font-size: 10px;
  color: white;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.data-value {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.data-value.large {
  font-size: 18px;
}

.data-status {
  font-size: 10px;
  color: white;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.data-value.danger { color: #ef4444; }
.data-value.warning { color: #f59e0b; }
.data-status.danger { color: #ef4444; }
.data-status.warning { color: #f59e0b; }

.health-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: white;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #34d399);
  border-radius: 4px;
  transition: width 0.5s;
}

.health-value {
  font-weight: 700;
}

.green-text { color: #10b981; }
.warning-text { color: #f59e0b; }
.danger-text { color: #ef4444; }

.update-time {
  font-size: 10px;
  color: #6b7280;
  text-align: right;
}

.diagnosis-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  /* background: rgba(10, 22, 40, 0.4); */
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 6px;
  overflow-y: auto;
}

.diagnosis-result::-webkit-scrollbar {
  width: 4px;
}

.diagnosis-result::-webkit-scrollbar-track {
  background: rgba(10, 22, 40, 0.3);
  border-radius: 2px;
}

.diagnosis-result::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.3);
  border-radius: 2px;
}

.diagnosis-result::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.5);
}

.result-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #3b82f6;
  font-weight: 600;
}

.confidence-badge {
  font-size: 12px;
  font-weight: 700;
  color: #10b981;
}

.confidence-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  margin-bottom: 8px;
}

.confidence-label {
  font-size: 11px;
  color: white;
  white-space: nowrap;
}

.confidence-track {
  flex: 1;
  height: 6px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.confidence-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #10b981);
  border-radius: 3px;
  transition: width 0.5s;
}

.confidence-value {
  font-size: 12px;
  font-weight: 700;
  color: #10b981;
  white-space: nowrap;
}

.result-section {
  margin-top: 12px;
}

.result-section h5 {
  font-size: 12px;
  color: rgba(255,255,255,0.8);
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.cause-item,
.conclusion-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  /* background: rgba(255,255,255,0.03); */
  /* border-left: 2px solid rgba(255,255,255,0.15); */
  border-radius: 0 6px 6px 0;
  margin-bottom: 6px;
}

.cause-item svg,
.conclusion-item svg {
  color: rgba(255,255,255,0.4);
  flex-shrink: 0;
  margin-top: 1px;
}

.cause-item.danger svg,
.conclusion-item svg {
  color: rgba(251, 146, 60, 0.7);
}

.cause-item span,
.conclusion-item span {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  font-weight: 500;
}

.cause-desc,
.conclusion-desc {
  font-size: 11px;
  color: rgba(255,255,255,0.45);
  line-height: 1.6;
  margin-top: 4px;
  padding-left: 10px;
}

.suggestion-list {
  list-style: none;
  padding-left: 10px;
}

.suggestion-list li {
  font-size: 11px;
  color: rgba(255,255,255,0.7);
  padding: 6px 0 6px 14px;
  position: relative;
  line-height: 1.5;
}

.suggestion-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  width: 5px;
  height: 5px;
  background: rgba(59, 130, 246, 0.5);
  border-radius: 50%;
}

.dispatch-info {
  flex: 0.8;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  /* background: rgba(10, 22, 40, 0.4); */
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 6px;
}

.dispatch-title {
  font-size: 12px;
  color: #3b82f6;
  font-weight: 600;
}

.dispatch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  gap: 50px;
}

.dispatch-row .label {
  color: white;
  white-space: nowrap;
  flex-shrink: 0;
}

.order-title {
  color: #e0e8f0;
  font-size: 12px;
  word-break: break-all;
  text-align: left;
}

.order-no {
  color: #a0b0c0;
  font-size: 12px;
  font-family: monospace;
}

.order-status-badge {
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 4px;
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.dispatch-row .tools-list {
  flex: 1;
  word-break: break-all;
  text-align: right;
}

.engineer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.engineer svg {
  color: #3b82f6;
}

.engineer b {
  display: block;
  color: #fff;
  font-size: 12px;
}

.engineer small {
  display: block;
  color: white;
  font-size: 10px;
  margin-top: 2px;
}

.online-badge {
  padding: 2px 8px;
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border-radius: 3px;
  font-size: 10px;
}

.priority-badge {
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}

.priority-badge.danger {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.skill-match {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.skill-match-bar {
  flex: 1;
  height: 6px;
  background: rgba(245, 158, 11, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.skill-match-fill {
  height: 100%;
  background: linear-gradient(90deg, #f59e0b, #10b981);
  border-radius: 3px;
  transition: width 0.5s;
}

.skill-match-value {
  font-size: 12px;
  font-weight: 700;
  color: #f59e0b;
  white-space: nowrap;
}

.tools-list {
  font-size: 11px;
    max-width: 200px;
  color: #c0d0e0;
    white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-desc {
  max-width: 200px;
  font-size: 11px;
  color: #c0d0e0;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dispatch-row svg {
  color: white;
  margin-right: 4px;
}

.dispatch-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: auto;
}

.dispatch-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.dispatch-btn svg {
  flex-shrink: 0;
}

.dispatch-btn b {
  display: block;
  font-size: 14px;
}

.dispatched-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.3);
  border-radius: 6px;
  color: #10b981;
  font-size: 13px;
  font-weight: 500;
  margin-top: auto;
}

.dispatch-btn small {
  font-size: 10px;
  opacity: 0.8;
}

.switch-engineer-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  color: #3b82f6;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.switch-engineer-btn:hover {
  background: rgba(59, 130, 246, 0.25);
  border-color: rgba(59, 130, 246, 0.5);
}

/* 用户回填弹窗 */
.feedback-overlay {
position: fixed;
top: 0;
left: 0;
width: 100vw;
height: 100vh;
background: rgba(0, 0, 0, 0.5);
display: flex;
align-items: center;
justify-content: center;
z-index: 3500;
}

.feedback-modal {
width: 460px;
background: linear-gradient(135deg, #0a1628, #0f1e36);
border: 1px solid rgba(59, 130, 246, 0.2);
border-radius: 14px;
overflow: hidden;
box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
}

.feedback-header {
display: flex;
align-items: center;
justify-content: space-between;
padding: 14px 20px;
border-bottom: 1px solid rgba(59, 130, 246, 0.15);
font-size: 15px;
font-weight: 700;
color: #c0d0e0;
}

.feedback-header .modal-close {
background: transparent;
border: 1px solid rgba(255,255,255,0.15);
border-radius: 4px;
cursor: pointer;
color: #a0b0c0;
font-size: 18px;
padding: 0 8px;
line-height: 1;
}

.feedback-header .modal-close:hover {
background: rgba(239, 68, 68, 0.2);
color: #ef4444;
}

.feedback-body {
padding: 20px;
}

.feedback-row {
margin-bottom: 16px;
}

.feedback-label {
display: block;
font-size: 13px;
color: rgba(160, 180, 200, 0.8);
margin-bottom: 8px;
}

.feedback-select {
width: 100%;
padding: 8px 12px;
background: rgba(10, 22, 40, 0.6);
border: 1px solid rgba(59, 130, 246, 0.25);
border-radius: 6px;
color: #c0d0e0;
font-size: 13px;
outline: none;
cursor: pointer;
}

.feedback-select option {
background: #0a1628;
color: #c0d0e0;
}

.feedback-textarea {
width: 100%;
padding: 10px 12px;
background: rgba(10, 22, 40, 0.6);
border: 1px solid rgba(59, 130, 246, 0.25);
border-radius: 6px;
color: #c0d0e0;
font-size: 13px;
outline: none;
resize: vertical;
font-family: inherit;
line-height: 1.6;
}

.feedback-textarea::placeholder {
color: rgba(160, 180, 200, 0.4);
}

.feedback-textarea:focus {
border-color: rgba(59, 130, 246, 0.5);
}

.feedback-footer {
display: flex;
justify-content: flex-end;
gap: 10px;
padding: 0 20px 20px;
}

.feedback-btn-cancel {
padding: 8px 20px;
background: transparent;
border: 1px solid rgba(160, 180, 200, 0.25);
border-radius: 6px;
color: #a0b0c0;
font-size: 13px;
cursor: pointer;
transition: all 0.2s;
}

.feedback-btn-cancel:hover {
background: rgba(160, 180, 200, 0.1);
}

.feedback-btn-confirm {
padding: 8px 20px;
background: linear-gradient(135deg, #1868ff, #00d4ff);
border: none;
border-radius: 6px;
color: #fff;
font-size: 13px;
font-weight: 600;
cursor: pointer;
transition: all 0.2s;
}

.feedback-btn-confirm:hover {
box-shadow: 0 0 16px rgba(18, 104, 255, 0.4);
}

.feedback-btn-confirm:disabled {
opacity: 0.6;
cursor: not-allowed;
}

.engineer-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 500px;
  max-height: 80vh;
  background: #0f1d30;
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 12px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.2);
  font-size: 16px;
  font-weight: 600;
}

.modal-close {
  background: none;
  border: none;
  color: white;
  font-size: 24px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.modal-close:hover {
  color: #fff;
}

.modal-body {
  padding: 16px 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.engineer-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.engineer-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.engineer-item:hover {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

.engineer-item.selected {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.5);
}

.engineer-avatar {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 50%;
  color: #3b82f6;
  flex-shrink: 0;
}

.engineer-details {
  flex: 1;
  min-width: 0;
}

.engineer-name {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.engineer-skill {
  font-size: 12px;
  color: white;
  margin-top: 2px;
}

.engineer-contact {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #667788;
  margin-top: 4px;
}

.engineer-check {
  flex-shrink: 0;
}

/* Bottom层 */
.bottom {
  height: 26vh;
  min-height: 160px;
  display: flex;
  gap: 12px;
  padding: 12px 20px;
  overflow: hidden;
}

.bottom-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* background: linear-gradient(135deg, rgba(20, 40, 70, 0.6), rgba(15, 30, 55, 0.6)); */
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: rgba(10, 22, 40, 0.1);
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
}

.card-icon {
  color: #3b82f6;
}

.card-stats {
  display: flex;
  gap: 16px;
  padding: 10px 14px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 10px;
  color: white;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.stat-value small {
  font-size: 11px;
  font-weight: 400;
  color: white;
}

.card-charts {
  flex: 1;
  display: flex;
  gap: 10px;
  padding: 0 14px 10px;
}

.chart-box {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chart-title {
  font-size: 10px;
  color: white;
  margin-bottom: 6px;
}

.chart-placeholder {
  flex: 1;
  min-height: 0;
}

.sankey-chart {
  min-height: 180px;
}

.chart-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 180px;
  gap: 10px;
}

.chart-loading .loading-text {
  color: white;
  font-size: 12px;
}

/* 资产状态统计 */
.device-stats {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 14px;
}

.device-total {
  flex-shrink: 0;
}

.total-ring {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: conic-gradient(#10b981 calc(var(--p) * 1%), rgba(59, 130, 246, 0.1) 0);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.total-ring::before {
  content: '';
  position: absolute;
  width: 60px;
  height: 60px;
  background: #0a1628;
  border-radius: 50%;
}

.total-ring span {
  position: relative;
  text-align: center;
  z-index: 1;
}

.total-ring b {
  display: block;
  font-size: 16px;
  color: #fff;
}

.total-ring small {
  font-size: 9px;
  color: white;
}

.device-legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #c0d0e0;
}

.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-item .dot.green { background: #10b981; }
.legend-item .dot.blue { background: #3b82f6; }
.legend-item .dot.orange { background: #f59e0b; }
.legend-item .dot.red { background: #ef4444; }
.legend-item .dot.gray { background: #6b7280; }

/* 历史告警 */
.history-content {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 10px 14px;
}

.history-stats {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-total {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: rgba(10, 22, 40, 0.4);
  border-radius: 6px;
}

.history-total svg {
  color: #ef4444;
}

.total-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}

.total-value small {
  font-size: 12px;
  color: white;
}

.total-trend {
  font-size: 11px;
}

.history-chart {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.history-trend {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.3);
  border-radius: 2px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.5);
}

/* 更多按钮 */
.more-btn {
  margin-left: auto;
  padding: 4px 12px;
  background: rgba(59, 130, 246, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  color: #e4e7eb;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.more-btn:hover {
  background: rgba(59, 130, 246, 0.25);
}

@keyframes flash-pulse {
  0%, 100% { opacity: 1; transform: scale(1); box-shadow: 0 0 0px rgba(59, 130, 246, 0); border-color: rgba(59, 130, 246, 0.3); }
  50% { opacity: 0.5; transform: scale(1.05); box-shadow: 0 0 12px rgba(59, 130, 246, 0.6); border-color: #3b82f6; }
}

.more-btn.flash-animation {
  animation: flash-pulse 0.5s ease-in-out 3;
}

/* 维修工单状态柱状图 */
.order-chart-container {
  flex: 1;
  min-height: 0;
  position: relative;
}
.order-status {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  margin-left: auto;
}
.order-status.status-10 {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
}
.order-status.status-20 {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
}
.order-status.status-30 {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
}
.order-status.status-40 {
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
}
.order-status.status-50 {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
}

/* iframe 全屏弹窗 */
.iframe-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.8);
  z-index: 9999;
}

.iframe-container {
  position: relative;
  width: 100%;
  height: 100%;
}

.iframe-container iframe {
  width: 100%;
  height: 100%;
  background: #fff;
}

.iframe-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  font-size: 28px;
  cursor: pointer;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.iframe-close:hover {
  background: rgba(239, 68, 68, 0.8);
}

/* 工单详情弹窗 */
.order-detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.7);
  z-index: 9998;
  display: flex;
  align-items: center;
  justify-content: center;
}

.order-detail-modal {
  width: 520px;
  max-height: 80vh;
  background: linear-gradient(135deg, #1a2a44 0%, #0f1d32 100%);
  border: 1px solid rgba(0, 180, 255, 0.3);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.order-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 180, 255, 0.2);
  font-size: 16px;
  font-weight: 700;
  color: #e0e8f0;
}

.order-detail-body {
  padding: 20px;
  overflow-y: auto;
  max-height: calc(80vh - 60px);
}

.detail-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  flex-shrink: 0;
  width: 90px;
  color: white;
  font-size: 14px;
}

.detail-value {
  flex: 1;
  color: #e0e8f0;
  font-size: 14px;
  word-break: break-all;
}

/* AI诊断与派单放大弹窗 */
.diagnosis-zoom-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  overflow-y: auto;
  padding: 40px 20px;
}

.diagnosis-zoom-content {
  position: relative;
  width: 90vw;
  max-width: 1600px;
  min-height: 80vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.zoom-close {
  margin-left: auto;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.zoom-close:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
}

.diagnosis-zoom-inner {
  width: 100%;
  overflow: visible;
}

.diagnosis-panel.zoomed {
  width: 100%;
  transform: scale(1.15);
  transform-origin: top center;
  font-size: 16px;
  overflow: visible;
  background: rgb(0, 60, 126);
  max-height: 100vh;
}

.diagnosis-panel.zoomed .panel-header {
  font-size: 18px;
  padding: 14px 20px;
}

.diagnosis-panel.zoomed .panel-icon {
  width: 24px;
  height: 24px;
}

.diagnosis-panel.zoomed .diagnosis-content {
  padding: 16px;
  gap: 16px;
  overflow: hidden;
}

.diagnosis-panel.zoomed .overview-title {
  font-size: 15px;
}

.diagnosis-panel.zoomed .device-info h4 {
  font-size: 18px;
}

.diagnosis-panel.zoomed .device-info p {
  font-size: 14px;
}

.diagnosis-panel.zoomed .data-label {
  font-size: 13px;
}

.diagnosis-panel.zoomed .data-value {
  font-size: 16px;
}

.diagnosis-panel.zoomed .data-value.large {
  font-size: 22px;
}

.diagnosis-panel.zoomed .result-title {
  font-size: 15px;
}

.diagnosis-panel.zoomed .result-section h5 {
  font-size: 14px;
}

.diagnosis-panel.zoomed .result-section p,
.diagnosis-panel.zoomed .result-section span {
  font-size: 13px;
}

.diagnosis-panel.zoomed .dispatch-title {
  font-size: 15px;
}

.diagnosis-panel.zoomed .dispatch-row .label {
  font-size: 13px;
}

.diagnosis-panel.zoomed .dispatch-row b {
  font-size: 15px;
}

.diagnosis-panel.zoomed .dispatch-btn {
  font-size: 15px;
}

.diagnosis-panel.zoomed .health-bar {
  font-size: 14px;
}

.diagnosis-panel.zoomed .health-value {
  font-size: 18px;
}

.diagnosis-panel.zoomed .empty-hint {
  font-size: 14px;
}

.diagnosis-panel.zoomed .thinking-text {
  font-size: 14px;
}

.diagnosis-panel.zoomed .confidence-badge {
  font-size: 13px;
}

.diagnosis-panel.zoomed .priority-badge {
  font-size: 13px;
}

.diagnosis-panel.zoomed .dispatched-hint {
  font-size: 14px;
}

.diagnosis-panel.zoomed .device-meta {
  font-size: 13px;
}

.diagnosis-panel.zoomed .severity-badge,
.diagnosis-panel.zoomed .status-badge {
  font-size: 13px;
}

.diagnosis-panel.zoomed .skill-desc {
  font-size: 13px;
}

.diagnosis-panel.zoomed .tools-list {
  font-size: 13px;
}

.diagnosis-panel.zoomed .meta-label {
  font-size: 13px;
}

.diagnosis-panel.zoomed .meta-value {
  font-size: 13px;
}

.diagnosis-panel.zoomed .conclusion-desc {
  font-size: 13px;
}

.diagnosis-panel.zoomed .cause-desc {
  font-size: 13px;
}

.diagnosis-panel.zoomed .dispatch-btn small {
  font-size: 12px;
}

.diagnosis-panel.zoomed .switch-engineer-btn {
  display: none;
}

.diagnosis-panel.zoomed .engineer svg {
  width: 40px;
  height: 40px;
}

.diagnosis-panel.zoomed .model-placeholder svg {
  width: 140px;
  height: 120px;
}

.diagnosis-panel.zoomed .bar-track {
  height: 10px;
}

.diagnosis-panel.zoomed .thinking-dots span {
  width: 10px;
  height: 10px;
}
</style>
