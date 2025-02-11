## Overview

Contains logs from runs that the issue was reproduced grouped by client type and date.
It includes trace / debug level details and if the log side is too large it only contains the logs around the failed request id.

You can see an error log on `server_out.log` with a `TraceId`. 
You can search for that trace id in the other logs to see the relevant details.