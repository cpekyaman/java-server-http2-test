#!/bin/bash

HOME_DIR="$PWD"
SERVER_CMD="runTomcat"
CLIENT_DIR="${HOME_DIR}/client/curl"
CLIENT_CMD="./main.sh"
TRACE_ENABLED="false"
MULTI_THREAD="false"

echo "creating work directory if not exists"
mkdir -p work/{logs,jetty/logs,tomcat/logs,tomcat/work}

if [ $# -eq 0 ]; then
  echo "no arguments are given, will use defaults"
else

  echo "parsing arguments"
  # we pre-process args for some flags as they affect other args
  for arg in "$@"
  do
     if [[ "$arg" == "-t" || "$arg" == "--trace" ]]; then
       TRACE_ENABLED="true"

       SERVER_CMD="${SERVER_CMD} -Ddetail.logLevel=trace"
       CLIENT_CMD="CURL_VERBOSE=true ${CLIENT_CMD}"
     fi

     if [[ "$arg" == "-m" || "$arg" == "--mthreads" ]]; then
      MULTI_THREAD="true"

      CLIENT_CMD="${CLIENT_CMD} -m"
    fi
  done

  while [[ $# -gt 0 ]]; do
    case $1 in
      -h|--help)
        echo "runs a server and a client for a certain duration until expected error is reproduced"
        echo "if no argument is specified, runs the test with tomcat as server and curl as client"
        echo "  specify -t to enable detail logging"
        echo "  specify -m to have clients use multiple threads to send requests concurrently"
        echo "  specify -s (tomcat | jetty) to pick specific server"
        echo "  specify -c (curl | go | python | jdk | apache) to pick specific client"

        exit 0
        ;;
      -t|--trace)
        TRACE_ENABLED="true"

        shift
        ;;
      -m|--mthreads)
        MULTI_THREAD="true"

        shift
        ;;
      -s|--server)
        CLIENT_DIR="${HOME_DIR}"

        if [ "$2" = "tomcat" ]; then
          SERVER_CMD="runTomcat"
        elif [ "$2" = "jetty" ]; then
          SERVER_CMD="runJetty"
        fi

        if [ "$TRACE_ENABLED" = "true" ]; then
          SERVER_CMD="${SERVER_CMD} -Ddetail.logLevel=trace"
        fi

        shift
        shift
        ;;
      -c|--client)
        if [ "$2" = "curl" ]; then
          CLIENT_DIR="${HOME_DIR}/client/curl"
          CLIENT_CMD="./main.sh"

          if [ "$TRACE_ENABLED" = "true" ]; then
            CLIENT_CMD="CURL_VERBOSE=true ${CLIENT_CMD}"
          fi

          if [ "$MULTI_THREAD" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} -m"
          fi
        elif [ "$2" = "go" ]; then
          CLIENT_DIR="${HOME_DIR}/client/go"
          CLIENT_CMD="go run main.go"

          if [ "$TRACE_ENABLED" = "true" ]; then
            CLIENT_CMD="GODEBUG=http2debug=1 ${CLIENT_CMD}"
          fi

          if [ "$MULTI_THREAD" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} --senderCount=4"
          fi
        elif [ "$2" = "python" ]; then
          CLIENT_DIR="${HOME_DIR}/client/python"
          CLIENT_CMD="./main.py"

          if [ "$TRACE_ENABLED" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} --trace"
          fi

          if [ "$MULTI_THREAD" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} --senderCount=4"
          fi
        elif [ "$2" = "apache" ]; then
          CLIENT_DIR="${HOME_DIR}"
          CLIENT_CMD="./gradlew runApacheClient"

          if [ "$TRACE_ENABLED" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} -Ddetail.logLevel=trace"
          fi

          if [ "$MULTI_THREAD" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} --args='--senderCount=4'"
          fi
        elif [ "$2" = "jdk" ]; then
          CLIENT_DIR="${HOME_DIR}"
          CLIENT_CMD="./gradlew runJdkClient"

          if [ "$TRACE_ENABLED" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} -Ddetail.logLevel=trace"
          fi

          if [ "$MULTI_THREAD" = "true" ]; then
            CLIENT_CMD="${CLIENT_CMD} --args='--senderCount=4'"
          fi
        fi

        shift
        shift
        ;;
      -*|--*)
        echo "Unknown option $1"
        exit 1
        ;;
    esac
  done
fi

./clean.sh

echo "running test with selected options"
echo "further logs can be seen in output files under work/logs"

(trap 'kill 0' SIGINT; (./gradlew ${SERVER_CMD} > ${HOME_DIR}/work/logs/server_out.log 2>&1) & (cd ${CLIENT_DIR}; eval ${CLIENT_CMD} > ${HOME_DIR}/work/logs/client_out.log 2>&1) & wait)
    