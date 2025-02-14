package com.polar.sdk.api.model.restapi

/**
 * Lists REST API services and corresponding paths
 */
data class PolarDeviceRestApiServices(val dictionary: Map<String, Any>) {
    /**
     * Maps available REST API service names to corresponding paths
      */
    val pathsForServices: Map<String,String>
        get() = dictionary["services"] as? Map<String,String> ?: mapOf()

    /**
     * Lists REST API service names
     */
    val serviceNames: List<String>
        get() = pathsForServices.keys.toList()

    /**
     * Lists REST API service paths
     */
    val servicePaths: List<String>
        get() = pathsForServices.values.toList()
}

/**
 *  Describes specific service API per SAGRFC95
 */
data class PolarDeviceRestApiServiceDescription (
    val dictionary: Map<String, Any>
)

/**
 * Events that can be acted upon using actions. Actions are returned in `actions` and `actionNames`
 * properties.
 */
val PolarDeviceRestApiServiceDescription.events: List<String>
    get() = dictionary["events"] as? List<String> ?: listOf<String>()

/**
 * Endpoints that can be applied in **endpoint=** parameter in paths from `actions` and `actionPaths`
 */
val PolarDeviceRestApiServiceDescription.endpoints: List<String>
    get() = dictionary["endpoints"] as? List<String> ?: listOf<String>()

/**
 * Actions/commands that can be sent, using put operation of corresponding path string
 * Path strings can contain following placeholders:
 *
 * **event=**: event name may follow equal to sign in path. Event names are listed using `events`
 * property. If given, the action targets the event.
 *
 * **resend=**: true or false may follow equal sign in path. true means client would like to receive
 * old events passed since last drop of connection
 *
 * **details=[]**: list of detail names may follow equal sign in path, specifying event detailed
 * data. Details are listed using `eventDetails`.
 *
 * **triggers=[]**: list of triggers may follow equal sign in path, specifying triggering related
 * to action. Triggers are listed using `eventTriggers`.
 *
 * **endpoint=**: endpoint, listed by `endpoints`, that is related to the action. This can be used
 * in post action paths.
 *
 */
val PolarDeviceRestApiServiceDescription.actions: Map<String, String>
    get() = dictionary["cmd"] as? Map<String, String> ?: mapOf<String, String>()

/**
 * Just the action names from `actions` property
 */
val PolarDeviceRestApiServiceDescription.actionNames: List<String>
    get() = actions.keys.toList()

/**
 * Just the action paths from `actions` property
 */
val PolarDeviceRestApiServiceDescription.actionPaths: List<String>
    get() = actions.values.toList()

/**
 * Lists event details that may be requested as returned event parameter values using action
 * path containing **details=[]** parameter placeholder
 * @param eventName the REST API event to get details for
 * @return detail names
 */
fun PolarDeviceRestApiServiceDescription.eventDetailsFor(eventName: String): List<String> {
    val eventMap = dictionary[eventName] as? Map<String, List<String>> ?: mapOf<String, List<String>>()
    return eventMap["details"] as? List<String> ?: listOf<String>()
}

/**
 * Lists triggers that may be used as trigger parameter list values when action path contains
 * **triggers=[]** parameter placeholder
 * @param eventName  the REST API event to get triggers for
 * @return triggers for the events
 */
fun PolarDeviceRestApiServiceDescription.eventTriggersFor(eventName: String): List<String> {
    val eventMap = dictionary[eventName] as? Map<String, List<String>> ?: mapOf<String, List<String>>()
    return eventMap["triggers"] as? List<String> ?: listOf<String>()
}