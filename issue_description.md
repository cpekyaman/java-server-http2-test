## basic system description

- java : open JDK 17.0.9
- tomcat : 9.0.88 (upgraded to 10.1.33 and downgraded to 10.1.24)
- http(s) with http2 upgrade protocol enabled on tomcat
- application with health check endpoints to be called by the monitoring system over http(s) GET.
- monitoring system uses http2 by default since server supports it.

## issue description / timeline

- tomcat upgraded to **10.1.24** to spot mostly javax to jakarta migration related issues.
- jakarta issues solved, and code updated to latest **10.x** version at the time of upgrade go-live decision (**10.1.33**).
- health checks started to generate 500 error, albeit relatively rarely, with corresponding error on server side:
  ```
    java.io.IOException: null
    at org.apache.coyote.http2.Stream$StandardStreamInputBuffer.receiveReset(Stream.java:1516) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Stream.receiveReset(Stream.java:224) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Http2UpgradeHandler.close(Http2UpgradeHandler.java:1305) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Http2UpgradeHandler.upgradeDispatch(Http2UpgradeHandler.java:437) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Http2AsyncUpgradeHandler.upgradeDispatch(Http2AsyncUpgradeHandler.java:43) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Http2AsyncParser$FrameCompletionHandler.failed(Http2AsyncParser.java:337) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.coyote.http2.Http2AsyncParser$FrameCompletionHandler.failed(Http2AsyncParser.java:167) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.tomcat.util.net.SocketWrapperBase$VectoredIOCompletionHandler.failed(SocketWrapperBase.java:1148) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.tomcat.util.net.NioEndpoint$NioSocketWrapper$NioOperationState.run(NioEndpoint.java:1660) ~[tomcat-coyote-10.1.33.jar:10.1.33]
    at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1190) [tomcat-util-10.1.33.jar:10.1.33]
    at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-util-10.1.33.jar:10.1.33]
    at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) [tomcat-util-10.1.33.jar:10.1.33]
    at java.lang.Thread.run(Thread.java:840) [?:?]
  ```
- even though error message is `null` in the logs, message is supposed be `stream.clientResetRequest=Client reset the stream before the request was fully read`.
  which implies somewhat misbehaving client, or something perceived as such by tomcat.
- [the change](https://github.com/apache/tomcat/commit/f902edf085c0c73139a66d1bfc4d5790a416b130) done in **10.1.29** for [bug 69302](https://bz.apache.org/bugzilla/show_bug.cgi?id=69302) seems to generate 500.
  which also seem to align with the class and line number in the stack trace.
- the change seems to be doing what it is designed to do.
  some older minor versions were also tested to see if there another root cause.
  with some trace logging enabled, the following error is seen relatively often in multiple tomcat versions (without resulting in 500 status):
  ```
  [org.apache.coyote.http2.Http2Parser] {https-jsse-nio-8443-exec-6} Connection [92], Stream [0], Frame type [null], Error
    java.io.IOException: Unable to unwrap data, invalid status [CLOSED]
    at org.apache.tomcat.util.net.SecureNioChannel.read(SecureNioChannel.java:772) ~[tomcat-coyote-10.1.24.jar:10.1.24]
    at org.apache.tomcat.util.net.NioEndpoint$NioSocketWrapper$NioOperationState.run(NioEndpoint.java:1609) ~[tomcat-coyote-10.1.24.jar:10.1.24]
    at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1190) [tomcat-util-10.1.24.jar:10.1.24]
    at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659) [tomcat-util-10.1.24.jar:10.1.24]
    at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) [tomcat-util-10.1.24.jar:10.1.24]
    at java.base/java.lang.Thread.run(Thread.java:840) [?:?]
  ```
  which again seems to imply a connection / stream being in a somewhat invalid state (by client ?).
  trace is mostly different but still happens around the same code context (`NioOperationState` reading request ?)