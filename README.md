# Operational Metrics

`operational-metrics` collects and exposes operational delivery metrics for PlatOps services.

The service currently has two main responsibilities:

1. Calculate service lead time metrics used for DORA-style reporting.
2. Consume deployment events and send qualifying deployment records to ServiceNow change registration.

## Service Lead Times

Service lead time is the duration between slug creation and the first successful deployment of that slug to Production.

The `service-lead-times-updater` scheduler:

1. Gets current releases from `releases-api`.
2. Keeps Production releases only.
3. Gets slug creation details from `service-dependencies`.
4. Gets the first completed Production deployment for each service and version from `releases-api`.
5. Stores the calculated lead times in Mongo.

The values are exposed from:

```http
GET /operational-metrics/service-lead-times
```

Relevant configuration:

```hocon
service-lead-times-updater.enabled
service-lead-times-updater.interval
service-lead-times-updater.initialDelay

microservice.services.releases-api
microservice.services.service-dependencies
```

## ServiceNow Deployment Events

`operational-metrics` consumes deployment events from SQS, stores accepted events as Mongo work items, enriches them with deployment metadata, and sends them to ServiceNow.

### Data Flow

1. A deployment event is published to the configured SQS queue.
2. `DeploymentEventHandler` polls `aws.sqs.deployment.queueUrl` when `aws.sqs.enabled` is `true`.
3. The SQS message body is parsed into a `DeploymentEvent`.
4. Only these event types are eligible for ServiceNow:
   - `deployment-complete`
   - `undeployment-failed`
   - `undeployment-complete`
5. The deployment-event allow-list is applied.
6. Allowed events are persisted to the Mongo work item collection `deploymentEventsQueue`.
7. The SQS message is deleted after successful parsing and handling.
8. `ServiceNowEventStreamRunner` pulls outstanding work items from Mongo when `servicenow-stream.enabled` is `true`.
9. The work item is enriched with:
   - previous deployment details from `releases-api`
   - branch and commit details from `artefact-processor`
   - ServiceNow CMDB CI mapping from the `service-now-mappings` Mongo collection
10. The event is transformed into the ServiceNow change registration payload.
11. A ServiceNow OAuth token is requested from `servicenow.url/oauth_token.do`.
12. The change registration payload is posted to `servicenow.url/api/ukrc/hmrc_change_registration/change_registration`.
13. The work item is deleted after a successful ServiceNow response.
14. Failed sends are retried using the work item retry interval. After repeated failures the item is marked permanently failed.

### Event Filtering

`deployment-event-handler.allow-list` controls which parsed SQS events are persisted for ServiceNow processing.

```hocon
deployment-event-handler {
  allow-list {
    environments = ["production"]
    services     = []
  }
}
```

The allow-list is case-insensitive and trims whitespace.

An empty list means "allow all" for that dimension. For example:

```hocon
environments = ["production"]
services     = []
```

allows all services in Production.

For focused testing, set both dimensions:

```hocon
deployment-event-handler.allow-list.environments = ["qa"]
deployment-event-handler.allow-list.services     = ["internal-auth"]
```

In app-config YAML, lists should use indexed keys:

```yaml
deployment-event-handler.allow-list.environments.0: qa
deployment-event-handler.allow-list.services.0: internal-auth
```

Events that do not match the allow-list are logged and skipped. They are not persisted to `deploymentEventsQueue`.

### SQS Configuration

The SQS consumer is enabled by:

```hocon
aws.sqs.enabled = true
```

Deployment event queue settings are:

```hocon
aws.sqs.deployment.queueUrl
aws.sqs.deployment.maxNumberOfMessages
aws.sqs.deployment.waitTimeSeconds
```

For LocalStack testing, configure `endpointOverride`:

```hocon
aws.sqs {
  enabled = true
  deployment {
    endpointOverride    = "https://localhost.localstack.cloud:4566"
    queueUrl            = "https://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/mdtp-deployment-events"
    maxNumberOfMessages = 1
    waitTimeSeconds     = 20
  }
}
```

When `endpointOverride` is absent or blank, the AWS SDK uses the normal AWS SQS endpoint.

### Work Item Queue

Accepted deployment events are stored in Mongo using `hmrc-mongo` work items.

Collection:

```text
deploymentEventsQueue
```

Relevant configuration:

```hocon
queue.retryInterval
queue.ttl
```

`queue.retryInterval` controls how long failed work items wait before they are eligible for another send attempt.

`queue.ttl` controls expiry of work items via the `updatedAt` TTL index.

### ServiceNow Mapping From repository.yaml

Each service can provide its ServiceNow CMDB CI mapping in its `repository.yaml`.

The key name is controlled by:

```hocon
servicenow.repository-yaml-mapping-key = "serviceNowMapping"
```

With the default key, a mapped repository should contain:

```yaml
serviceNowMapping: SNSVC0005001
```

If the key is missing, blank, or only whitespace, the service is treated as unmapped and the ServiceNow payload uses:

```hocon
servicenow.default-cmdb-ci
```

Mappings are not read directly during every deployment event. The `service-now-mappings-updater` scheduler fetches repository data from `teams-and-repositories`, reads `repository.yaml`, and stores mappings in the Mongo collection `service-now-mappings`.

Relevant configuration:

```hocon
service-now-mappings-updater.enabled
service-now-mappings-updater.interval
service-now-mappings-updater.initialDelay

microservice.services.teams-and-repositories
servicenow.default-cmdb-ci
servicenow.repository-yaml-mapping-key
```

### ServiceNow Payload

The ServiceNow change registration request body is a single-element JSON array.

Example shape:

```json
[
  {
    "short_description": "internal-auth 0.122.0 deployed in qa",
    "description": "Pipeline execution ID: edge-123\nRepository: https://github.com/hmrc/internal-auth\nBranch: main",
    "cmdb_ci": "SNSVC0005001",
    "work_start": "15/05/2026 16:24:19",
    "work_end": "15/05/2026 17:24:19",
    "close_code": "successful",
    "correlation_id": "edge-123",
    "correlation_display": "MDTP"
  }
]
```

`description` is intentionally a single string. It contains the deployment details that were previously represented as separate fields.

`work_start` is derived from the deployment event time minus 10 minutes. `work_end` is the deployment event time. Both are formatted for ServiceNow as `dd/MM/yyyy HH:mm:ss` in the London time zone.

### ServiceNow OAuth and Proxy

ServiceNow calls use OAuth client credentials.

Relevant configuration:

```hocon
servicenow.url
servicenow.oauth.client-id
servicenow.oauth.client-secret
servicenow.oauth.expiry-buffer
```

The token endpoint is:

```text
{servicenow.url}/oauth_token.do
```

The change registration endpoint is:

```text
{servicenow.url}/api/ukrc/hmrc_change_registration/change_registration
```

Outbound ServiceNow traffic should use the PlatOps Squid proxy:

```hocon
http-verbs.proxy.enabled = true
proxy.protocol           = http
proxy.host               = outbound-proxy-vip
proxy.port               = 3128
proxy.username           = operational-metrics
proxy.password           = SECRET[proxy/password]
```

Squid must also allow the `operational-metrics` proxy user to reach the configured ServiceNow host. A `407 Proxy Authentication Required` from ServiceNow calls is a proxy-layer failure, not a ServiceNow OAuth failure.

## Important Configuration Summary

| Key | Purpose |
| --- | --- |
| `aws.sqs.enabled` | Starts the deployment event SQS consumer when `true`. |
| `aws.sqs.deployment.queueUrl` | SQS queue consumed for deployment events. |
| `aws.sqs.deployment.endpointOverride` | Optional LocalStack endpoint override. |
| `deployment-event-handler.allow-list.environments` | Environments accepted from deployment events. Empty means all environments. |
| `deployment-event-handler.allow-list.services` | Services accepted from deployment events. Empty means all services. |
| `servicenow-stream.enabled` | Starts the Mongo work item stream that sends events to ServiceNow. |
| `servicenow-stream.source-tick.initialDelay` | Delay before the ServiceNow stream starts polling. |
| `servicenow-stream.source-tick.interval` | Poll interval for ServiceNow work item processing. |
| `queue.retryInterval` | Retry interval for failed ServiceNow work items. |
| `queue.ttl` | TTL for deployment event work items. |
| `servicenow.url` | Base ServiceNow URL. Do not include endpoint paths. |
| `servicenow.oauth.client-id` | OAuth client ID for ServiceNow token generation. |
| `servicenow.oauth.client-secret` | OAuth client secret for ServiceNow token generation. |
| `servicenow.oauth.expiry-buffer` | Refresh buffer before token expiry. |
| `servicenow.default-cmdb-ci` | Fallback CMDB CI when no repository mapping exists. |
| `servicenow.repository-yaml-mapping-key` | `repository.yaml` key used to find the repo-specific CMDB CI. |
| `service-now-mappings-updater.*` | Controls the scheduler that refreshes repo to CMDB CI mappings. |
| `http-verbs.proxy.enabled` | Enables proxy use for calls using `.withProxy`. |
| `proxy.*` | Squid proxy connection and credentials. |

## Local Development

Run the service with the normal service-manager or sbt workflow used for PlatOps microservices.

For LocalStack SQS testing:

1. Start LocalStack.
2. Run `SeedLocalStack` to create/populate the local queue.
3. Enable `aws.sqs.enabled`.
4. Set `aws.sqs.deployment.endpointOverride`.
5. Set `aws.sqs.deployment.queueUrl` to the LocalStack queue URL.

Focused tests for the ServiceNow flow:

```bash
sbt --batch -Dsbt.server.forcestart=true 'set Test / fork := false' 'testOnly uk.gov.hmrc.operationalmetrics.servicenow.*'
```

## License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
