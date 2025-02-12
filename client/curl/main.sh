#!/usr/bin/env bash

# sort of a test-driver script for actual test scripts.
# starts one or more `http2_curl` scripts depending on `-m` option being specified.
# waits until any of the scripts gets expected error from the server.

echo 'starting test'

# whether to run single instance of request sender script, or multiple in parallel
multi_thread="false"
if [[ $# -eq 1 ]]; then
  if [[ "$1" -eq "-m" ]]; then
    multi_thread="true"
  fi
fi

echo 'checking server is running'

max_tries=10
tries=0
running="false"

# we will check if the server is running first.
# if server is not reachable after a couple of tries, the script will exit.
while [[ $tries -lt $max_tries ]]; do
  curl -k -s -o /dev/null "https://localhost:8443/http2-server/api/ping"
  exit_code=$?

  if [[ $exit_code -eq 0 ]]; then
    running="true"
    break
  elif [[ $exit_code -eq 7 ]]; then
    echo "could not connect to server, will try again"
    sleep 2
  else
    echo "unexpected error $exit_code, exiting"
    exit $exit_code
  fi

  tries=$((tries+1))
done

if [ "$running" = "false" ]; then
  echo "server is not running or not reachable, exiting"
  exit 1
fi

VERBOSE=${CURL_VERBOSE:-"false"}
# either 4 scripts in parallel or just a single one.
if [ "$multi_thread" = "true" ]; then
    echo 'running 4 request scripts'
    (trap 'echo test completed; kill 0' SIGINT SIGUSR1; ./http2_curl.sh 1 $VERBOSE & ./http2_curl.sh 2 $VERBOSE & ./http2_curl.sh 3 $VERBOSE & ./http2_curl.sh 4 $VERBOSE & wait)
else
  echo 'running single request script'
  (trap 'echo test completed; kill 0' SIGINT SIGUSR1; ./http2_curl.sh 1 $VERBOSE & wait)
fi