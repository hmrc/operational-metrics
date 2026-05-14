
# Operational Metrics

operational-metrics is a service designed to calculate and track various operational metrics, including DORA metrics. These metrics help teams understand deployment performance and delivery efficiency.

### DORA Metrics
DORA (DevOps Research and Assessment) metrics are key indicators for software delivery performance.

### Service Lead Times
Measures the duration between slug creation and the first successful deployment to production for a service.

### ServiceNow Events
#### Seed LocalStack for testing:
 - Run SeedLocalStack to populate LocalStack with JSON via resources
 - DeploymentEventHandler will poll from LocalStack via SQSConsumer when endpoint override is present in application.conf:
   ```
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

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").