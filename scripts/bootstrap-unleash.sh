#!/usr/bin/env bash
set -euo pipefail

# Configuration
UNLEASH_URL="${UNLEASH_URL:-http://localhost:4242}"
# LOCAL DEMO ADMIN CREDENTIAL: Scoped for administrative operations (bootstrap & flag management)
ADMIN_TOKEN="${UNLEASH_ADMIN_TOKEN:-*:*.local-demo-admin-token-not-for-production}"
PROJECT_ID="default"
ENVIRONMENT="development"
FEATURE_NAME="search-v2"

echo "=========================================================="
echo " Bootstrapping Unleash Feature Flag: ${FEATURE_NAME}"
echo " Target: ${UNLEASH_URL} (Env: ${ENVIRONMENT}, Project: ${PROJECT_ID})"
echo "=========================================================="

# 1. Wait for Unleash Server to be healthy
echo "Waiting for Unleash server at ${UNLEASH_URL}/health..."
MAX_RETRIES=30
RETRY_COUNT=0
until curl -s -f "${UNLEASH_URL}/health" > /dev/null; do
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [ "${RETRY_COUNT}" -ge "${MAX_RETRIES}" ]; then
    echo "ERROR: Unleash server did not become healthy in time."
    exit 1
  fi
  sleep 1
done
echo "Unleash server is healthy."

# 2. Check if feature flag exists, create if missing
FEATURE_HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: ${ADMIN_TOKEN}" \
  "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}")

if [ "${FEATURE_HTTP_STATUS}" -eq 404 ]; then
  echo "Creating feature flag '${FEATURE_NAME}' in project '${PROJECT_ID}'..."
  curl -s -f -X POST "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features" \
    -H "Authorization: ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
      "name": "'"${FEATURE_NAME}"'",
      "description": "Search engine V2 migration rollout with gradual percentage & allowlist",
      "type": "release"
    }' > /dev/null
  echo "Feature flag '${FEATURE_NAME}' created."
else
  echo "Feature flag '${FEATURE_NAME}' already exists (HTTP ${FEATURE_HTTP_STATUS})."
fi

# 3. Clean up any existing strategies in development environment to avoid residual unrestricted 100% rollout
echo "Clearing existing strategies for '${FEATURE_NAME}' in '${ENVIRONMENT}'..."
STRATEGIES_JSON=$(curl -s \
  -H "Authorization: ${ADMIN_TOKEN}" \
  "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies")

# Extract strategy IDs and delete each
STRATEGY_IDS=$(echo "${STRATEGIES_JSON}" | grep -o '"id":"[^"]*' | cut -d'"' -f4 || true)
for STRATEGY_ID in ${STRATEGY_IDS}; do
  if [ -n "${STRATEGY_ID}" ]; then
    curl -s -X DELETE \
      -H "Authorization: ${ADMIN_TOKEN}" \
      "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies/${STRATEGY_ID}" > /dev/null
    echo "Removed existing strategy ID: ${STRATEGY_ID}"
  fi
done

# 4. Add Strategy 1: Percentage rollout (initially 0%, stickiness: userId, groupId: search-v2)
echo "Adding Strategy 1: Flexible rollout (0%, stickiness: userId, groupId: search-v2)..."
curl -s -f -X POST \
  -H "Authorization: ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies" \
  -d '{
    "name": "flexibleRollout",
    "title": "Percentage Rollout (0%)",
    "disabled": false,
    "parameters": {
      "rollout": "0",
      "stickiness": "userId",
      "groupId": "'"${FEATURE_NAME}"'"
    },
    "constraints": []
  }' > /dev/null
echo "Strategy 1 added."

# 5. Add Strategy 2: Allowlist (100% rollout constrained to userId IN [alice, bob, qa-123])
echo "Adding Strategy 2: Allowlist for [alice, bob, qa-123]..."
curl -s -f -X POST \
  -H "Authorization: ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/strategies" \
  -d '{
    "name": "flexibleRollout",
    "title": "Allowlist (alice, bob, qa-123)",
    "disabled": false,
    "parameters": {
      "rollout": "100",
      "stickiness": "default",
      "groupId": "'"${FEATURE_NAME}"'"
    },
    "constraints": [
      {
        "contextName": "userId",
        "operator": "IN",
        "caseInsensitive": false,
        "inverted": false,
        "values": ["alice", "bob", "qa-123"]
      }
    ]
  }' > /dev/null
echo "Strategy 2 added."

# 6. Ensure the feature flag is switched OFF initially in development
echo "Setting '${FEATURE_NAME}' in '${ENVIRONMENT}' to OFF..."
curl -s -X POST \
  -H "Authorization: ${ADMIN_TOKEN}" \
  "${UNLEASH_URL}/api/admin/projects/${PROJECT_ID}/features/${FEATURE_NAME}/environments/${ENVIRONMENT}/off" > /dev/null

echo "=========================================================="
echo " Bootstrap Complete! Flag state:"
echo " - Feature: ${FEATURE_NAME}"
echo " - Environment: ${ENVIRONMENT}"
echo " - Status: OFF"
echo " - Strategy 1: flexibleRollout 0% (stickiness: userId, groupId: ${FEATURE_NAME})"
echo " - Strategy 2: allowlist [alice, bob, qa-123] (100%)"
echo "=========================================================="
