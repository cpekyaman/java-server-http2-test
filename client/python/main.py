#!/usr/bin/env python3

import asyncio
import getopt
import httpx
import logging
import sys
import threading
import time
import uuid


def main(argv):
    """
    Our main test suite runner method and our entry point.
    We create multiple threads each for asyncio.run, start them and wait for them finish.
    Basically, each thread is executing send_requests_loop via asyncio.

    :param argv: command line arguments

    :return:
    """
    configure_base_logging()

    logging.info("starting test")

    sender_count = 1
    test_duration = 5
    request_url = "https://localhost:8443/http2-server/api/ping"
    trace_enabled = False

    try:
        opts, args = getopt.getopt(argv, "ts:d:r:", ["trace", "senderCount=", "duration=", "requestUrl="])
    except getopt.GetoptError:
        logging.error('main.py -t -s <senderCount> -d <testDuration> -r <targetUrl>')
        sys.exit(1)

    for opt, arg in opts:
        if opt in ("-s", "--senderCount"):
            sender_count = int(arg)
        elif opt in ("-d", "--duration"):
            test_duration = int(arg)
        elif opt in ("-r", "--requestUrl"):
            request_url = arg
        elif opt in ("-t", "--trace"):
            trace_enabled = True

    if trace_enabled:
        logging.info("configuring trace logging")
        configure_trace_logging()

    check_server_running(request_url)

    logging.info(f"running {sender_count} senders for {test_duration} minutes for target url {request_url}")

    logging.info(f"creating sender threads")
    sender_threads = list()
    # once a thread receives expected error we will signal the others to also stop.
    done = threading.Event()
    for sender_id in range(1, sender_count + 1):
        sender_thread = threading.Thread(target=asyncio.run, args=(send_requests_loop(sender_id,
                                                                                      done,
                                                                                      test_duration * 60,
                                                                                      request_url),))
        sender_threads.append(sender_thread)
        sender_thread.start()

    logging.info("waiting for client threads")
    for thread in sender_threads:
        thread.join()

    logging.info("test completed")


def configure_base_logging():
    log_format = logging.Formatter("%(levelname)s [%(asctime)s] %(name)s - %(message)s")

    stdout = logging.StreamHandler(stream=sys.stdout)
    stdout.setLevel(logging.INFO)
    stdout.setFormatter(log_format)

    stderr = logging.StreamHandler(stream=sys.stderr)
    stderr.setLevel(logging.WARNING)
    stderr.setFormatter(log_format)

    logging.basicConfig(handlers=[stdout, stderr], level=logging.INFO)

    httpx_logger = logging.getLogger("httpx")
    httpx_logger.addHandler(stdout)
    httpx_logger.addHandler(stderr)
    httpx_logger.setLevel(logging.WARNING)

    httpcore_logger = logging.getLogger("httpcore")
    httpcore_logger.addHandler(stdout)
    httpcore_logger.addHandler(stderr)
    httpcore_logger.setLevel(logging.WARNING)


def configure_trace_logging():
    trace_handler = logging.FileHandler("../../work/logs/client_trace.log")
    trace_handler.setLevel(logging.DEBUG)

    httpx_logger = logging.getLogger("httpx")
    httpx_logger.setLevel(logging.DEBUG)
    httpx_logger.addHandler(trace_handler)

    httpcore_logger = logging.getLogger("httpcore")
    httpcore_logger.setLevel(logging.DEBUG)
    httpcore_logger.addHandler(trace_handler)


def check_server_running(request_url):
    logging.info("checking if server is running")

    server_running = False
    max_tries = 10
    tries = 0

    with httpx.Client(verify=False) as client:
        while tries < max_tries and not server_running:
            try:
                tries += 1
                client.get(request_url, timeout=4.0)
                server_running = True
            except httpx.ConnectError:
                logging.info("could not connect to server, will try again")
                time.sleep(1)

    if not server_running:
        logging.error("server is not running or not reachable, exiting")
        sys.exit(2)


async def send_requests_loop(sender_id, done, test_duration, request_url):
    """
    We create an async client and send multiple parallel requests.
    We repeat request sending until we receive expected error, or test times out.

    :param sender_id: just to serve as a form of request_id
    :param done: event to signal we're done and can stop
    :param test_duration: max duration we are going to run
    :param request_url: target url we're going to hit

    :return:
    """

    start_time = time.time()

    logging.info(f"sending requests for client {sender_id}")
    async with httpx.AsyncClient(http2=True, verify=False) as client:
        while not done.is_set():
            if time.time() >= start_time + test_duration:
                logging.warning(f"client {sender_id} did not receive expected error in time")
                break

            responses = await send_requests_parallel(client, sender_id, request_url)

            # if any of the responses got 500, we achieved test goal and we end the test.
            for response in responses:
                if response.status_code == 500:
                    logging.info(f"client {sender_id} got expected response for request, stopping test")
                    done.set()

        time.sleep(0.1)

    logging.info(f"closing client {sender_id}")
    await client.aclose()


async def send_requests_parallel(client, sender_id, request_url):
    """
    We send multiple requests to the same url in parallel.

    :param client: shared async client
    :param sender_id: just to serve as a form of request_id
    :param request_url: target url we're going to hit

    :return: responses from parallel async requests
    """

    urls = [request_url] * 5
    return await asyncio.gather(*[send_request(client, sender_id, url) for url in urls])


async def send_request(client, sender_id, url):
    """
    Send a single request to the given url with auto generated trace_id

    :param client: shared async client
    :param sender_id: just to serve as a form of request_id
    :param url: target url

    :return: response of the async request
    """

    headers = {'trace-id': uuid.uuid4().hex, 'sender-id': str(sender_id)}
    return await client.get(url, headers=headers, timeout=4.0)


if __name__ == "__main__":
    main(sys.argv[1:])
