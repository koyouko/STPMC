"""
Generate an OpenMetrics file with HISTORY_DAYS of synthetic Kafka metrics,
ready for `promtool tsdb create-blocks-from openmetrics`.

OpenMetrics rules honoured here:
  - timestamps are in SECONDS (float ok)
  - samples for each series are emitted in ascending time order
  - the file ends with a literal `# EOF` line
  - one `# TYPE <metric> gauge` per metric, emitted once up front

Usage:
  python3 generate_history.py /out/history.txt
"""

import sys
import time

import topology

DAY = topology.DAY


def main(out_path: str):
    now = time.time()
    # Align "now" down to the step grid and end one step before now so the live
    # exporter owns the most recent point (avoids a duplicate at the seam).
    step = topology.HISTORY_STEP_SECONDS
    end = (int(now) // step) * step
    start = end - int(topology.HISTORY_DAYS * DAY)
    timestamps = list(range(start, end, step))

    # Build per-series sample lists so we can emit each series' points together
    # in ascending time order (OpenMetrics-friendly and fast for promtool).
    series = {}   # key -> (metric, label_str, [ (ts, value), ... ])
    order = []    # preserve first-seen order

    def label_str(labels):
        return "{" + ",".join(f'{k}="{v}"' for k, v in labels.items()) + "}"

    for ts in timestamps:
        for metric, labels, value in topology.iter_samples(t=ts, now=now):
            key = metric + label_str(labels)
            slot = series.get(key)
            if slot is None:
                slot = (metric, label_str(labels), [])
                series[key] = slot
                order.append(key)
            slot[2].append((ts, value))

    metrics_seen = []
    for key in order:
        m = series[key][0]
        if m not in metrics_seen:
            metrics_seen.append(m)

    n_samples = 0
    with open(out_path, "w") as f:
        for m in metrics_seen:
            f.write(f"# TYPE {m} gauge\n")
        for key in order:
            metric, lbls, points = series[key]
            for ts, value in points:
                f.write(f"{metric}{lbls} {value:.6g} {ts}\n")
                n_samples += 1
        f.write("# EOF\n")

    span_days = (end - start) / DAY
    print(f"[gen-history] wrote {n_samples:,} samples "
          f"({len(order)} series x {len(timestamps)} points) "
          f"covering {span_days:.0f}d -> {out_path}", flush=True)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "history.txt"
    main(out)
