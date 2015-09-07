# lib-cave-batching [![Build Status](https://travis-ci.org/gilt/lib-cave-batching.svg?branch=master)](https://travis-ci.org/gilt/lib-cave-batching) [![Coverage Status](https://coveralls.io/repos/gilt/lib-cave-batching/badge.svg?branch=master&service=github)](https://coveralls.io/github/gilt/lib-cave-batching?branch=master)


A library that allows batching of metrics when publishing to CAVE. It requires a CAVE client, which can be downloaded from the apidoc website.

## Contents and Example Usage

### client.CaveBatchConfiguration

This trait must be extended to provide the configuration parameters for the batching client. There are two mandatory parameters, and four optional parameters.

* _organisationName_, _teamName_: mandatory String parameters to specify which organisation and team to publish metrics for.

* _sendTimeout_: optional FiniteDuration parameter, specifies the maximum duration after which a batch is emitted. Default is 30 seconds.

* _sendBatchSize_: maximum number of metrics in a batch. Default is 100 metrics.

* _maxAttempts_: number of attempts to send a batch of metrics. Default is 2. Set this to 1 if you  do not need to retry.
 
* _retryTimeout_: optional FiniteDuration parameter, specifies how long to wait between attempts. Default is 120 seconds.

```
val configuration = new CaveBatchConfiguration {
  override def organisationName = "gilt"
  override def teamName = "twain"
  override def sendTimeout = 10.seconds
  override def sendBatchSize = 1000
  override def maxAttempts = 5
  override def retryTimeout = 1.minute
}
```

### client.CaveBatchClient

Simple batching of metrics for CAVE. This class gathers raw metrics together, and sends to CAVE, as batches, in a timely fashion.

he approach in this class is to collect metrics into collections, and when full, or a timer (based on the age of the first metric in the collection) expires, that batch is sent to CAVE.

A minimal runtime impact is achieved by using a single Timer, and a single thread - the timer is used simply to schedule tasks to be executed on the thread, with the thread being used for all the work (interacting with CAVE, etc.).

Note: each instance of this class builds its own batches, and delivers to CAVE individually - multiple instances will result in multiple batches, if this is so desired.

```
val batchClient = new CaveBatchClient(configuration, client)
...
createOrganisationMetric(metric)
createTeamMetric(metric)
...
```

## License
Copyright 2014 Gilt Groupe, Inc.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
