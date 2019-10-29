# Kafka/Http4s service template

## Work In Progress 

Todo:

- [ ] Make messages idempotent (i.e. facts/events)
- [ ] Use Tapir for http 
- [x] use testcontainers-scala with confluent platform https://github.com/confluentinc/examples/blob/5.3.1-post/cp-all-in-one/docker-compose.yml
- [ ] use avro serialization w/ schemas for messages 
- [ ] helm/k8s deployment
- [ ] more configuration
- [x] cleanup/better logging
- [ ] other service to interact with this one, maybe some external http endpoint for http client
- [ ] monitoring
- [ ] write readme with examples


To deploy kafka locally for tests:
- docker run --rm -it -e ADVERTISED_PORT=9092 -e ADVERTISED_HOST=127.0.0.1 -p 9092:9092 -p 2181:2181 spotify/kafka

