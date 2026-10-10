package com.teppe21.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.teppe21.finances.data.local.entity.StockQuoteEntity
import com.teppe21.finances.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY position ASC, addedAt ASC")
    fun getAllFlow(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist ORDER BY position ASC, addedAt ASC")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun getBySymbol(symbol: String): WatchlistEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM watchlist")
    suspend fun maxPosition(): Int

    /** Returns -1 when the symbol is already on the watchlist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WatchlistEntity): Long

    @Query("UPDATE watchlist SET position = :position WHERE symbol = :symbol")
    suspend fun updatePosition(symbol: String, position: Int)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM watchlist")
    suspend fun deleteAll()
}

@Dao
interface StockQuoteDao {
    @Query("SELECT * FROM stock_quotes")
    fun getAllFlow(): Flow<List<StockQuoteEntity>>

    @Query("SELECT * FROM stock_quotes WHERE symbol = :symbol LIMIT 1")
    suspend fun get(symbol: String): StockQuoteEntity?

    @Upsert
    suspend fun upsert(entity: StockQuoteEntity)

    @Query("DELETE FROM stock_quotes WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM stock_quotes")
    suspend fun deleteAll()
}
