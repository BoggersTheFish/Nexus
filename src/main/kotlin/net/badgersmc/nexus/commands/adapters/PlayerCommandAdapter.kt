package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.world.World
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.badgersmc.nexus.commands.CommandDefinition
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import java.lang.reflect.Method
import javax.annotation.Nonnull
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

/**
 * Adapter for player commands (AbstractPlayerCommand).
 *
 * Player commands run on the world thread and have access to:
 * - CommandContext (sender, messaging)
 * - World (the world where command was executed)
 * - Store<EntityStore> (entity component store)
 * - PlayerRef (reference to the player who executed the command)
 * - Ref<EntityStore> (entity reference to the player)
 *
 * This adapter:
 * 1. Creates Hytale arguments in the constructor
 * 2. Extracts argument values from CommandContext in execute()
 * 3. Injects context parameters based on type
 * 4. Invokes the user's execute() method with proper parameters
 * 5. Handles suspend functions via runBlocking
 */
class PlayerCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractPlayerCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val arguments = mutableListOf<Any>()
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

    override fun execute(
        @Nonnull context: CommandContext,
        @Nonnull store: Store<EntityStore>,
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull playerRef: PlayerRef,
        @Nonnull world: World
    ) {
        try {
            val params = buildParameterArray(context, store, ref, playerRef, world)
            invokeExecuteMethod(params)
        } catch (e: Exception) {
            println("[HyCore] Command '${definition.annotation.name}' execution failed: ${e.message}")
            e.printStackTrace()
            context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
        }
    }

    /**
     * Build the parameter array for the execute() method.
     */
    private fun buildParameterArray(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World
    ): Array<Any?> {
        val params = mutableListOf<Any?>()
        var argIndex = 0

        for (param in definition.parameters) {
            params.add(when {
                param.isArg -> {
                    val arg = arguments[argIndex++]
                    val method = arg::class.java.getMethod("get", CommandContext::class.java)
                    val value = method.invoke(arg, context)
                    value ?: zeroValue(param.type)
                }
                param.isContext -> {
                    // Inject context value based on type
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        "World" -> world
                        "Store" -> store
                        "PlayerRef" -> playerRef
                        "Ref" -> ref
                        else -> throw CommandException(
                            "Unsupported @Context type '${param.type.simpleName}' in command '${definition.annotation.name}'. " +
                            "Supported: CommandContext, World, Store<EntityStore>, PlayerRef, Ref<EntityStore>"
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
