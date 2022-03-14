# Document Library Supervisor

The supervisor controls how documents are handled after they are ingested into the document library. 
It decides which consumer should process a document next and publishes a message to the appropriate queue.  
After all the other rules have been processed and assuming the `ANALYTICAL_SUPERVISOR` env var is `true` it will send the doc to the "analytical.supervisor" queue regardless of whether it has been processed previously by that consumer.

## Documentation

Please refer to the [documentation](docs/index.md) for more info about the supervisor.
 
## Runtime Configuration

The app allows runtime configuration via environment variables

* **MONGO_USERNAME** - login username for mongodb
* **MONGO_PASSWORD** - login password for mongodb
* **MONGO_HOST** - host to connect to
* **MONGO_PORT** - optional: port to connect to (default: 27017) 
* **MONGO_DOCLIB_DATABASE** - the doclib database
* **MONGO_AUTHSOURCE** - optional: database to authenticate against (default: admin)
* **MONGO_DOCUMENTS_COLLECTION** - the documents collection
* **RABBITMQ_USERNAME** - login username for rabbitmq
* **RABBITMQ_PASSWORD** - login password for rabbitmq
* **RABBITMQ_HOST** - host to connect to
* **RABBITMQ_PORT** - optional: port to connect to (default: 5672)
* **RABBITMQ_VHOST** - optional: vhost to connect to (default: /)
* **RABBITMQ_DOCLIB_EXCHANGE** - optional: exchange that the consumer should be bound to
* **CONSUMER_QUEUE** - optional: name of the queue to consume (default: klein.prefetch)
* **CONSUMER_CONCURRENCY** - optional: number of messages to handle concurrently (default: 1)
* **ANALYTICAL_SUPERVISOR** - `true` or `false` (default: `false`) whether docs should be queued onto the analytical supervisor after other processing has completed

## Testing
```bash
docker-compose up -d
sbt clean test it:test
```

## Dependency Scanning

https://github.com/albuch/sbt-dependency-check

The sbt-dependency-check plugin can be used to create a HTML report under `target/scala-x.x/dependency-check-report.html`

```bash
sbt dependencyCheck
```
