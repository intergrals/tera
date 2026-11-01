package com.tera.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isTracking ASC, sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isTracking ASC, sortOrder ASC, name ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun transactionCount(accountId: Long): Int

    @Query("UPDATE accounts SET isClosed = :closed WHERE id = :id")
    suspend fun setClosed(id: Long, closed: Boolean)

    @Query("SELECT * FROM accounts WHERE type = 'CREDIT' AND isTracking = 0")
    suspend fun getCreditAccounts(): List<AccountEntity>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category_groups ORDER BY sortOrder ASC, name ASC")
    fun observeGroups(): Flow<List<CategoryGroupEntity>>

    @Query("SELECT * FROM category_groups ORDER BY sortOrder ASC, name ASC")
    suspend fun getGroups(): List<CategoryGroupEntity>

    @Insert
    suspend fun insertGroup(group: CategoryGroupEntity): Long

    @Update
    suspend fun updateGroup(group: CategoryGroupEntity)

    @Delete
    suspend fun deleteGroup(group: CategoryGroupEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE linkedAccountId = :accountId LIMIT 1")
    suspend fun getPaymentCategoryFor(accountId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE systemKind = :kind LIMIT 1")
    suspend fun getSystemCategory(kind: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun transactionCount(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM budget_allocations WHERE categoryId = :categoryId AND assignedCents != 0")
    suspend fun nonZeroAllocationCount(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM scheduled_transactions WHERE categoryId = :categoryId")
    suspend fun scheduledCount(categoryId: Long): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("DELETE FROM category_groups")
    suspend fun deleteAllGroups()
}

@Dao
interface TargetSnoozeDao {

    @Query("SELECT * FROM target_snoozes")
    fun observeAll(): Flow<List<TargetSnoozeEntity>>

    @Query("SELECT * FROM target_snoozes")
    suspend fun getAll(): List<TargetSnoozeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snooze: TargetSnoozeEntity)

    @Query("DELETE FROM target_snoozes WHERE categoryId = :categoryId AND month = :month")
    suspend fun delete(categoryId: Long, month: String)

    @Query("DELETE FROM target_snoozes")
    suspend fun deleteAll()
}

@Dao
interface AllocationDao {

    @Query("SELECT * FROM budget_allocations WHERE month = :month")
    fun observeMonth(month: String): Flow<List<AllocationEntity>>

    @Query("SELECT * FROM budget_allocations")
    fun observeAll(): Flow<List<AllocationEntity>>

    @Query("SELECT * FROM budget_allocations")
    suspend fun getAll(): List<AllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE month = :month")
    suspend fun getMonth(month: String): List<AllocationEntity>

    @Query("SELECT * FROM budget_allocations WHERE month = :month AND categoryId = :categoryId")
    suspend fun get(month: String, categoryId: Long): AllocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allocation: AllocationEntity)

    @Query("DELETE FROM budget_allocations WHERE month = :month AND categoryId = :categoryId")
    suspend fun clear(month: String, categoryId: Long)

    @Query("DELETE FROM budget_allocations")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date ASC, id ASC")
    suspend fun getAllOrdered(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeForAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date ASC, id ASC")
    suspend fun getForAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferPeerId = :id")
    suspend fun getByPeerId(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT payee FROM transactions WHERE payee != '' ORDER BY payee ASC")
    suspend fun distinctPayees(): List<String>

    @Query("UPDATE transactions SET cleared = 2 WHERE accountId = :accountId AND cleared = 1")
    suspend fun markClearedAsReconciled(accountId: Long)

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND cleared = 0 ORDER BY date ASC")
    suspend fun getUnclearedForAccount(accountId: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:accountId = -1 OR accountId = :accountId)
          AND (:query = '' OR payee LIKE '%' || :query || '%'
               OR memo LIKE '%' || :query || '%'
               OR amountCents LIKE '%' || :query || '%')
        ORDER BY date DESC, id DESC
        """,
    )
    suspend fun search(query: String, accountId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface ScheduledDao {

    @Query("SELECT * FROM scheduled_transactions ORDER BY nextDate ASC, id ASC")
    fun observeAll(): Flow<List<ScheduledEntity>>

    @Query("SELECT * FROM scheduled_transactions ORDER BY nextDate ASC, id ASC")
    suspend fun getAll(): List<ScheduledEntity>

    @Query("SELECT * FROM scheduled_transactions WHERE id = :id")
    suspend fun getById(id: Long): ScheduledEntity?

    @Insert
    suspend fun insert(scheduled: ScheduledEntity): Long

    @Update
    suspend fun update(scheduled: ScheduledEntity)

    @Query("DELETE FROM scheduled_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_transactions")
    suspend fun deleteAll()
}
