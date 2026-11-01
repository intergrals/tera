package com.envelope.budget.data

import androidx.room.withTransaction
import com.envelope.budget.data.db.AccountEntity
import com.envelope.budget.data.db.AllocationEntity
import com.envelope.budget.data.db.AppDatabase
import com.envelope.budget.data.db.CategoryEntity
import com.envelope.budget.data.db.CategoryGroupEntity
import com.envelope.budget.data.db.ScheduledEntity
import com.envelope.budget.data.db.TargetSnoozeEntity
import com.envelope.budget.data.db.TransactionEntity
import com.envelope.budget.domain.ClearanceState
import com.envelope.budget.domain.FlagColor
import com.envelope.budget.domain.OverspendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * JSON backup / restore and CSV export.
 *
 * Backups carry `schemaVersion` from day one plus the app settings, so a restore is a
 * complete, versioned snapshot rather than just the tables. Restore validates the whole
 * document (including referential integrity) before touching the database, and then
 * replaces everything inside one transaction.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("app", "Envelope")

        val current = settings.current()
        root.put(
            "settings",
            JSONObject().apply {
                put("currencySymbol", current.currencySymbol)
                put("overspendMode", current.overspendMode.name)
                put("onboardingDone", current.onboardingDone)
            },
        )

        root.put("accounts", db.accountDao().getAll().toJsonArray { it.toJson() })
        root.put("categoryGroups", db.categoryDao().getGroups().toJsonArray { it.toJson() })
        root.put("categories", db.categoryDao().getCategories().toJsonArray { it.toJson() })
        root.put("targetSnoozes", db.targetSnoozeDao().getAll().toJsonArray { it.toJson() })
        root.put("allocations", db.allocationDao().getAll().toJsonArray { it.toJson() })
        root.put("transactions", db.transactionDao().getAllOrdered().toJsonArray { it.toJson() })
        root.put("scheduled", db.scheduledDao().getAll().toJsonArray { it.toJson() })

        root.toString(2)
    }

    suspend fun importJson(text: String): RestoreSummary = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw DomainException("That file is not a valid Envelope backup")
        }

        val version = root.optInt("schemaVersion", -1)
        if (version < 0) throw DomainException("Backup is missing its schema version")
        if (version > SCHEMA_VERSION) {
            throw DomainException("This backup was made by a newer version of Envelope")
        }

        val accounts = root.readArray("accounts") { it.toAccount() }
        val groups = root.readArray("categoryGroups") { it.toGroup() }
        val categories = root.readArray("categories") { it.toCategory() }
        val snoozes = root.readArray("targetSnoozes") { it.toSnooze() }
        val allocations = root.readArray("allocations") { it.toAllocation() }
        val transactions = root.readArray("transactions") { it.toTransaction() }
        val scheduled = root.readArray("scheduled") { it.toScheduled() }

        validate(
            accounts, groups, categories, snoozes, allocations, transactions, scheduled,
        )

        val restoredSettings = root.optJSONObject("settings")?.let { json ->
            AppSettings(
                currencySymbol = json.optString("currencySymbol", "$").ifBlank { "$" },
                overspendMode = OverspendMode.fromStorage(json.optString("overspendMode")),
                onboardingDone = json.optBoolean("onboardingDone", true),
            )
        }

        db.withTransaction {
            db.transactionDao().deleteAll()
            db.scheduledDao().deleteAll()
            db.allocationDao().deleteAll()
            db.targetSnoozeDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().deleteAllGroups()
            db.accountDao().deleteAll()

            accounts.forEach { db.accountDao().insert(it) }
            groups.forEach { db.categoryDao().insertGroup(it) }
            categories.forEach { db.categoryDao().insert(it) }
            snoozes.forEach { db.targetSnoozeDao().insert(it) }
            allocations.forEach { db.allocationDao().upsert(it) }
            transactions.forEach { db.transactionDao().insert(it) }
            scheduled.forEach { db.scheduledDao().insert(it) }
        }

        restoredSettings?.let { settings.restore(it) }

        RestoreSummary(
            accounts = accounts.size,
            categories = categories.size,
            transactions = transactions.size,
            allocations = allocations.size,
        )
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().getAll().associateBy { it.id }
        val categories = db.categoryDao().getCategories().associateBy { it.id }
        val builder = StringBuilder()
        builder.append("Date,Account,Payee,Category,Memo,Amount,Cleared,Flag\n")
        db.transactionDao().getAllOrdered().forEach { tx ->
            val accountName = accounts[tx.accountId]?.name ?: ""
            val categoryName = tx.categoryId?.let { categories[it]?.name } ?: ""
            val cleared = ClearanceState.fromOrdinal(tx.cleared).name.lowercase()
            val flag = FlagColor.fromOrdinal(tx.flag).name.lowercase()
            builder.append(csv(tx.date))
                .append(',').append(csv(accountName))
                .append(',').append(csv(tx.payee))
                .append(',').append(csv(categoryName))
                .append(',').append(csv(tx.memo))
                .append(',').append(csv(formatAmount(tx.amountCents)))
                .append(',').append(csv(cleared))
                .append(',').append(csv(flag))
                .append('\n')
        }
        builder.toString()
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.scheduledDao().deleteAll()
            db.allocationDao().deleteAll()
            db.targetSnoozeDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().deleteAllGroups()
            db.accountDao().deleteAll()
        }
        settings.setOnboardingDone(false)
    }

    // ---------------------------------------------------------------- validation

    private fun validate(
        accounts: List<AccountEntity>,
        groups: List<CategoryGroupEntity>,
        categories: List<CategoryEntity>,
        snoozes: List<TargetSnoozeEntity>,
        allocations: List<AllocationEntity>,
        transactions: List<TransactionEntity>,
        scheduled: List<ScheduledEntity>,
    ) {
        val accountIds = accounts.map { it.id }.toSet()
        val groupIds = groups.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()

        if (accountIds.size != accounts.size) fail("Duplicate account ids in backup")
        if (categoryIds.size != categories.size) fail("Duplicate category ids in backup")

        categories.forEach { category ->
            if (category.groupId !in groupIds) {
                fail("Category \u201C${category.name}\u201D points at a missing group")
            }
        }
        transactions.forEach { tx ->
            if (tx.accountId !in accountIds) fail("A transaction points at a missing account")
            if (tx.categoryId != null && tx.categoryId !in categoryIds) {
                fail("A transaction points at a missing category")
            }
            if (tx.amountCents == 0L) fail("A transaction has a zero amount")
            parseDate(tx.date)
        }
        transactions.filter { it.transferPeerId != null }.forEach { tx ->
            val peer = transactions.firstOrNull { it.id == tx.transferPeerId }
            if (peer == null) fail("A transfer is missing its other half")
            if (peer.transferPeerId != tx.id) fail("A transfer's two halves disagree")
        }
        snoozes.forEach { if (it.categoryId !in categoryIds) fail("A target snooze is orphaned") }
        allocations.forEach {
            if (it.categoryId !in categoryIds) fail("An assignment points at a missing category")
            if (!it.month.matches(Regex("\\d{4}-\\d{2}"))) fail("An assignment has a bad month")
        }
        scheduled.forEach {
            if (it.accountId !in accountIds) fail("A scheduled transaction points at a missing account")
            if (it.categoryId != null && it.categoryId !in categoryIds) {
                fail("A scheduled transaction points at a missing category")
            }
        }
    }

    private fun parseDate(iso: String) {
        try {
            LocalDate.parse(iso)
        } catch (_: Exception) {
            fail("A transaction has an unreadable date: $iso")
        }
    }

    private fun fail(message: String): Nothing = throw DomainException(message)

    // ---------------------------------------------------------------- JSON mapping

    private fun AccountEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type)
        put("startingBalanceCents", startingBalanceCents)
        put("startingDate", startingDate)
        put("isTracking", isTracking); put("isClosed", isClosed)
        put("sortOrder", sortOrder); put("createdAt", createdAt)
    }

    private fun CategoryGroupEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("sortOrder", sortOrder)
        put("isHidden", isHidden); put("isSystem", isSystem)
    }

    private fun CategoryEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("groupId", groupId); put("name", name); put("note", note)
        put("sortOrder", sortOrder); put("isHidden", isHidden); put("isSystem", isSystem)
        put("systemKind", systemKind ?: JSONObject.NULL)
        put("linkedAccountId", linkedAccountId ?: JSONObject.NULL)
        put("targetType", targetType)
        put("targetAmountCents", targetAmountCents ?: JSONObject.NULL)
        put("targetDate", targetDate ?: JSONObject.NULL)
    }

    private fun TargetSnoozeEntity.toJson(): JSONObject = JSONObject().apply {
        put("categoryId", categoryId); put("month", month)
    }

    private fun AllocationEntity.toJson(): JSONObject = JSONObject().apply {
        put("month", month); put("categoryId", categoryId); put("assignedCents", assignedCents)
    }

    private fun TransactionEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("accountId", accountId)
        put("categoryId", categoryId ?: JSONObject.NULL)
        put("date", date); put("payee", payee); put("memo", memo)
        put("amountCents", amountCents); put("cleared", cleared); put("flag", flag)
        put("transferPeerId", transferPeerId ?: JSONObject.NULL)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun ScheduledEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("accountId", accountId)
        put("categoryId", categoryId ?: JSONObject.NULL)
        put("payee", payee); put("memo", memo); put("amountCents", amountCents)
        put("frequency", frequency); put("nextDate", nextDate)
        put("lastPostedDate", lastPostedDate ?: JSONObject.NULL)
        put("isActive", isActive); put("isTransfer", isTransfer)
        put("transferAccountId", transferAccountId ?: JSONObject.NULL)
    }

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(mapper(it)) } }

    private fun <T> JSONObject.readArray(key: String, mapper: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { index ->
            mapper(array.getJSONObject(index))
        }
    }

    private fun JSONObject.toAccount(): AccountEntity = AccountEntity(
        id = getLong("id"),
        name = getString("name"),
        type = getString("type"),
        startingBalanceCents = getLong("startingBalanceCents"),
        startingDate = getString("startingDate"),
        isTracking = optBoolean("isTracking", false),
        isClosed = optBoolean("isClosed", false),
        sortOrder = optInt("sortOrder", 0),
        createdAt = optLong("createdAt", 0L),
    )

    private fun JSONObject.toGroup(): CategoryGroupEntity = CategoryGroupEntity(
        id = getLong("id"),
        name = getString("name"),
        sortOrder = optInt("sortOrder", 0),
        isHidden = optBoolean("isHidden", false),
        isSystem = optBoolean("isSystem", false),
    )

    private fun JSONObject.toCategory(): CategoryEntity = CategoryEntity(
        id = getLong("id"),
        groupId = getLong("groupId"),
        name = getString("name"),
        note = optString("note", ""),
        sortOrder = optInt("sortOrder", 0),
        isHidden = optBoolean("isHidden", false),
        isSystem = optBoolean("isSystem", false),
        systemKind = optNullableString("systemKind"),
        linkedAccountId = optNullableLong("linkedAccountId"),
        targetType = optString("targetType", "NONE"),
        targetAmountCents = optNullableLong("targetAmountCents"),
        targetDate = optNullableString("targetDate"),
    )

    private fun JSONObject.toSnooze(): TargetSnoozeEntity = TargetSnoozeEntity(
        categoryId = getLong("categoryId"),
        month = getString("month"),
    )

    private fun JSONObject.toAllocation(): AllocationEntity = AllocationEntity(
        month = getString("month"),
        categoryId = getLong("categoryId"),
        assignedCents = getLong("assignedCents"),
    )

    private fun JSONObject.toTransaction(): TransactionEntity = TransactionEntity(
        id = getLong("id"),
        accountId = getLong("accountId"),
        categoryId = optNullableLong("categoryId"),
        date = getString("date"),
        payee = optString("payee", ""),
        memo = optString("memo", ""),
        amountCents = getLong("amountCents"),
        cleared = optInt("cleared", 0),
        flag = optInt("flag", 0),
        transferPeerId = optNullableLong("transferPeerId"),
        createdAt = optLong("createdAt", 0L),
        updatedAt = optLong("updatedAt", 0L),
    )

    private fun JSONObject.toScheduled(): ScheduledEntity = ScheduledEntity(
        id = getLong("id"),
        accountId = getLong("accountId"),
        categoryId = optNullableLong("categoryId"),
        payee = optString("payee", ""),
        memo = optString("memo", ""),
        amountCents = getLong("amountCents"),
        frequency = optString("frequency", "MONTHLY"),
        nextDate = getString("nextDate"),
        lastPostedDate = optNullableString("lastPostedDate"),
        isActive = optBoolean("isActive", true),
        isTransfer = optBoolean("isTransfer", false),
        transferAccountId = optNullableLong("transferAccountId"),
    )

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (isNull(key) || !has(key)) return null
        return optLong(key)
    }

    // ---------------------------------------------------------------- CSV helpers

    private fun formatAmount(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val text = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    data class RestoreSummary(
        val accounts: Int,
        val categories: Int,
        val transactions: Int,
        val allocations: Int,
    )

    companion object {
        const val SCHEMA_VERSION = 1
        const val UTF8_BOM = "\uFEFF"
    }
}
