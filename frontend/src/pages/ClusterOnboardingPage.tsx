import { useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  AuthProfileRequest,
  AuthProfileType,
  ClusterEnvironment,
  ClusterListenerRequest,
  ComponentKind,
  CreateClusterRequest,
  ServiceEndpointProtocol,
  ServiceEndpointRequest,
  TestConnectionResponse,
} from '../types/api'
import { formatLabel } from '../utils/formatters'
import type { DashboardContext } from '../layouts/AppLayout'

type WizardStep = 'basics' | 'listener' | 'endpoints' | 'review'
const steps: WizardStep[] = ['basics', 'listener', 'endpoints', 'review']

const defaultAuthProfile: AuthProfileRequest = {
  name: 'default',
  type: 'PLAINTEXT',
  securityProtocol: 'PLAINTEXT',
}

const defaultListener: ClusterListenerRequest = {
  name: 'primary',
  host: '',
  port: 9092,
  preferred: true,
  authProfile: { ...defaultAuthProfile },
}

const emptyEndpoint: ServiceEndpointRequest = {
  kind: 'ZOOKEEPER',
  protocol: 'TCP',
  host: '',
  port: 2181,
}

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

export function ClusterOnboardingPage() {
  const { reloadClusters, setError } = useOutletContext<DashboardContext>()
  const navigate = useNavigate()
  const [step, setStep] = useState<WizardStep>('basics')
  const [submitting, setSubmitting] = useState(false)
  const [testResult, setTestResult] = useState<TestConnectionResponse | null>(null)
  const [testing, setTesting] = useState(false)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [environment, setEnvironment] = useState<ClusterEnvironment>('NON_PROD')
  const [listener, setListener] = useState<ClusterListenerRequest>({ ...defaultListener })
  const [endpoints, setEndpoints] = useState<ServiceEndpointRequest[]>([])

  const stepIndex = steps.indexOf(step)
  const canPrev = stepIndex > 0
  const canNext = stepIndex < steps.length - 1

  function goPrev() {
    if (canPrev) setStep(steps[stepIndex - 1])
  }
  function goNext() {
    if (canNext) setStep(steps[stepIndex + 1])
  }

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
    setEndpoints((prev) => [...prev, { ...emptyEndpoint, kind, port: defaultPorts[kind] }])
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

  async function handleTestConnection() {
    setTesting(true)
    setTestResult(null)
    try {
      const result = await apiClient.testConnection({
        bootstrapServers: `${listener.host}:${listener.port}`,
        authProfile: listener.authProfile,
      })
      setTestResult(result)
    } catch (err) {
      setTestResult({ success: false, clusterId: null, nodeCount: 0, latencyMs: 0, errorMessage: err instanceof Error ? err.message : 'Connection test failed' })
    } finally {
      setTesting(false)
    }
  }

  async function handleSubmit() {
    setSubmitting(true)
    try {
      const payload: CreateClusterRequest = {
        name,
        environment,
        description,
        listeners: [listener],
        serviceEndpoints: endpoints.length > 0 ? endpoints : undefined,
      }
      const created = await apiClient.createCluster(payload)
      await reloadClusters()
      void navigate(`/clusters/${created.clusterId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create cluster')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">Cluster onboarding</span>
          <h1>Register a Kafka cluster</h1>
          <p>Walk through the wizard to onboard an existing Kafka cluster into Mission Control.</p>
        </div>
        <div className="hero__actions">
          <button className="secondary-button" type="button" onClick={() => void navigate('/')}>
            Cancel
          </button>
        </div>
      </header>

      <section className="wizard">
        <div className="wizard__steps">
          {steps.map((s, i) => (
            <button
              key={s}
              type="button"
              className={`wizard__step ${s === step ? 'wizard__step--active' : ''} ${i < stepIndex ? 'wizard__step--done' : ''}`}
              onClick={() => setStep(s)}
            >
              <span className="wizard__step-number">{i + 1}</span>
              <span>{formatLabel(s)}</span>
            </button>
          ))}
        </div>

        <div className="wizard__body">
          {step === 'basics' && (
            <div className="wizard__panel">
              <h2>Cluster basics</h2>
              <div className="form-grid">
                <label>
                  Cluster name
                  <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Confluent Prod East" />
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
                  <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} placeholder="Optional cluster description" />
                </label>
              </div>
            </div>
          )}

          {step === 'listener' && (
            <div className="wizard__panel">
              <h2>Kafka listener</h2>
              <div className="form-grid">
                <label>
                  Bootstrap host
                  <input value={listener.host} onChange={(e) => setListener({ ...listener, host: e.target.value })} placeholder="e.g. kafka-broker-01.corp.net" />
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
                      <input value={listener.authProfile.truststorePath ?? ''} onChange={(e) => updateAuthProfile('truststorePath', e.target.value)} placeholder="/path/to/truststore.jks" />
                    </label>
                    <label>
                      Truststore password file
                      <input value={listener.authProfile.truststorePasswordFile ?? ''} onChange={(e) => updateAuthProfile('truststorePasswordFile', e.target.value)} placeholder="/path/to/truststore-password" />
                    </label>
                  </>
                )}

                {listener.authProfile.type === 'MTLS_SSL' && (
                  <>
                    <label>
                      Keystore path
                      <input value={listener.authProfile.keystorePath ?? ''} onChange={(e) => updateAuthProfile('keystorePath', e.target.value)} placeholder="/path/to/keystore.jks" />
                    </label>
                    <label>
                      Keystore password file
                      <input value={listener.authProfile.keystorePasswordFile ?? ''} onChange={(e) => updateAuthProfile('keystorePasswordFile', e.target.value)} placeholder="/path/to/keystore-password" />
                    </label>
                    <label>
                      Key password file
                      <input value={listener.authProfile.keyPasswordFile ?? ''} onChange={(e) => updateAuthProfile('keyPasswordFile', e.target.value)} placeholder="/path/to/key-password" />
                    </label>
                  </>
                )}

                {listener.authProfile.type === 'SASL_GSSAPI' && (
                  <>
                    <label>
                      Kerberos principal
                      <input value={listener.authProfile.principal ?? ''} onChange={(e) => updateAuthProfile('principal', e.target.value)} placeholder="kafka/broker@REALM.COM" />
                    </label>
                    <label>
                      Keytab path
                      <input value={listener.authProfile.keytabPath ?? ''} onChange={(e) => updateAuthProfile('keytabPath', e.target.value)} placeholder="/path/to/keytab" />
                    </label>
                    <label>
                      krb5.conf path
                      <input value={listener.authProfile.krb5ConfigPath ?? ''} onChange={(e) => updateAuthProfile('krb5ConfigPath', e.target.value)} placeholder="/etc/krb5.conf" />
                    </label>
                    <label>
                      SASL service name
                      <input value={listener.authProfile.saslServiceName ?? ''} onChange={(e) => updateAuthProfile('saslServiceName', e.target.value)} placeholder="kafka" />
                    </label>
                  </>
                )}
              </div>

              <div className="wizard__test-connection">
                <button className="secondary-button" type="button" onClick={() => void handleTestConnection()} disabled={testing || !listener.host}>
                  {testing ? 'Testing…' : 'Test connection'}
                </button>
                {testResult && (
                  <div className={`test-result test-result--${testResult.success ? 'success' : 'failure'}`}>
                    {testResult.success ? (
                      <span>Connected — {testResult.nodeCount} broker(s), {testResult.latencyMs}ms latency, cluster ID: {testResult.clusterId}</span>
                    ) : (
                      <span>Failed — {testResult.errorMessage}</span>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {step === 'endpoints' && (
            <div className="wizard__panel">
              <h2>Service endpoints</h2>
              <p className="hint-text">Add optional service endpoints for health monitoring. These are not required but enable TCP/HTTP probes.</p>

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
                          <input value={endpoint.host ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'host', e.target.value)} placeholder="e.g. zookeeper-01.corp.net" />
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
                          <input value={endpoint.baseUrl ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'baseUrl', e.target.value)} placeholder="e.g. http://schema-registry:8081" />
                        </label>
                        <label>
                          Health path
                          <input value={endpoint.healthPath ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'healthPath', e.target.value)} placeholder="e.g. /health" />
                        </label>
                      </>
                    )}
                    <label>
                      Version (optional)
                      <input value={endpoint.version ?? ''} onChange={(e) => updateEndpoint(endpoint.kind, 'version', e.target.value)} placeholder="e.g. 7.5.1" />
                    </label>
                  </div>
                </div>
              ))}

              {endpoints.length === 0 && (
                <div className="empty-state">
                  <p>No service endpoints added. You can add them later by editing the cluster.</p>
                </div>
              )}
            </div>
          )}

          {step === 'review' && (
            <div className="wizard__panel">
              <h2>Review &amp; confirm</h2>
              <div className="wizard__review">
                <dl className="wizard__review-list">
                  <div><dt>Cluster name</dt><dd>{name || '—'}</dd></div>
                  <div><dt>Environment</dt><dd>{environment === 'PROD' ? 'Production' : 'Non-Production'}</dd></div>
                  <div><dt>Description</dt><dd>{description || '—'}</dd></div>
                  <div><dt>Bootstrap server</dt><dd>{listener.host}:{listener.port}</dd></div>
                  <div><dt>Auth type</dt><dd>{formatLabel(listener.authProfile.type)}</dd></div>
                  <div><dt>Service endpoints</dt><dd>{endpoints.length === 0 ? 'None' : endpoints.map((e) => formatLabel(e.kind)).join(', ')}</dd></div>
                </dl>

                {testResult?.success && (
                  <div className="test-result test-result--success">
                    Connection verified — {testResult.nodeCount} broker(s)
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="wizard__nav">
          <button className="secondary-button" type="button" onClick={goPrev} disabled={!canPrev}>
            Previous
          </button>
          <div className="wizard__nav-right">
            {step === 'review' ? (
              <button className="primary-button" type="button" onClick={() => void handleSubmit()} disabled={submitting || !name || !listener.host}>
                {submitting ? 'Creating…' : 'Create cluster'}
              </button>
            ) : (
              <button className="primary-button" type="button" onClick={goNext} disabled={!canNext}>
                Next
              </button>
            )}
          </div>
        </div>
      </section>
    </>
  )
}
