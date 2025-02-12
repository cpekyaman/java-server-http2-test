## overview

A simple test suite, created as a gradle java project, to essentially ping an http(s) GET endpoint over http/2.  
Reproduces the issue where the server sends the client a 500 error due to some premature stream reset (details given [here](issue_description.md)).  
By default, runs a `tomcat` server and a single threaded `curl` client to try to reproduce the error.  
Multiple server and client options are available (a very limited set).  

`trace_logs_history` directory contains trace logs from some runs with the reproduced issue already.

### how to run

- run `./gradlew build` if you are running the code the first time (to download dependencies, build, etc.)
- run `./run/sh` driver script from project root, which will occupy the shell (possibly with `-m -t` options).
- in another window, run `tail -f work/logs/client_out.log`.
- once you see `test completed`, client reproduced the issue (and exits).
- terminate the driver script if it is still running.
- check `work/logs/server_out.log` for corresponding stack trace(s).
- check `work/logs/server_trace.log` for server details, if you've run with trace logs enabled (`-t` option).
- check `work/logs/client_trace.log` for client details, if you've run with trace logs enabled (`-t` option).
  (`curl` simply dumps verbose info into `client_out.log`, not `trace` file).

### caveats / findings

- only specific clients can reproduce the issue consistently (particularly `go` and `curl`).
- both of **java** clients and **python** client seem to work fine, or may require a longer test duration.
- without `-m` option, `curl` either can't reproduce the issue, or may require a longer test duration.
- `go` client can produce the issue very fast, even with a single thread.
- `go` and `curl` also have **jetty** generate a bunch of silent failures which can be seen in trace level logs, not causing 500 response status.
- pre **10.1.29** versions of tomcat have slightly different failures relatively often, not causing 500 response status.

## components

### run.sh

Main test driver is `run.sh` script, which should be run from project root directory and requires `bash`.
By default, it runs a `tomcat` server and a single threaded `curl` client to try to reproduce the error.
Most useful options to run the script are (options can be mixed, except for `-h`):
- `./run.sh -h` to see **help**
- `./run.sh -t` to to enable **trace** level logs
- `./run.sh -m` to run client in **multi-threaded** mode

Servers and clients all have their own startup commands / scripts. 
So, you can individually start / stop them without using the driver script.

### server

- **tomcat**:  
  version (v 10.1.35).  
  Default server, as it is the server we want to reproduce the issue for.  
  can be run via gradle command `./gradlew runTomcat`.  
  server version can be changed by changing `tomcatVersion` in `build.gradle`.
- **jetty**:  
  version (v 12.0.16).  
  provided as a means to compare, or as a means to check client behaviour is sane.  
  can be run via gradle command `./gradlew runJetty`.  
  server version can be changed by changing `jettyVersion` in `build.gradle`.

### client

All non-java clients reside in folders under `client` subfolder. 
All of those clients, except curl, require specific language runtimes to be installed.
Java clients reside in `client` subpackage under the root package of main java source set.

All clients follow these simple steps:
- send the request
- check the status 
  - if 500, stop and end the test
  - if 200, next step
- sleep 100 msecs

The steps are performed in a loop until test is terminated, or max test duration is reached (except curl).

#### list of used clients
- **go**:   
  requires [golang](https://go.dev/doc/install) to be installed.  
  before you run go client, you need to go to `client/go` subfolder and run:
  ```
  go mod download
  go mod tidy
  ```
  you can run it via `go run main.go` from `client/go` folder.  
  you can use it with driver script via `-c go` option.
- **python**:   
  requires [python 3.x](https://www.python.org/downloads/) to be installed.  
  before you run python client, you need to go to `client/python` subfolder and run:
  ```
  python -m ensurepip --upgrade
  pip install httpx
  ```
  you can run it via `./main.py` from `client/python` folder.  
  you can use it with driver script via `-c python` option.
- **curl**:   
  you can run it via `./main.sh` from `client/curl` folder.  
  you can use it with driver script via `-c curl` option (the default).
- **apache http client 5 (java)**:  
  you can run it via `./gradlew runApacheClient` from project root folder.  
  you can use it with driver script via `-c apache` option.
- **jdk client (java)**:  
  you can run it via `./gradlew runJdkClient` from project root folder.  
  you can use it with driver script via `-c jdk` option.

The default is `curl`, as it is more straightforward to run and does not require setup unlike some other clients.

## logging

Server request logs are under `work/<server>/logs`, all the other logs are under `work/logs`:
- **client_out.log**:  
  basic output logs of the client. by default, only errors or warnings, and some test parameters are logged.  
  clients also log when the expected error case happens, before they finish the test and exit.
- **client_trace.log**:  
  when trace is enabled, all the clients except `curl` output some http/2 low level details here.
  `curl` outputs is `verbose` logs to `client_out` file.
- **server_out.log**:  
  basic output logs of the client. by default, only errors or warnings, and some test parameters are logged.  
  a specific logger configured for each server also logs here when expected error case happens.
- **server_trace.log**:  
  when trace is enabled, servers output some http/2 low level details here.

logs include `traceId` and `senderId` fields to help with identifying / correlating log lines (as much as possible):
- `traceId` identifies an individual request, or the batch of parallel requests in case of `curl`.
- `senderId` identifies the client thread sending the request, if multi-threaded option is used.

