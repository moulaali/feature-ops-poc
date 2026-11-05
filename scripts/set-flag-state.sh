#!/usr/bin/env bash
set -euo pipefail

UNLEASH_URL="${UNLEASH_URL:-http://localhost:4242}"
ADMIN_TOKEN="${UNLEASH_ADMIN_TOKEN:-*:*.local-demo-admin-token-not-for-production}"
PROJECT_ID="default"
ENVIRONMENT="development"
FEATURE_NAME="search-v2"

ACTION="${1:-help}"

case "${ACTION}" in
  on)
    echo "Turning flag '${FEATURE_NAME}' ON in environment '${ENVIRONMENT}'..."
    curl -s -f -X POST \
      -H "Authorization: ${ADMIN_TOKEN}" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/on"
    echo "Flag '${FEATURE_NAME}' is now ON."
    ;;
  off)
    echo "Turning flag '${FEATURE_NAME}' OFF in environment '${ENVIRONMENT}'..."
    curl -s -f -X POST \
      -H "Authorization: ${ADMIN_TOKEN}" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/off"
    echo "Flag '${FEATURE_NAME}' is now OFF."
    ;;
  percentage|rollout)
    PERCENTAGE="${2:-}"
    if [ -z "${PERCENTAGE}" ]; then
      echo "Error: Please specify percentage rollout (0-100). Example: ./scripts/set-flag-state.sh percentage 10"
      exit 1
    fi
    echo "Finding percentage rollout strategy ID for '${FEATURE_NAME}'..."
    STRATEGIES_JSON=$(curl -s -H "Authorization: ${ADMIN_TOKEN}" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies")

    STRATEGY_ID=$(echo "${STRATEGIES_JSON}" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    found = [s['id'] for s in data if s.get('parameters', {}).get('stickiness') == 'userId']
    print(found[0] if found else '')
except Exception as e:
    sys.exit(1)
")

    if [ -z "${STRATEGY_ID}" ]; then
      echo "Error: Could not locate percentage rollout strategy. Run ./scripts/bootstrap-unleash.sh first."
      exit 1
    fi

    echo "Updating percentage rollout strategy (${STRATEGY_ID}) to ${PERCENTAGE}%..."
    curl -s -f -X PUT \
      -H "Authorization: ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies/${STRATEGY_ID}" \
      -d '{
        "name": "flexibleRollout",
        "title": "Percentage Rollout ('"${PERCENTAGE}"'%)",
        "disabled": false,
        "parameters": {
          "rollout": "'"${PERCENTAGE}"'",
          "stickiness": "userId",
          "groupId": "'"${FEATURE_NAME}"'"
        },
        "constraints": []
      }' > /dev/null
    echo "Percentage rollout updated to ${PERCENTAGE}%."
    ;;
  status)
    echo "Querying '${FEATURE_NAME}' in '${ENVIRONMENT}'..."
    curl -s -H "Authorization: ${ADMIN_TOKEN}" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}" | python3 -c "
import sys, json
data = json.load(sys.stdin)
env_data = next((e for e in data.get('environments', []) if e.get('name') == '${ENVIRONMENT}'), None)
if env_data:
    print(f\"Feature: {data.get('name')}\")
    print(f\"Environment: ${ENVIRONMENT}\")
    print(f\"Enabled: {env_data.get('enabled')}\")
    print(\"Strategies:\")
    for s in env_data.get('strategies', []):
        name = s.get('title') or s.get('name')
        params = s.get('parameters', {})
        constraints = s.get('constraints', [])
        print(f\"  - {name}: params={params}, constraints={constraints}\")
else:
    print('Environment data not found.')
"
    ;;
  *)
    echo "Usage: $0 {on|off|percentage <0-100>|status}"
    exit 1
    ;;
esac
