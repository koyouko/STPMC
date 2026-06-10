"""
LenseIQ synthetic Kafka metrics exporter.

Serves Prometheus exposition format at /metrics on :9404 (the JMX-exporter
default). Values are computed for "now" on every scrape from topology.py, so
the series continue seamlessly from the backfilled history.

Stdlib only - no pip install required.
"""

import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import topology

PORT = 9404

# Prometheus exposition uses millisecond timestamps; we omit timestamps and let
# Prometheus stamp the scrape time. Values are computed at request time.

_TYPE_LINES = "\n".join(f"# TYPE {m} gauge" for m in topology.GAUGE_METRICS)


def _esc(v: str) -> str:
    return v.replace("\\", "\\\\").replace('"', '\\"')


def _fmt_labels(labels: dict) -> str:
    inner = ",".join(f'{k}="{_esc(v)}"' for k, v in labels.items())
    return "{" + inner + "}"


def render() -> str:
    now = time.time()
    out = [_TYPE_LINES]
    # group by metric name so each # TYPE is followed by its samples (not required
    # by Prometheus, but tidy); here we just stream all lines.
    for metric, labels, value in topology.iter_samples(t=now, now=now):
        out.append(f"{metric}{_fmt_labels(labels)} {value:.6g}")
    out.append("")
    return "\n".join(out)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/metrics", "/"):
            body = render().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path in ("/healthz", "/-/healthy"):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok\n")
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *args):
        pass  # quiet


if __name__ == "__main__":
    n = topology.series_count()
    print(f"[lenseiq-exporter] serving {n} series at :{PORT}/metrics "
          f"({len(topology.CLUSTERS)} clusters)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
