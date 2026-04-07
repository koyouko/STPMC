#!/usr/bin/env node
/**
 * Lightweight production server for the Mission Control frontend.
 * Serves the Vite build output and proxies /api/ to the backend.
 * Zero dependencies — uses only Node.js built-ins.
 *
 * Usage:
 *   node serve.js
 *
 * Environment variables:
 *   PORT            - Frontend port (default: 8081)
 *   BACKEND_URL     - Backend API URL (default: http://localhost:8080)
 */

import { createServer, request as httpRequest } from 'node:http'
import { readFileSync, existsSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const DIST_DIR = join(__dirname, 'dist')
const PORT = parseInt(process.env.PORT || '8081', 10)
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080'

const MIME_TYPES = {
  '.html': 'text/html',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.png':  'image/png',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function serveStatic(res, filePath) {
  try {
    if (!existsSync(filePath) || !statSync(filePath).isFile()) return false
    const ext = extname(filePath)
    const mime = MIME_TYPES[ext] || 'application/octet-stream'
    const content = readFileSync(filePath)
    res.writeHead(200, {
      'Content-Type': mime,
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
    })
    res.end(content)
    return true
  } catch {
    return false
  }
}

function proxyRequest(req, res) {
  const backend = new URL(BACKEND_URL)
  const proxyReq = httpRequest(
    {
      hostname: backend.hostname,
      port: backend.port,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: backend.host },
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers)
      proxyRes.pipe(res)
    },
  )
  proxyReq.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ error: 'Backend unavailable' }))
  })
  req.pipe(proxyReq)
}

const server = createServer((req, res) => {
  const urlPath = req.url.split('?')[0]

  // Proxy API requests to backend
  if (urlPath.startsWith('/api/') || urlPath.startsWith('/actuator/')) {
    proxyRequest(req, res)
    return
  }

  // Try serving static file from dist/
  const filePath = join(DIST_DIR, urlPath === '/' ? 'index.html' : urlPath)
  if (serveStatic(res, filePath)) return

  // SPA fallback — all other routes get index.html
  serveStatic(res, join(DIST_DIR, 'index.html'))
})

server.listen(PORT, '0.0.0.0', () => {
  console.log('')
  console.log('  =============================================')
  console.log('   STP Kafka Mission Control — Frontend')
  console.log('  =============================================')
  console.log(`   URL:      http://0.0.0.0:${PORT}`)
  console.log(`   Backend:  ${BACKEND_URL}`)
  console.log(`   Static:   ${DIST_DIR}`)
  console.log('  =============================================')
  console.log('')
})
