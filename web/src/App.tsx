import './App.css'

const engineeringTracks = [
  '控制台页面统一落在 web/ 目录',
  '前端技术栈固定为 React + TypeScript + Bun',
  '首版先提供工程骨架，不直接耦合后端 API',
  '后续管理台、运维台、平台配置页都从这里扩展',
]

const nextSlices = [
  '补应用路由和页面分区',
  '接入统一 API client 与环境配置',
  '沉淀基础布局、表单和数据展示组件',
  '按 Linear / Notion 任务逐步接入真实控制台能力',
]

const localCommands = [
  'bun install',
  'bun run dev',
  'bun run typecheck',
  'bun run build',
]

function App() {
  return (
    <div className="app-shell">
      <header className="hero">
        <p className="eyebrow">web / React + TypeScript + Bun</p>
        <div className="hero-copy">
          <span className="status-pill">x-ai-gateway control plane</span>
          <h1>前端控制台工程已经在这里落位。</h1>
          <p className="lead">
            当前页面是 <code>web/</code> 的工程基线。后续后台管理台、运维指挥台、账号池配置页和平台扩展面板都从这里继续生长。
          </p>
        </div>
        <div className="stack-bar">
          <span>React 19</span>
          <span>TypeScript 6</span>
          <span>Vite 8</span>
          <span>Bun 1.3.12</span>
        </div>
      </header>

      <main className="content-grid">
        <section className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">工程约定</p>
            <h2>这次脚手架先把边界定清楚。</h2>
          </div>
          <ul className="check-list">
            {engineeringTracks.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>

        <section className="panel">
          <div className="panel-head">
            <p className="panel-kicker">本地命令</p>
            <h2>最小开发回路</h2>
          </div>
          <div className="command-stack">
            {localCommands.map((item) => (
              <code key={item}>{item}</code>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <p className="panel-kicker">下一步</p>
            <h2>继续往控制台方向扩</h2>
          </div>
          <ol className="step-list">
            {nextSlices.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ol>
        </section>

        <section className="panel panel-wide panel-accent">
          <div className="panel-head">
            <p className="panel-kicker">工程定位</p>
            <h2>前端先做稳，再接业务。</h2>
          </div>
          <p className="accent-copy">
            这一版不急着把页面数量堆上去，先把运行时、脚本、TypeScript、样式基线和目录边界收口。这样后面接
            `x-ai-gateway` 的后台接口时，前端不会再经历一次二次迁移。
          </p>
        </section>
      </main>
    </div>
  )
}

export default App
