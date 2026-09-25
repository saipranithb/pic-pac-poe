package com.thevaguebox.picpac.testing

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Test-only loader for the platform-neutral parity contract. */
class GoldenFixtureDocument private constructor(
    private val root: JsonObject,
    val requiredGroups: List<String>,
) {
    val caseCount: Int = requiredGroups.sumOf { group -> root.requireArray(group).size() }

    fun cases(group: String): List<GoldenFixtureCase> {
        contract(group in requiredGroups) { "fixture group $group is not declared in requiredFixtureGroups" }
        return root.requireArray(group).mapIndexed { index, element ->
            val value = element.requireObject("$group[$index]")
            GoldenFixtureCase(group, value.requireString("id"), value)
        }
    }

    fun objectValue(name: String): JsonObject = root.requireObject(name)

    fun requireHandledGroups(handledGroups: Set<String>) {
        val required = requiredGroups.toSet()
        contract(requiredGroups.size == required.size) { "requiredFixtureGroups contains duplicates" }
        contract(handledGroups == required) {
            val missing = required - handledGroups
            val unknown = handledGroups - required
            "Kotlin fixture ownership mismatch; missing=$missing unknown=$unknown"
        }
    }

    companion object {
        private const val SUPPORTED_SCHEMA = 1
        private val metadataKeys = setOf(
            "schemaVersion",
            "pathBase",
            "purpose",
            "sources",
            "encoding",
            "requiredFixtureGroups",
            "graphOracle",
        )

        fun load(path: Path = locateRepositoryRoot().resolve("docs/ios-handoff/golden-fixtures.json")): GoldenFixtureDocument {
            contract(Files.isRegularFile(path)) { "golden fixture file is missing: $path" }
            val root = Files.newBufferedReader(path).use { reader ->
                JsonParser.parseReader(reader).requireObject("root")
            }
            contract(root.requireInt("schemaVersion") == SUPPORTED_SCHEMA) {
                "unsupported golden fixture schema ${root["schemaVersion"]}; expected $SUPPORTED_SCHEMA"
            }
            contract(root.requireString("pathBase") == "repository root") {
                "pathBase must be repository root"
            }
            val requiredGroups = root.requireArray("requiredFixtureGroups").mapIndexed { index, value ->
                value.requireString("requiredFixtureGroups[$index]")
            }
            contract(requiredGroups.isNotEmpty()) { "requiredFixtureGroups must not be empty" }
            val unknownTopLevel = root.keySet() - metadataKeys - requiredGroups.toSet()
            contract(unknownTopLevel.isEmpty()) { "unhandled top-level fixture groups/keys: $unknownTopLevel" }

            val allIds = linkedSetOf<String>()
            requiredGroups.forEach { group ->
                val rows = root.requireArray(group)
                contract(rows.size() > 0) { "$group must contain at least one fixture" }
                rows.forEachIndexed { index, element ->
                    val fixture = element.requireObject("$group[$index]")
                    val id = fixture.requireString("id")
                    contract(allIds.add(id)) { "duplicate fixture id $id" }
                }
            }
            validateBoards(root, "root")
            return GoldenFixtureDocument(root, requiredGroups)
        }

        private fun locateRepositoryRoot(): Path {
            val start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
            return generateSequence(start) { current -> current.parent }
                .firstOrNull { candidate -> Files.isRegularFile(candidate.resolve("docs/ios-handoff/golden-fixtures.json")) }
                ?: throw FixtureContractException("cannot locate repository root from $start")
        }

        private fun validateBoards(element: JsonElement, context: String) {
            when {
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, child) ->
                    if (key == "board" || key == "expectedBoard") {
                        val cells = child.requireArray("$context.$key")
                        contract(cells.size() == 9) { "$context.$key must contain nine cells" }
                        cells.forEachIndexed { index, cell ->
                            contract(cell.isJsonNull || (cell.isJsonPrimitive && cell.asString in setOf("X", "O"))) {
                                "$context.$key[$index] must be null, X, or O"
                            }
                        }
                    } else {
                        validateBoards(child, "$context.$key")
                    }
                }
                element.isJsonArray -> element.asJsonArray.forEachIndexed { index, child ->
                    validateBoards(child, "$context[$index]")
                }
            }
        }
    }
}

data class GoldenFixtureCase(
    val group: String,
    val id: String,
    val value: JsonObject,
) {
    val label: String get() = "$group/$id"

    fun verify(block: () -> Unit) {
        try {
            block()
        } catch (failure: AssertionError) {
            throw AssertionError("$label: ${failure.message}", failure)
        } catch (failure: RuntimeException) {
            throw AssertionError("$label: ${failure.message}", failure)
        }
    }
}

class FixtureContractException(message: String) : IllegalArgumentException(message)

fun JsonObject.requireObject(name: String): JsonObject =
    get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        ?: throw FixtureContractException("$name must be an object")

fun JsonObject.optionalObject(name: String): JsonObject? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.requireObject(name)

fun JsonObject.requireArray(name: String): JsonArray =
    get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        ?: throw FixtureContractException("$name must be an array")

fun JsonObject.optionalArray(name: String): JsonArray? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.requireArray(name)

fun JsonObject.requireString(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?: throw FixtureContractException("$name must be a string")

fun JsonObject.optionalString(name: String): String? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.let { value ->
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString
        else throw FixtureContractException("$name must be a string when present")
    }

fun JsonObject.requireInt(name: String): Int {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal
        ?: throw FixtureContractException("$name must be an integer")
    return try {
        value.intValueExact()
    } catch (_: ArithmeticException) {
        throw FixtureContractException("$name must be an exact 32-bit integer")
    }
}

fun JsonObject.requireLong(name: String): Long {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal
        ?: throw FixtureContractException("$name must be an integer")
    return try {
        value.longValueExact()
    } catch (_: ArithmeticException) {
        throw FixtureContractException("$name must be an exact 64-bit integer")
    }
}

fun JsonObject.requireDouble(name: String): Double =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
        ?: throw FixtureContractException("$name must be a number")

fun JsonObject.requireBoolean(name: String): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
        ?: throw FixtureContractException("$name must be a boolean")

fun JsonObject.optionalBoolean(name: String): Boolean? =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.let { value ->
        if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) value.asBoolean
        else throw FixtureContractException("$name must be a boolean when present")
    }

fun JsonElement.requireObject(context: String): JsonObject =
    takeIf(JsonElement::isJsonObject)?.asJsonObject
        ?: throw FixtureContractException("$context must be an object")

fun JsonElement.requireArray(context: String): JsonArray =
    takeIf(JsonElement::isJsonArray)?.asJsonArray
        ?: throw FixtureContractException("$context must be an array")

fun JsonElement.requireString(context: String): String =
    takeIf { isJsonPrimitive && asJsonPrimitive.isString }?.asString
        ?: throw FixtureContractException("$context must be a string")

fun JsonArray.intValues(context: String): List<Int> = mapIndexed { index, element ->
    val value = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal
        ?: throw FixtureContractException("$context[$index] must be an integer")
    try {
        value.intValueExact()
    } catch (_: ArithmeticException) {
        throw FixtureContractException("$context[$index] must be an exact 32-bit integer")
    }
}

private inline fun contract(condition: Boolean, message: () -> String) {
    if (!condition) throw FixtureContractException(message())
}
