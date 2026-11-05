#!/usr/bin/env bash
set -euo pipefail

SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
SAMPLE_SIZE="${1:-1000}"

echo "=========================================================="
echo " Checking Distribution Across ${SAMPLE_SIZE} Non-Allowlisted IDs"
echo " Endpoint: ${SERVICE_URL}/search"
echo "=========================================================="

python3 - <<EOF
import urllib.request
import json
import sys
from concurrent.futures import ThreadPoolExecutor

base_url = "${SERVICE_URL}/search"
sample_size = int("${SAMPLE_SIZE}")
v1_count = 0
v2_count = 0
errors = 0

print(f"Sending {sample_size} requests to {base_url}...")

def fetch_search(user_id):
    url = f"{base_url}?q=poc-query&userId={user_id}"
    req = urllib.request.Request(url, headers={"User-Agent": "DistributionTest/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            return data.get("implementation")
    except Exception as e:
        return "ERROR"

with ThreadPoolExecutor(max_workers=20) as executor:
    # Use distinct IDs prefixed with 'random-user-' (guaranteed non-allowlisted)
    user_ids = [f"random-user-{i}" for i in range(1, sample_size + 1)]
    results = list(executor.map(fetch_search, user_ids))

for impl in results:
    if impl == "v1":
        v1_count += 1
    elif impl == "v2":
        v2_count += 1
    else:
        errors += 1

v2_pct = (v2_count / sample_size) * 100
v1_pct = (v1_count / sample_size) * 100

print("\n--- Distribution Results ---")
print(f"Total Requests: {sample_size}")
print(f"V1 (OLD Engine): {v1_count} ({v1_pct:.1f}%)")
print(f"V2 (NEW Engine): {v2_count} ({v2_pct:.1f}%)")
if errors > 0:
    print(f"Errors: {errors}")

print("\n--- Notes on Flexible Rollout & SDK Resilience ---")
print("1. Flexible rollout uses MurmurHash3 hashing over (stickiness + groupId).")
print("   Over large populations (e.g. 1,000 users), a 10% rollout results in ~10% V2 traffic")
print("   (statistical variance is expected; exact 10% is never asserted).")
print("2. Resiliency Note: Unleash SDKs maintain an in-memory toggle cache backed by local disk.")
print("   During an Unleash server or network outage, the SDK retains cached rules,")
print("   so an outage does NOT automatically switch flags OFF or disrupt customer traffic.")
EOF
