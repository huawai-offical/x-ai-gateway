import { useSearchParams } from 'react-router-dom'

export function OauthCallbackPage() {
  const [searchParams] = useSearchParams()
  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">OAuth callback</p>
          <h2>授权回调结果</h2>
        </div>
        <div className="code-block">
          <pre>{JSON.stringify(Object.fromEntries(searchParams.entries()), null, 2)}</pre>
        </div>
      </div>
    </section>
  )
}
