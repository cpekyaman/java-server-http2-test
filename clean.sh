  #!/bin/bash

echo "removing old log files"

rm -Rf ./work/jetty/logs/*
rm -Rf ./work/tomcat/logs/*
rm -Rf ./work/logs/*

echo "removed old log files"