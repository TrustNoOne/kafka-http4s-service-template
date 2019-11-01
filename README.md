# Kafka/Http4s service template

## Work In Progress 

Todo:

- [ ] Use Tapir for http 
- [x] use testcontainers-scala with confluent platform https://github.com/confluentinc/examples/blob/5.3.1-post/cp-all-in-one/docker-compose.yml
- [ ] use avro serialization w/ schemas for messages 
- [ ] helm/k8s deployment
- [x] cleanup/better logging
- [x] other service to interact with this one
- [ ] HTTP Integration test 
- [ ] Boilerplate for paginated http apis 
- [ ] Run ITs into docker itself to avoid kafka port clashes
- [ ] monitoring
- [ ] write readme with examples


To deploy kafka locally for tests:
- docker-compose -f backend-service/app/src/it/resources up -d
- docker-compose -f backend-service/app/src/it/resources down
