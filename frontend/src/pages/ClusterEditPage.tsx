import { useEffect, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  AuthProfileRequest,
  AuthProfileType,
  ClusterConfigResponse,
  ClusterEnvironment,
  ClusterListenerRequest,
  ComponentKind,
  ServiceEndpointProtocol,
  ServiceEndpointRequest,
  UpdateClusterRequest,
} from '../types/api'
import { formatLabel } from '../utils/formatters'
import type { DashboardContext } from '../layouts/AppLayout'

const componentKinds: ComponentKind[] = ['ZOOKEEPER', 'SCHEMA_REGISTRY', 'CONTROL_CENTER', 'PROMETHEUS', 'KRAFT', 'MDS']
const defaultPorts: Record<ComponentKind, number> = {
  KAFKA: 9092,
  ZOOKEEPER: 2181,
  SCHEMA_REGISTRY: 8081,
  CONTROL_CENTER: 9021,
  PROMETHEUS: 9090,
  KRAFT: 9093,
  MDS: 8090,
}

export function ClusterEditPage() {
  const { clusterId } = useParams<{ clusterId: string }>()
  const { reloadClusters, setError } = useOutletContext<DashboardContext>()
  const navigate = useNavigate()
  const [config, setConfig] = useState<ClusterConfigResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [deactivating, setDeactivating] = useState(false)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [environment, setEnvironment] = useState<ClusterEnvironment>('NON_PROD')
  const [listener, setListener] = useState<ClusterListenerRequest>({
    name: 'primary',
    host: '',
    port: 9092,
    preferred: true,
    authProfile: { name: 'default', type: 'PLAINTEXT', securityProtocol: 'PLAINTEXT' },
  })
  const [endpoints, setEndpoints] = useState<ServiceEndpointRequest[]>([])

  useEffect(() => {
    if (!clusterId) return
    void apiClient.getClusterConfig(clusterId).then((cfg) => {
      setConfig(cfg)
      setName(cfg.name)
      setDescription(cfg.description ?? '')
      setEnvironment(cfg.environment)
      if (cfg.listeners.length > 0) {
        const first = cfg.listeners[0]
        setListener({
          name: first.name,
          host: first.host,
          port: first.port,
          preferred: first.preferred,
          authProfile: {
            name: first.authProfile.name,
            type: first.authProfile.type,
            securityProtocol: first.authProfile.securityProtocol,
            truststorePath: first.authProfile.truststorePath ?? undefined,
            keystorePath: first.authProfile.keystorePath ?? undefined,
            principal: first.authProfile.principal ?? undefined,
            keytabPath: first.authProfile.keytabPath ?? undefined,
            krb5ConfigPath: first.authProfile.krb5ConfigPath ?? undefined,
            saslServiceName: first.authProfile.saslServiceName ?? undefined,
          },
        })
      }
      setEndpoints(
        cfg.serviceEndpoints.map((e) => ({
          kind: e.kind,
          protocol: e.protocol,
          baseUrl: e.baseUrl ?? undefined,
          host: e.host ?? undefined,
          port: e.port ?? undefined,
          healthPath: e.healthPath ?? undefined,
          version: e.version ?? undefined,
        })),
      )
    }).catch((err) => {
      setError(err instanceof Error ? err.message : 'Failed to load cluster config')
    })
  }, [clusterId, setError])

  function updateAuthProfile(field: keyof AuthProfileRequest, value: string) {
    setListener((prev) => ({
      ...prev,
      authProfile: { ...prev.authProfile, [field]: value },
    }))
  }

  function handleAuthTypeChange(type: AuthProfileType) {
    const protocolMap: Record<AuthProfileType, string> = {
      PLAINTEXT: 'PLAINTEXT',
      MTLS_SSL: 'SSL',
      SASL_GSSAPI: 'SASL_PLAINTEXT',
    }
    setListener((prev) => ({
      ...prev,
      authProfile: { ...prev.authProfile, type, securityProtocol: protocolMap[type] },
    }))
  }

  function addEndpoint(kind: ComponentKind) {
    if (endpoints.some((e) => e.kind === kind)) return
    setEndpoints((prev) => [...prev, { kind, protocol: 'TCP', host: '', port: defaultPorts[kind] }])
  }

  function removeEndpoint(kind: ComponentKind) {
    setEndpoints((prev) => prev.filter((e) => e.kind !== kind))
  }

  function updateEndpoint(kind: ComponentKind, field: keyof ServiceEndpointRequest, value: string | number) {
    setEndpoints((prev) =>
      prev.map((e) => (e.kind === kind ? { ...e, [field]: value } : e)),
    )
  }

  function updateEndpointProtocol(kind: ComponentKind, protocol: ServiceEndpointProtocol) {
    setEndpoints((prev) =>
      prev.map((e) => (e.kind === kind ? { ...e, protocol } : e)),
    )
  }

  async function handleSubmit() {
    if (!clusterId) return
    setSubmitting(true)
    try {
      const payload: UpdateClusterRequest = {
        name,
        description,
        environment,
        listeners: [listener],
        serviceEndpoints: endpoints,
      }
      await apiClient.updateCluster(clusterId, payload)
      await reloadClusters()
      void navigate(`/clusters/${clusterId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update cluster')
    } finally {
      setSubmitting(false)
    }
  }

  if (!config) {
    return <div className="loading-card">Loading cluster configuration…</div>
  }

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">Edit cluster</span>
          <h1>{config.name}</h1>
        </div>
        <div className="hero__actions">
          <button className="secondary-button" type="button" onClick={() => void navigate(`/clusters/${clusterId}`)}>
            Cancel
          </button>
        </div>
      </header>

      <section className="wizard__panel">
        <h2>Cluster basics</h2>
        <div className="form-grid">
          <label>
            Cluster name
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <label>
            Environment
            <select value={environment} onChange={(e) => setEnvironment(e.target.value as ClusterEnvironment)}>
              <option value="PROD">Production</option>
              <option value="NON_PROD">Non-Production</option>
            </select>
          </label>
          <label className="form-grid__wide">
            Description
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
          </label>
        </div>
      </section>

      <section className="wizard__panel">
        <h2>Kafka listener</h2>
        <div className="form-grid">
          <label>
            Bootstrap host
            <input value={listener.host} onChange={(e) => setListener({ ...listener, host: e.target.value })} />
          </label>
          <label>
            Port
            <input type="number" value={listener.port} onChange={(e) => setListener({ ...listener, port: parseInt(e.target.value) || 9092 })} />
          </label>
          <label>
            Auth type
            <select value={listener.authProfile.type} onChange={(e) => handleAuthTypeChange(e.target.value as AuthProfileType)}>
              <option value="PLAINTEXT">Plaintext</option>
              <option value="MTLS_SSL">mTLS / SSL</option>
              <option value="SASL_GSSAPI">Kerberos (SASL/GSSAPI)</option>
            </select>
          </label>
          <label>
            Security protocol
            <input value={listener.authProfile.securityProtocol} onChange={(e) => updateAuthProfile('securityProtocol', e.target.value)} />
          </label>

          {(listener.authProfile.type === 'MTLS_SSL' || listener.authProfile.type === 'SASL_GSSAPI') && (
            <>
              <label>
                Truststore path
                <input value={listener.authProfile.truststorePath ?? ''} onChange={(e) => updateAuthProfile('truststorePath', e.target.value)} />
              </label>
              <label>
                Truststore password file
                <input value={listener.authProfile.truststorePasswordFile ?? ''} onChange={(e) => updateAuthProfile('truststorePasswordFile', e.target.value)} />
              </label>
            </>
          )}

          {listener.authProfile.type === 'MTLS_SSL' && (
            <>
              <label>
                Keystore path
                <input value={listener.authProfile.keystorePath ?? ''} onChange={(e) => updateAuthProfile('keystorePath', e.target.value)} />
              </label>
              <label>
                Keystore password file
                <input value={listener.authProfile.keystorePasswordFile ?? ''} onChange={(e) => updateAuthProfile('keystorePasswordFile', e.target.value)} />
              </label>
              <label>
                Key password file
                <input value={listener.authProfile.keyPasswordFile ?? ''} onChange={(e) => updateAuthProfile('keyPasswordFile', e.target.value)} />
              </label>
            </>
          )}

          {listener.authProfile.type === 'SASL_GSSAPI' && (
            <>
              <label>
                Kerberos principal
                <input value={listener.authProfile.principal ?? ''} onChange={(e) => updateAuthProfile('principal', e.target.value)} />
              </label>
              <label>
                Keytab path
                <input value={listener.authProfile.keytabPath ?? ''} onChange={(e) => updateAuthProfile('keytabPath', e.target.value)} />
              </label>
              <label>
                krb5.conf path
                <input value={listener.authProfile.krb5ConfigPath ?? ''} onChange={(e) => updateAuthProfile('krb5ConfigPath', e.target.value)} />
              </label>
              <label>
                SASL service name
                <input value={listener.authProfile.saslServiceName ?? ''} onChange={(e) => updateAuthProfile('saslServiceName', e.target.value)} />
              </label>
            </>
          )}
        </div>
      </section>

      <section className="wizard__panel">
        <h2>Service endpoints</h2>
        <div className="wizard__endpoint-buttons">
          {componentKinds.map((kind) => (
            <button
              key={kind}
              type="button"
              className={`secondary-button ${endpoints.some((e) => e.kind === kind) ? 'secondary-button--active' : ''}`}
              onClick={() => endpoints.some((e) => e.kind === kind) ? removeEndpoint(kind) : addEndpoint(kind)}
            >
              {endpoints.some((e) => e.kind === kind) ? '✓ ' : '+ '}
              {formatLabel(kind)}
            </button>
          ))}
        </div>

        {endpoints.map((endpoint) => (
          <div key={endpoint.kind} className="wizard__endpoint-form">
            <div className="wizard__endpoint-form-header">
              <h3>{formatLabel(endpoint.kind)}</h3>
              <button className="ghost-button" type="button" onClick={() => removeEndpoint(endpoint.kind)}>Remove</button>
            </div>
            <div className="form-grid">
              <label>
                Protocol
                <select value={endpoint.protocol} onChange={(e) => updateEndpointProtocol(endpoint.kind, e.target.value as ServiceEndpointProtocol)}>
                  <option value="TCP">TCP</option>
                  <option value="HTTP">HTTP</option>
                  <option value="HTTPS">HTTPS</option>
                </select>
              </label>
              {endpoint.protocol === 'TCP' ? (
                <>
                  <label>
                    Host
                    <input value={endpoint.host ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'host', e.target.value)} />
                  </label>
                  <label>
                    Port
                    <input type="number" value={endpoint.port ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'port', parseInt(e.target.value) || 0)} />
                  </label>
                </>
              ) : (
                <>
                  <label>
                    Base URL
                    <input value={endpoint.baseUrl ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'baseUrl', e.target.value)} />
                  </label>
                  <label>
                    Health path
                    <input value={endpoint.healthPath ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'healthPath', e.target.value)} />
                  </label>
                </>
              )}
              <label>
                Version (optional)
                <input value={endpoint.version ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'version', e.target.value)} />
              </label>
            </div>
          </div>
        ))}
      </section>

      <div className="wizard__nav">
        <button className="secondary-button" type="button" onClick={() => void navigate(`/clusters/${clusterId}`)}>
          Cancel
        </button>
        <button className="primary-button" type="button" onClick={() => void handleSubmit()} disabled={submitting || !name || !listener.host}>
          {submitting ? 'Saving…' : 'Save changes'}
        </button>
      </div>

      <section className="danger-zone">
        <h2>Danger Zone</h2>
        <div className="danger-zone__content">
          <div>
            <strong>Deactivate this cluster</strong>
            <p>Once deactivated, this cluster will no longer be monitored and self-service tasks will be unavailable.</p>
          </div>
          <button
            className="btn btn--danger"
            type="button"
            disabled={deactivating}
            onClick={async () => {
              if (!window.confirm('Are you sure you want to deactivate this cluster? This can be reversed by re-onboarding.')) return
              setDeactivating(true)
              try {
                await apiClient.deleteCluster(clusterId!)
                await reloadClusters()
                void navigate('/')
              } catch (err) {
                setError(err instanceof Error ? err.message : 'Failed to deactivate cluster')
              } finally {
                setDeactivating(false)
              }
            }}
          >
            {deactivating ? 'Deactivating…' : 'Deactivate cluster'}
          </button>
        </div>
      </section>
    </>
  )
}
