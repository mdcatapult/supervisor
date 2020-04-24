# Document Library Supervisor

This supervisor acts as the main flow control for document handling subsequent to being added by the prefetch consumer

## Documentation

Please refer to the [documentation](docs/index.md) for more info about the supervisor
 

## Runtime Configuration

The app allows runtime configuration via environment variables

* **MONGO_USERNAME** - login username for mongodb
* **MONGO_PASSWORD** - login password for mongodb
* **MONGO_HOST** - host to connect to
* **MONGO_PORT** - optional: port to connect to (default: 27017) 
* **MONGO_DATABASE** - database to connect to
* **MONGO_AUTH_DB** - optional: database to authenticate against (default: admin)
* **MONGO_COLLECTION** - default collection to read and write to
* **RABBITMQ_USERNAME** - login username for rabbitmq
* **RABBITMQ_PASSWORD** - login password for rabbitmq
* **RABBITMQ_HOST** - host to connect to
* **RABBITMQ_PORT** - optional: port to connect to (default: 5672)
* **RABBITMQ_VHOST** - optional: vhost to connect to (default: /)
* **RABBITMQ_EXCHANGE** - optional: exchange that the consumer should be bound to
* **UPSTREAM_QUEUE** - optional: name of the queue to consume (default: klein.prefetch)
* **UPSTREAM_CONCURRENT** - optional: number of messages to handle concurrently (default: 1)
* **AWS_ACCESS_KEY_ID** - optional: AWS access key for use when not run withing AWS 
* **AWS_SECRET_ACCESS_KEY** - optional: AWS secret key for use when not run withing AWS
* **ANALYTICAL_SUPERVISOR** - `true` or `false` (default: `false`) whether docs should be queued onto the analytical supervisor after other processing has completed
