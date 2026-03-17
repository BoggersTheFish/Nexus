package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.badgersmc.nexus.commands.CommandDefinition
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import javax.annotation.Nonnull
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

/**
 * Adapter for async commands (AbstractAsyncCommand).
 *
 * Async commands run on a background thread and have no access to world/entity state.
 * They're suitable for commands that don't need world data (e.g., rules, help).
 *
 * This adapter:
 * 1. Creates Hytale arguments in the constructor
 * 2. Extracts argument values from CommandContext in executeAsync()
 * 3. Invokes the user's execute() method with proper parameters
 * 4. Handles suspend functions via runBlocking
 */
class AsyncCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractAsyncCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val arguments = mutableListOf<Any>() // Hytale Argument instances
    private val isSuspend = definition.executeMethod.isSuspend
    private val javaMethod: Method = definition.executeMethod.javaMethod
        ?: throw CommandException("Could not get Java method for command '${definition.annotation.name}'")

    init {
        // Create Hytale arguments for each @Arg parameter
        for (param in definition.parameters.filter { it.isArg }) {
            val resolver = ArgumentResolvers.get(param.type)
                ?: throw CommandException(
                    "No ArgumentResolver for type ${param.type.simpleName} in command '${definition.annotation.name}'"
                )

            val argAnnotation = param.argAnnotation!!
            val hytaleArg = when {
                argAnnotation.required && argAnnotation.defaultValue.isEmpty() ->
                    resolver.createRequiredArg(this, argAnnotation.name, argAnnotation.description)

                argAnnotation.defaultValue.isNotEmpty() ->
                    resolver.createDefaultArg(this, argAnnotation.name, argAnnotation.description, argAnnotation.defaultValue)

                else ->
                    resolver.createOptionalArg(this, argAnnotation.name, argAnnotation.description)
            }

            arguments.add(hytaleArg)
        }

        // Register permission if specified
        if (definition.annotation.permission.isNotEmpty()) {
            requirePermission(HytalePermissions.fromCommand(definition.annotation.permission))
        }

        // Register aliases
        if (definition.annotation.aliases.isNotEmpty()) {
            addAliases(*definition.annotation.aliases)
        }

    }

    override fun executeAsync(@Nonnull context: CommandContext): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync {
            try {
                val params = buildParameterArray(context)
                invokeExecuteMethod(params)
            } catch (e: Exception) {
                println("[HyCore] Command '${definition.annotation.name}' execution failed: ${e.message}")
                e.printStackTrace()
                context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
            }
            null
        }
    }

    /**
     * Build the parameter array for the execute() method.
     */
    private fun buildParameterArray(context: CommandContext): Array<Any?> {
        val params = mutableListOf<Any?>()
        var argIndex = 0

        for (param in definition.parameters) {
            params.add(when {
                param.isArg -> {
                    // Extract argument value from context.
                    // OptionalArg.get() returns null when the flag was not supplied —
                    // coerce null to the zero value for the param type so command code
                    // never receives null for a non-nullable Kotlin type.
                    val arg = arguments[argIndex++]
                    val method = arg::class.java.getMethod("get", CommandContext::class.java)
                    val value = method.invoke(arg, context)
                    value ?: zeroValue(param.type)
                }
                param.isContext -> {
                    // Inject context value
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        else -> throw CommandException(
                            "Async commands only support @Context CommandContext. " +
                            "Found: ${param.type.simpleName} in command '${definition.annotation.name}'"
                        )
                    }
                }
                else -> throw CommandException("Parameter has neither @Arg nor @Context")
            })
        }

        return params.toTypedArray()
    }

    /**
     * Invoke the user's execute() method via Java reflection (avoids KotlinReflectionInternalError
     * on built-in types like String/Int that Kotlin reflection cannot introspect at runtime).
     * Suspend functions receive a Continuation as the last argument via runBlocking.
     */
    private fun invokeExecuteMethod(params: Array<Any?>) {
        if (isSuspend) {
            runBlocking {
                suspendCancellableCoroutine<Any?> { cont ->
                    javaMethod.invoke(commandBean, *params, cont)
                }
            }
        } else {
            javaMethod.invoke(commandBean, *params)
        }
    }

    /**
     * Return the zero/empty value for a type when an OptionalArg was not supplied.
     * OptionalArg.get() returns null when the flag is absent; this prevents NPEs
     * in command code that declares non-nullable Kotlin types.
     */
    private fun zeroValue(type: KClass<*>): Any = when (type) {
        String::class  -> ""
        Int::class     -> 0
        Long::class    -> 0L
        Double::class  -> 0.0
        Float::class   -> 0f
        Boolean::class -> false
        else           -> ""
    }
}
