package com.apollographql.apollo3.subscription

import com.apollographql.apollo3.api.ResponseAdapterCache
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.api.internal.json.BufferedSourceJsonReader
import com.apollographql.apollo3.api.internal.json.Utils.readRecursively
import com.apollographql.apollo3.testing.MockSubscription
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class ApolloOperationMessageSerializerTest {
  private val serializer = ApolloOperationMessageSerializer

  @Test
  fun writeClientMessage_init() {
    val message = OperationClientMessage.Init(mapOf(
        "param1" to "value1",
        "param2" to "value2"
    ))
    assertEquals(parseJson(serializer.writeClientMessage(message)), mapOf(
        "type" to "connection_init",
        "payload" to message.connectionParams
    ))
  }

  @Test
  fun writeClientMessage_start() {
    val subscription = MockSubscription()

    val regularQuery = OperationClientMessage.Start(
        subscriptionId = "subscription-id",
        subscription = subscription,
        responseAdapterCache = ResponseAdapterCache.DEFAULT,
        autoPersistSubscription = false,
        sendSubscriptionDocument = true
    )
    val persistedQueryWithoutDocument = OperationClientMessage.Start(
        subscriptionId = "subscription-id",
        subscription = subscription,
        responseAdapterCache = ResponseAdapterCache.DEFAULT,
        autoPersistSubscription = true,
        sendSubscriptionDocument = false
    )
    val persistedQueryWithDocument = OperationClientMessage.Start(
        subscriptionId = "subscription-id",
        subscription = subscription,
        responseAdapterCache = ResponseAdapterCache.DEFAULT,
        autoPersistSubscription = true,
        sendSubscriptionDocument = true
    )
    assertEquals(parseJson(serializer.writeClientMessage(regularQuery)), mapOf(
        "id" to regularQuery.subscriptionId,
        "type" to "subscribe",
        "payload" to mapOf(
            "variables" to subscription.variables(ResponseAdapterCache.DEFAULT).valueMap,
            "operationName" to subscription.name(),
            "query" to subscription.document()
        )
    ))
    assertEquals(parseJson(serializer.writeClientMessage(persistedQueryWithoutDocument)), mapOf(
        "id" to persistedQueryWithoutDocument.subscriptionId,
        "type" to "subscribe",
        "payload" to mapOf(
            "variables" to subscription.variables(ResponseAdapterCache.DEFAULT).valueMap,
            "operationName" to subscription.name(),
            "extensions" to mapOf(
                "persistedQuery" to mapOf(
                    "version" to 1,
                    "sha256Hash" to subscription.id()
                )
            )
        )
    ))
    assertEquals(parseJson(serializer.writeClientMessage(persistedQueryWithDocument)), mapOf(
        "id" to persistedQueryWithDocument.subscriptionId,
        "type" to "subscribe",
        "payload" to mapOf(
            "variables" to subscription.variables(ResponseAdapterCache.DEFAULT).valueMap,
            "operationName" to subscription.name(),
            "query" to subscription.document(),
            "extensions" to mapOf(
                "persistedQuery" to mapOf(
                    "version" to 1,
                    "sha256Hash" to subscription.id()
                )
            )
        )
    ))
  }

  @Test
  fun writeClientMessage_stop() {
    val message = OperationClientMessage.Stop("subscription-id")
    assertEquals(parseJson(serializer.writeClientMessage(message)),
        mapOf(
            "type" to "complete",
            "id" to "subscription-id"
        ))
  }

  @Test
  fun writeClientMessage_terminate() {
    val message = OperationClientMessage.Terminate()
    assertEquals(parseJson(serializer.writeClientMessage(message)),
        mapOf(
            "type" to "connection_terminate"
        ))
  }

  @Test
  fun readServerMessage_connectionAcknowledge() {
    assertEquals(
        serializer.readServerMessage("""{"type":"connection_ack"}"""),
        OperationServerMessage.ConnectionAcknowledge
    )
  }

  @Test
  fun readServerMessage_data() {
    assertEquals(
        serializer.readServerMessage("""{"type":"next","id":"some-id","payload":{"key":"value"}}"""),
        OperationServerMessage.Data(
            id = "some-id",
            payload = mapOf("key" to "value")
        ))
  }

  @Test
  fun readServerMessage_keepAlive() {
    assertEquals(serializer.readServerMessage("""{"type":"ka"}"""),
        OperationServerMessage.ConnectionKeepAlive)
  }

  @Test
  fun readServerMessage_error() {
    assertEquals(serializer.readServerMessage("""{"type":"error","id":"some-id","payload":{"key":"value"}}"""),
        OperationServerMessage.Error(
            id = "some-id",
            payload = mapOf("key" to "value")
        )
    )
  }

  @Test
  fun readServerMessage_connectionError() {
    assertEquals(serializer.readServerMessage("""{"type":"connection_error","payload":{"key":"value"}}"""),
        OperationServerMessage.ConnectionError(
            payload = mapOf("key" to "value")
        )
    )
  }

  @Test
  fun readServerMessage_complete() {
    assertEquals(serializer.readServerMessage("""{"type":"complete","id":"some-id"}"""),
        OperationServerMessage.Complete(
            id = "some-id"
        )
    )
  }

  @Test
  fun readServerMessage_unknown() {
    assertEquals(
        serializer.readServerMessage("invalid json"),
        OperationServerMessage.Unsupported("invalid json")
    )
    assertEquals(
        serializer.readServerMessage("{}"),
        OperationServerMessage.Unsupported("{}")
    )
    assertEquals(
        serializer.readServerMessage("""{"type":"unknown"}"""),
        OperationServerMessage.Unsupported("""{"type":"unknown"}""")
    )
  }

  private fun OperationMessageSerializer.writeClientMessage(message: OperationClientMessage): String =
      Buffer()
          .also { writeClientMessage(message, it) }
          .readUtf8()

  private fun OperationMessageSerializer.readServerMessage(json: String): OperationServerMessage =
      Buffer()
          .writeUtf8(json)
          .let { readServerMessage(it) }

  private fun parseJson(json: String): Any? =
      Buffer()
          .writeUtf8(json)
          .let(::BufferedSourceJsonReader)
          .readRecursively()
}
