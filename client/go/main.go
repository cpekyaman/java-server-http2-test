package main

import (
	"context"
	"crypto/tls"
	"errors"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"dev.pkymn.issuedemo/tomcat-http2/cleanhttp"
	"github.com/google/uuid"
	"golang.org/x/net/http2"
)

// we use this to pick either specific settings used by consul, or our simple http2 settings.
var clientType = flag.String("clientType", "consul", "HTTP client variant to use")

// these are standard parameters our clients support
var senderCount = flag.Int("senderCount", 1, "number of parallel request sender routines to run")
var testDurationMinutes = flag.Duration("duration", 5, "max duration the test is run")
var requestUrl = flag.String("requestUrl", "https://localhost:8443/http2-server/api/ping", "target request url")

func main() {
	fmt.Println("starting test")
	flag.Parse()

	// we need trace logs, so configure the trace log file
	if strings.Contains(os.Getenv("GODEBUG"), "http2debug") {
		fmt.Println("configuring trace logging")

		logFileName := "../../work/logs/client_trace.log"

		logFile, err := os.OpenFile(logFileName, os.O_APPEND|os.O_RDWR|os.O_CREATE, 0644)
		if err != nil {
			log.Panic(err)
		}
		defer logFile.Close()

		log.SetOutput(logFile)
	}

	client := newClient(clientType)
	checkServerRunning(client)

	fmt.Printf("running %d senders for %d minutes for target url %s\n", *senderCount, *testDurationMinutes, *requestUrl)

	// this is for the main thread to wait until all senders are done.
	var wg = &sync.WaitGroup{}

	// this is to time limit the test run and also allow one sender to notify others to end test.
	ctx, cancel := context.WithDeadline(context.Background(), time.Now().Add(*testDurationMinutes*time.Minute))
	defer cancel()

	fmt.Printf("creating sender tasks\n")
	for senderId := 1; senderId <= *senderCount; senderId++ {
		wg.Add(1)
		go sendRequests(client, ctx, cancel, wg, senderId)
	}

	fmt.Println("waiting for client tasks")
	wg.Wait()
	fmt.Println("test completed")
}

func checkServerRunning(client *http.Client) {
	serverRunning := false
	maxTries := 10
	tries := 0

	fmt.Println("checking if server is running")

	for !serverRunning && tries < maxTries {
		req, err := http.NewRequest("GET", *requestUrl, nil)
		if err != nil {
			fmt.Println("could not create ping request")
			os.Exit(1)
		}

		tries += 1

		resp, err := client.Do(req)
		if err != nil {
			if errors.Is(err, syscall.ECONNREFUSED) {
				fmt.Println("could not connect to server, will try again")
				time.Sleep(2 * time.Second)
			} else {
				fmt.Printf("unexpected error for server ping %s\n", err.Error())
				os.Exit(2)
			}
		} else {
			serverRunning = true
			resp.Body.Close()
		}
	}

	if !serverRunning {
		fmt.Println("server is not running or not reachable, exiting")
		os.Exit(3)
	}
}

func newClient(clientType *string) *http.Client {
	tlsConfig := &tls.Config{InsecureSkipVerify: true}

	switch *clientType {
	case "consul":
		fmt.Println("using consul http client settings")
		transport := cleanhttp.DefaultTransport()
		transport.DisableKeepAlives = true
		transport.TLSClientConfig = tlsConfig

		return &http.Client{Transport: transport, Timeout: 4 * time.Second}
	case "http2":
		fmt.Println("using custom http2 transport")
		transport := &http2.Transport{
			TLSClientConfig: tlsConfig,
		}
		return &http.Client{Transport: transport, Timeout: 4 * time.Second}
	default:
		panic("unknown clientType")
	}
}

func sendRequests(client *http.Client, ctx context.Context, cancel context.CancelFunc, wg *sync.WaitGroup, senderId int) {
	defer wg.Done()
	fmt.Printf("sending requests for client %d\n", senderId)

	for {
		select {
		case <-ctx.Done():
			fmt.Printf("test timed out or completed, client %d done\n", senderId)
			return
		default:
			traceId := uuid.New().String()
			done, err := sendRequest(client, ctx, senderId, traceId)
			if err != nil {
				if errors.Is(err, syscall.ECONNREFUSED) {
					fmt.Printf("client %d got connection refused, stopping test\n", senderId)
					cancel()
				} else {
					fmt.Printf("client %d got an error, stopping client\n", senderId)
				}
				return
			}

			if done {
				fmt.Printf("client %d got expected response for request %s, stopping test\n", senderId, traceId)
				cancel()
			}

			time.Sleep(100 * time.Millisecond)
		}
	}
}

func sendRequest(client *http.Client, ctx context.Context, senderId int, traceId string) (bool, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", *requestUrl, nil)
	if err != nil {
		fmt.Printf("sender %d could not create request\n", senderId)
		return false, err
	}

	// to identify each request on the server side
	req.Header.Add("trace-id", traceId)
	req.Header.Add("sender-id", strconv.Itoa(senderId))

	resp, err := client.Do(req)
	if err != nil {
		fmt.Printf("sender %d got error %s for request %s\n", senderId, err.Error(), traceId)
		return false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == 500 {
		fmt.Printf("sender %d got expected error for request %s\n", senderId, traceId)
		return true, nil
	}

	return false, nil
}
