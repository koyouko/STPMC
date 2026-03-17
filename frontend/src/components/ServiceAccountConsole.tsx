import { useState } from 'react'
import type {
  ClusterEnvironment,
  CreateServiceAccountRequest,
  ServiceAccountResponse,
  ServiceAccountTokenResponse,
  TokenScope,
} from '../types/api'

interface ServiceAccountConsoleProps {
  accounts: ServiceAccountResponse[]
  onCreateAccount: (payload: CreateServiceAccountRequest) => Promise<void>
  onCreateToken: (serviceAccountId: string, name: string) => Promise<ServiceAccountTokenResponse>
}

const allScopes: TokenScope[] = ['HEALTH_READ', 'HEALTH_REFRESH', 'CLUSTER_READ']
const allEnvironments: ClusterEnvironment[] = ['PROD', 'NON_PROD']

function formatScope(scope: TokenScope) {
  return scope.toLowerCase().replaceAll('_', ':')
}

export function ServiceAccountConsole({ accounts, onCreateAccount, onCreateToken }: ServiceAccountConsoleProps) {
  const [accountName, setAccountName] = useState('observability-gateway')
  const [description, setDescription] = useState('Reads Mission Control health snapshots')
  const [latestToken, setLatestToken] = useState<ServiceAccountTokenResponse | null>(null)
  const [tokenName, setTokenName] = useState('default-health-token')
  const [creating, setCreating] = useState(false)

  async function handleCreateAccount() {
    setCreating(true)
    try {
      await onCreateAccount({
        name: accountName,
        description,
        scopes: allScopes,
        allowedEnvironments: allEnvironments,
        allowedClusterIds: [],
      })
    } finally {
      setCreating(false)
    }
  }

  async function handleTokenCreate(serviceAccountId: string) {
    const token = await onCreateToken(serviceAccountId, tokenName)
    setLatestToken(token)
  }

  return (
    <section className="service-account-console">
      <div className="console-card">
        <div className="console-card__header">
          <div>
            <span className="eyebrow">External API</span>
            <h3>Service Account Console</h3>
          </div>
          <button className="primary-button" type="button" onClick={handleCreateAccount} disabled={creating}>
            {creating ? 'Creating…' : 'Create service account'}
          </button>
        </div>
        <div className="form-grid">
          <label>
            Account name
            <input value={accountName} onChange={(event) => setAccountName(event.target.value)} />
          </label>
          <label>
            Default token label
            <input value={tokenName} onChange={(event) => setTokenName(event.target.value)} />
          </label>
          <label className="form-grid__wide">
            Description
            <textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={3} />
          </label>
        </div>
      </div>

      <div className="console-grid">
        <div className="console-card">
          <div className="console-card__header">
            <div>
              <span className="eyebrow">Accounts</span>
              <h3>Machine clients</h3>
            </div>
          </div>
          <div className="account-list">
            {accounts.map((account) => (
              <article key={account.id} className="account-row">
                <div className="account-row__body">
                  <strong>{account.name}</strong>
                  <p className="account-row__summary">{account.description}</p>
                  <div className="pill-row">
                    {account.allowedEnvironments.map((environment) => (
                      <span key={environment} className={`env-badge env-badge--${environment.toLowerCase()}`}>
                        {environment === 'PROD' ? 'Production' : 'Non-Prod'}
                      </span>
                    ))}
                  </div>
                  <div className="pill-row">
                    {account.scopes.map((scope) => (
                      <span key={scope} className="scope-pill">
                        {formatScope(scope)}
                      </span>
                    ))}
                  </div>
                  <small className="account-row__stats">{account.tokens.length} token(s) issued</small>
                </div>
                <button className="secondary-button" type="button" onClick={() => handleTokenCreate(account.id)}>
                  Mint token
                </button>
              </article>
            ))}
          </div>
        </div>

        <div className="console-card">
          <div className="console-card__header">
            <div>
              <span className="eyebrow">Usage</span>
              <h3>Integration snippet</h3>
            </div>
          </div>
          <pre className="code-block">{`curl -H "Authorization: Bearer ${latestToken?.rawToken ?? '<mint-a-token>'}" \\
  http://localhost:8080/api/external/v1/clusters/health`}</pre>
          {latestToken ? (
            <div className="token-banner">
              <strong>New token</strong>
              <code>{latestToken.rawToken}</code>
              <small>Copy it now. The raw value is only returned once.</small>
            </div>
          ) : (
            <p className="hint-text">Mint a token from an account on the left to test the external health API right away.</p>
          )}
        </div>
      </div>
    </section>
  )
}
