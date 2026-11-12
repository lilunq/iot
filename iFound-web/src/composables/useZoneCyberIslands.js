/**
 * useZoneCyberIslands —— 仪表盘分区态势 Canvas 2D 赛博小岛渲染
 *
 * 设计要点：
 * - 纯 2D，Y 轴 0.5 压缩做假透视
 * - 节点位置：第一个区域居中，其余围绕中心360°均匀分布
 * - 所有子区域都有连线连接到中心区域 + 流粒子
 * - 配色严格沿用 Dashboard 现有 CSS 变量（--blue / --cyan / --green / --orange / --soft）
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { buildZoneCardSnapshot } from '@/utils/zoneSituation'

// 与 Dashboard.vue 中的 STATUS_COLORS 保持一致
const STATUS_COLORS = {
  ok: { color: '#08ae66', glow: 'rgba(0,255,204,0.45)' },
  warn: { color: '#ff6a00', glow: 'rgba(255,106,0,0.45)' }
}

// Dashboard 全局 CSS 变量（写死为变量解析后的值，避免运行时读取 computed style）
const COLORS = {
  bgGrid: 'rgba(0, 162, 255, 0.10)',
  bgGridStrong: 'rgba(0, 162, 255, 0.18)',
  cyan: '#00e5ff',
  blue: '#00d8ff',
  blueGlow: '#00f6ff',
  green: '#00ffcc',
  greenFill: 'rgba(0,255,204,0.18)',
  orange: '#ff9933',
  orangeBright: '#ff6a00',
  orangeFill: 'rgba(255,106,0,0.20)',
  text: '#ffffff',
  labelShadow: 'rgba(0, 8, 20, 0.85)'
}

/**
 * 把 Dashboard 实时 zoneAreas 状态融合成画布节点。
 * 位置计算：第一个区域居中 (0,0)，其余区域围绕中心半径 100 的圆周上均匀分布。
 */
function buildCanvasNodes(zoneAreas) {
  if (!Array.isArray(zoneAreas) || zoneAreas.length === 0) return []

  const radius = 100
  return zoneAreas.map((z, index) => {
    let x, y
    if (index === 0) {
      // 第一个区域：居中
      x = 0
      y = 0
    } else {
      // 其余区域：围绕中心360°均匀分布，从 -90°（正上方）起顺时针
      const angle = ((index - 1) / (zoneAreas.length - 1)) * Math.PI * 2 - Math.PI / 2
      x = Math.cos(angle) * radius
      y = Math.sin(angle) * radius
    }

    const key = z._rawSubareaCid || z.name
    const snapshot = buildZoneCardSnapshot(z)
    const liveHeat = z.heat || null
    const status = liveHeat?.type || 'ok'
    const color = liveHeat?.color || STATUS_COLORS[status]?.color || COLORS.cyan

    return {
      id: z.id || index + 1,
      name: z.name,
      cascadeId: z._rawSubareaCid || '',
      x,
      y,
      parentId: index === 0 ? 0 : 1, // 所有子区域都连接到第一个区域
      status,
      color,
      totalScore: snapshot.totalScore,
      anomalyTotal: snapshot.anomalyTotal,
      liveRef: z,
      // 动效参数
      floatOffset: Math.random() * Math.PI * 2,
      floatSpeed: 0.6 + Math.random() * 0.6,
      // 用于粒子流错开
      flowOffset: Math.random()
    }
  })
}

/**
 * 计算节点的屏幕坐标。
 * - 自动适配 zoneListCache 数据的 X/Y 范围到画布内（边距 60px）
 * - 只用 X/Y，不再做 Y 轴 0.5 压缩的假透视
 */
function buildProjection(nodes, w, h) {
  if (!nodes.length) {
    return { toScreen: () => ({ x: 0, y: 0 }), bounds: null }
  }
  const padX = 70, padY = 70
  const xs = nodes.map(n => n.x)
  const ys = nodes.map(n => n.y)
  const minX = Math.min(...xs), maxX = Math.max(...xs)
  const minY = Math.min(...ys), maxY = Math.max(...ys)
  const rangeX = Math.max(maxX - minX, 0.001)
  const rangeY = Math.max(maxY - minY, 0.001)
  const usableW = w - padX * 2
  const usableH = h - padY * 2

  const toScreen = (x, y) => ({
    x: padX + ((x - minX) / rangeX) * usableW,
    y: padY + ((y - minY) / rangeY) * usableH
  })

  return { toScreen, bounds: { minX, maxX, minY, maxY } }
}

/**
 * 把 (x, y) → 屏幕坐标 并写回节点上，避免每帧重算。
 */
function projectNodes(nodes, w, h) {
  const { toScreen } = buildProjection(nodes, w, h)
  nodes.forEach(n => {
    const s = toScreen(n.x, n.y)
    n.sx = s.x
    n.sy = s.y
  })
  return nodes
}

/** 画网格底 */
function drawGrid(ctx, w, h, renderTime) {
  ctx.strokeStyle = COLORS.bgGrid
  ctx.lineWidth = 1
  const step = 40
  // 微动：网格整体随时间上下偏移 1px，模拟呼吸
  const drift = Math.sin(renderTime * 0.005) * 0.6
  ctx.beginPath()
  for (let x = (drift % step); x < w; x += step) {
    ctx.moveTo(x, 0); ctx.lineTo(x, h)
  }
  for (let y = (drift % step); y < h; y += step) {
    ctx.moveTo(0, y); ctx.lineTo(w, y)
  }
  ctx.stroke()
}

/** 画辐射连线 + 流粒子（parent → child） */
function drawConnectingLines(ctx, nodes, renderTime) {
  const byId = new Map(nodes.map(n => [Number(n.id), n]))
  nodes.forEach(child => {
    if (!child.parentId) return
    const parent = byId.get(Number(child.parentId))
    if (!parent || parent.sx == null) return

    // 连线
    const opacity = 0.30 + Math.sin(renderTime * 0.03 + child.floatOffset) * 0.08
    ctx.strokeStyle = `rgba(0, 216, 255, ${opacity.toFixed(3)})`
    ctx.lineWidth = 1.2
    ctx.setLineDash([4, 3])
    ctx.beginPath()
    ctx.moveTo(parent.sx, parent.sy)
    ctx.lineTo(child.sx, child.sy)
    ctx.stroke()
    ctx.setLineDash([])

    // 流粒子
    const p = (renderTime * 0.005 + child.flowOffset) % 1.0
    const lx = parent.sx + (child.sx - parent.sx) * p
    const ly = parent.sy + (child.sy - parent.sy) * p
    ctx.save()
    ctx.shadowColor = COLORS.blue
    ctx.shadowBlur = 6
    ctx.fillStyle = COLORS.blue
    ctx.beginPath()
    ctx.arc(lx, ly, 2.5, 0, Math.PI * 2)
    ctx.fill()
    ctx.restore()
  })
}

/** 画一个节点：六边形小岛 + 内部条纹 + 文字 + 状态徽章 */
function drawNode(ctx, node, renderTime, isSelected) {
  const { sx, sy, color, status, name, totalScore, anomalyTotal, parentId } = node
  const isCenter = parentId === 0 // 中心节点标识
  // 浮动偏移
  const floatY = Math.sin(renderTime * node.floatSpeed * 0.02 + node.floatOffset) * (isCenter ? 6 : 4)
  const cx = sx
  const cy = sy + floatY

  // 节点六边形大小：中心节点更大
  const baseSize = isCenter ? 60 : Math.max(28, Math.min(46, (anomalyTotal || 0) * 4 + 28))
  const halfW = baseSize
  const halfH = baseSize * 0.5  // Y 轴 0.5 压缩

  // 警告态呼吸
  const isWarn = status === 'warn'
  const breath = isWarn ? (Math.sin(renderTime * 0.12) * 0.25 + 0.75) : 1

  // 外发光
  ctx.save()
  ctx.shadowColor = color
  ctx.shadowBlur = isSelected ? 18 : (isWarn ? 12 : (isCenter ? 16 : 8))

  // 中心节点3D效果：先画侧面（底部偏移）
  if (isCenter) {
    const sideDepth = 12 // 3D侧面深度
    ctx.beginPath()
    for (let i = 0; i < 6; i++) {
      const a = (Math.PI / 3) * i + Math.PI / 6
      const x = cx + halfW * Math.cos(a)
      const y = cy + halfH * Math.sin(a) + sideDepth
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.fillStyle = isWarn ? 'rgba(255,106,0,0.12)' : 'rgba(0,255,204,0.08)'
    ctx.fill()
    ctx.strokeStyle = isWarn ? 'rgba(255,106,0,0.3)' : 'rgba(0,229,255,0.25)'
    ctx.lineWidth = 1
    ctx.stroke()

    // 画侧面连接线（顶面到底面）
    ctx.strokeStyle = isWarn ? 'rgba(255,106,0,0.2)' : 'rgba(0,229,255,0.15)'
    ctx.lineWidth = 1
    for (let i = 0; i < 6; i++) {
      const a = (Math.PI / 3) * i + Math.PI / 6
      const topX = cx + halfW * Math.cos(a)
      const topY = cy + halfH * Math.sin(a)
      const bottomX = topX
      const bottomY = topY + sideDepth
      ctx.beginPath()
      ctx.moveTo(topX, topY)
      ctx.lineTo(bottomX, bottomY)
      ctx.stroke()
    }
  }

  // 顶面六边形
  ctx.beginPath()
  for (let i = 0; i < 6; i++) {
    const a = (Math.PI / 3) * i + Math.PI / 6
    const x = cx + halfW * Math.cos(a)
    const y = cy + halfH * Math.sin(a)
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
  }
  ctx.closePath()
  // 填充：正常绿 / 警告橙 / 选中蓝
  if (isSelected) {
    ctx.fillStyle = 'rgba(0, 246, 255, 0.30)'
  } else if (isWarn) {
    ctx.fillStyle = COLORS.orangeFill
  } else {
    ctx.fillStyle = isCenter ? 'rgba(0,255,204,0.25)' : COLORS.greenFill
  }
  ctx.fill()
  // 描边
  ctx.strokeStyle = isSelected ? COLORS.blueGlow : (isWarn ? COLORS.orangeBright : (isCenter ? COLORS.cyan : COLORS.cyan))
  ctx.lineWidth = isSelected ? 2.5 : (isCenter ? 2.2 : 1.8)
  ctx.stroke()
  ctx.restore()

  // 内部科幻条纹（5 条横线，10% 透明）
  ctx.save()
  ctx.strokeStyle = isWarn ? `rgba(255,106,0,${0.18 * breath})` : `rgba(0,229,255,0.18)`
  ctx.lineWidth = 1
  ctx.beginPath()
  for (let d = -2; d <= 2; d++) {
    const yy = cy + d * (halfH / 3)
    ctx.moveTo(cx - halfW * 0.65, yy)
    ctx.lineTo(cx + halfW * 0.65, yy)
  }
  ctx.stroke()
  ctx.restore()

  // 中央状态圆点
  ctx.save()
  ctx.shadowColor = color
  ctx.shadowBlur = isWarn ? 8 : (isCenter ? 6 : 4)
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(cx, cy, isWarn ? 3.5 : (isCenter ? 4 : 2.5), 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()

  // 文字标签（节点下方）
  ctx.save()
  ctx.font = isCenter ? 'bold 16px "PingFang SC", "Microsoft YaHei", sans-serif' : 'bold 13px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  // 阴影
  ctx.shadowColor = COLORS.labelShadow
  ctx.shadowBlur = 4
  ctx.fillStyle = COLORS.text
  ctx.fillText(name, cx, cy + halfH + 8)

  // 副标题：综合评分 + 需关注项
  ctx.shadowBlur = 0
  ctx.font = isCenter ? '13px "PingFang SC", "Microsoft YaHei", sans-serif' : '11px "PingFang SC", "Microsoft YaHei", sans-serif'
  const scoreText = totalScore != null ? `${totalScore}分` : '-'
  const anomalyText = anomalyTotal > 0 ? `${anomalyTotal}项需关注` : '正常'
  ctx.fillStyle = COLORS.cyan
  ctx.fillText(scoreText, cx, cy + halfH + (isCenter ? 30 : 26))
  ctx.fillStyle = anomalyTotal > 0 ? COLORS.orange : COLORS.cyan
  ctx.fillText(anomalyText, cx, cy + halfH + (isCenter ? 48 : 42))
  ctx.restore()

  // 警告态呼吸外环（叠加在六边形外围）
  if (isWarn) {
    ctx.save()
    ctx.strokeStyle = `rgba(255,106,0,${0.4 * breath})`
    ctx.lineWidth = 1.5
    ctx.beginPath()
    for (let i = 0; i < 6; i++) {
      const a = (Math.PI / 3) * i + Math.PI / 6
      const x = cx + (halfW + 6) * Math.cos(a)
      const y = cy + (halfH + 3) * Math.sin(a)
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.stroke()
    ctx.restore()
  }
}

/** 在 (px, py) 处找到被点击的节点；找不到返回 null */
function hitTestNode(nodes, px, py) {
  // 倒序遍历，匹配最上层的节点
  for (let i = nodes.length - 1; i >= 0; i--) {
    const n = nodes[i]
    const dx = px - n.sx
    const dy = py - n.sy
    // 中心节点更大，使用更大的点击区域
    const isCenter = n.parentId === 0
    const halfW = isCenter ? 65 : 30
    const halfH = isCenter ? 35 : 18
    if (Math.abs(dx) <= halfW && Math.abs(dy) <= halfH) return n
  }
  return null
}

/**
 * useZoneCyberIslands —— 单实例渲染（main 或 modal 二选一）
 *
 * @param {Ref<HTMLCanvasElement>} canvasRef —— <canvas> 元素的 ref
 * @param {Ref|Function} liveAreasGetter —— 既可以传 zoneAreas 的 ref，也可以传 () => zoneAreas.value 的 getter（避免 setup 阶段 TDZ）
 * @param {Function} onNodeClick —— 点击节点回调 (canvasNode) => void
 */
export function useZoneCyberIslands(canvasRef, liveAreasGetter, onNodeClick) {
  // 兼容 ref 或 getter 函数
  const readLiveAreas = () => {
    if (typeof liveAreasGetter === 'function') return liveAreasGetter() || []
    return liveAreasGetter?.value || []
  }
  const canvasNodes = computed(() => buildCanvasNodes(readLiveAreas()))
  const selectedNodeId = ref(null)
  const isScanningNodeId = ref(null)  // 用于 triggerPanelScan 高亮

  let rafId = null
  let renderTime = 0
  let resizeObs = null

  function start() {
    const canvas = canvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    function resize() {
      // 重要：不要写 canvas.style.width/height —— 会覆盖 CSS width:100% 让画布塌成 1px
      // 只设置 canvas.width/height（实际像素缓冲区，不影响 CSS 布局）
      const cssW = canvas.clientWidth || canvas.getBoundingClientRect().width
      const cssH = canvas.clientHeight || canvas.getBoundingClientRect().height
      const dpr = window.devicePixelRatio || 1
      const pxW = Math.max(1, Math.floor(cssW * dpr))
      const pxH = Math.max(1, Math.floor(cssH * dpr))
      if (canvas.width !== pxW) canvas.width = pxW
      if (canvas.height !== pxH) canvas.height = pxH
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      return { w: cssW, h: cssH }
    }
    let dims = resize()
    // 如果首帧尺寸过小（v-show 隐藏切换后立即调用），下一帧再 resize 等布局完成
    if (dims.w < 10 || dims.h < 10) {
      requestAnimationFrame(() => {
        dims = resize()
      })
    }

    // 监听容器尺寸变化
    if (typeof ResizeObserver !== 'undefined') {
      resizeObs = new ResizeObserver(() => resize())
      resizeObs.observe(canvas)
    }

    function draw() {
      rafId = requestAnimationFrame(draw)
      renderTime += 1
      // 实时读最新 CSS 尺寸（clientWidth 不被 style.width 污染）
      const cssW = canvas.clientWidth
      const cssH = canvas.clientHeight
      if (cssW < 10 || cssH < 10) return
      // 同步 canvas 像素缓冲区与 CSS 尺寸（DPR），但不动 style.width/height
      const dpr = window.devicePixelRatio || 1
      const targetW = Math.floor(cssW * dpr)
      const targetH = Math.floor(cssH * dpr)
      if (canvas.width !== targetW || canvas.height !== targetH) {
        canvas.width = targetW
        canvas.height = targetH
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      }
      const w = cssW, h = cssH

      ctx.clearRect(0, 0, w, h)
      drawGrid(ctx, w, h, renderTime)

      const nodes = canvasNodes.value
      if (!nodes.length) {
        // 空状态提示
        ctx.save()
        ctx.fillStyle = 'rgba(0, 216, 255, 0.5)'
        ctx.font = '13px "PingFang SC", "Microsoft YaHei", sans-serif'
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText('请在「区域配置」中启用并保存至少一个分区', w / 2, h / 2)
        ctx.restore()
        return
      }

      projectNodes(nodes, w, h)
      drawConnectingLines(ctx, nodes, renderTime)
      nodes.forEach(n => {
        const isSelected = n.id === selectedNodeId.value
        const isScanning = n.id === isScanningNodeId.value
        drawNode(ctx, n, renderTime, isSelected || isScanning)
      })
    }
    draw()

    // 点击事件
    canvas.onclick = (e) => {
      const rect = canvas.getBoundingClientRect()
      const px = e.clientX - rect.left
      const py = e.clientY - rect.top
      const nodes = canvasNodes.value
      const hit = hitTestNode(nodes, px, py)
      if (hit) {
        onNodeClick && onNodeClick(hit)
      }
    }
  }

  function stop() {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    if (resizeObs) {
      resizeObs.disconnect()
      resizeObs = null
    }
    if (canvasRef.value) {
      canvasRef.value.onclick = null
    }
  }

  /** 高亮某个节点 2 秒（替代原 triggerPanelScan） */
  function pulseNode(nodeId) {
    isScanningNodeId.value = nodeId
    setTimeout(() => {
      if (isScanningNodeId.value === nodeId) isScanningNodeId.value = null
    }, 2000)
  }

  onMounted(() => {
    // 不再监听 zone-config-changed，位置数据直接来自 zoneAreas
  })

  onBeforeUnmount(() => {
    stop()
  })

  return {
    canvasNodes,
    selectedNodeId,
    start,
    stop,
    pulseNode
  }
}
