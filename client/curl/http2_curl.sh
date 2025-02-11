#!/usr/bin/env bash

# sends multiple http2 requests by using `curl` with `parallel` option until expected failure response is received.
# it sleeps 100msec after each batch of parallel requests.

sender_id="$1"
log_verbose="$2"

trap "echo sender script $sender_id is terminated; exit" SIGINT SIGTERM

echo "sender script $sender_id running"

response_status="200";
end_test="false"
curl_opts="-k -s -o /dev/null"

while [[ "$response_status" == *"200"* ]] ; do
  # used for log correlation.
  trace_id=$(LC_ALL=C tr -dc A-F0-9 </dev/urandom | head -c 16; echo)

  # for tracing / debugging
	if [ "$log_verbose" = "true" ]; then
	  curl_opts="${curl_opts} -v";
	fi

  # sends as many requests as given in the urls file in parallel.
  response_status=$(curl ${curl_opts} -w "%{response_code}" -H "trace-id: $trace_id" -H "sender-id: $sender_id" --http2 --parallel --config parallel_request_urls.txt);

  # response_status contains responses of parallel requests together concatenated.
	if [[ "$response_status" == *"500"* ]]; then
	    echo "client $sender_id got expected response for request $trace_id, stopping test"
	    end_test="true"
	    break
	fi

	sleep 0.1s
done

# we got what we wanted, we'll cause the other test scripts to also finish.
if [ "$end_test" = "true" ]; then
  echo "sender $sender_id received expected response, ending test"
  sleep 5
  kill -USR1 $PPID
fi