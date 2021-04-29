package com.apollographql.apollo3.subscription

import com.apollographql.apollo3.api.ResponseAdapterCache
import com.apollographql.apollo3.api.Subscription

sealed class OperationClientMessage {
  class Init(val connectionParams: Map<String, Any?>) : OperationClientMessage() {
    companion object {
      internal const val TYPE = "connection_init"
    }
  }

  class Start(
      val subscriptionId: String,
      val subscription: Subscription<*>,
      val responseAdapterCache: ResponseAdapterCache,
      val autoPersistSubscription: Boolean,
      /**
       * whether or not to send the document. Only valid if [autoPersistSubscription] is true
       */
      val sendSubscriptionDocument: Boolean
  ) : OperationClientMessage() {
    companion object {
      internal const val TYPE = "subscribe"
    }
  }

  class Stop(val subscriptionId: String) : OperationClientMessage() {
    companion object {
      internal const val TYPE = "complete"
    }
  }

  class Terminate : OperationClientMessage() {
    companion object {
      internal const val TYPE = "connection_terminate"
    }
  }
}