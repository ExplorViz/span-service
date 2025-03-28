package net.explorviz.span.adapter.injection

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces

@Dependent
class MockSchemaRegistryClientProducer {

    @Produces
    @IfBuildProfile("test")
    fun produceMockSchemaRegistry(): SchemaRegistryClient {
        return MockSchemaRegistryClient()
    }
}
