# Kafka/Http4s service template

## Work In Progress 

Todo:

- [x] Use Tapir for http 
- [x] use testcontainers-scala with confluent platform
- [x] use avro serialization w/ schemas for messages 
- [x] cleanup/better logging
- [x] other service to interact with this one
- [x] added ZIO versions
- [ ] helm/k8s deployment
- [ ] HTTP Integration test 
- [ ] Boilerplate for paginated http apis 
- [ ] Run ITs into docker itself to avoid kafka port clashes
- [ ] monitoring
- [ ] write readme with examples


To deploy kafka locally for tests:
- docker-compose -f cats/backend/app/src/it/resources/docker-compose-kafka.yml up -d
- docker-compose -f cats/backend/app/src/it/resources/docker-compose-kafka.yml down
