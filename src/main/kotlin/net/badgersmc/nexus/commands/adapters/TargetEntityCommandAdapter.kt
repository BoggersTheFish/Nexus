package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import it.unimi.dsi.fastutil.objects.ObjectList
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
 * Adapter for target entity commands (AbstractTargetEntityCommand).
 *
 * Target entity commands use raycasting to find entities in the player's view and run
 * on the world thread. The execute() signature (from JAR bytecode inspection) is:
 *
 *   execute(context, entities, world, store)
 *
 * Where:
 * - entities — ObjectList<Ref<EntityStore>> of entities in view (from fastutil, bundled in Hytale JAR)
 * - world    — World the command was executed in
 * - store    — Store<EntityStore> for the world
 *
 * Supported @Context types: CommandContext, World, Store<EntityStore>, ObjectList
 */
class TargetEntityCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractTargetEntityCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val arguments = mutableListOf<Any>()
    private val isSuspend = definition.executeMethod.isSuspend
    private val javaMethod: Method = definition.executeMethod.javaMethod
        ?: throw CommandException("Could not get Java method for command '${definition.annotation.name}'")

    init {
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

        if (definition.annotation.permission.isNotEmpty()) {
            requirePermission(HytalePermissions.fromCommand(definition.annotation.permission))
        }

        if (definition.annotation.aliases.isNotEmpty()) {
            addAliases(*definition.annotation.aliases)
        }

    }

    // Actual signature from JAR bytecode (NameAndType #256):
    //   execute(context, entities: ObjectList<Ref<EntityStore>>, world, store)
    override fun execute(
        @Nonnull context: CommandContext,
        @Nonnull entities: ObjectList<Ref<EntityStore>>,
        @Nonnull world: World,
        @Nonnull store: Store<EntityStore>
    ) {
        try {
            val params = buildParameterArray(context, entities, world, store)
            invokeExecuteMethod(params)
        } catch (e: Exception) {
            println("[HyCore] Command '${definition.annotation.name}' execution failed: ${e.message}")
            e.printStackTrace()
            context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
        }
    }

    private fun buildParameterArray(
        context: CommandContext,
        entities: ObjectList<Ref<EntityStore>>,
        world: World,
        store: Store<EntityStore>
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
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        "World" -> world
                        "Store" -> store
                        "ObjectList" -> entities
                        else -> throw CommandException(
                            "Unsupported @Context type '${param.type.simpleName}' in TARGET_ENTITY command '${definition.annotation.name}'. " +
                            "Supported: CommandContext, World, Store<EntityStore>, ObjectList<Ref<EntityStore>>"
                        )
                    }
                }
                else -> throw CommandException("Parameter has neither @Arg nor @Context")
            })
        }

        return params.toTypedArray()
    }

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
