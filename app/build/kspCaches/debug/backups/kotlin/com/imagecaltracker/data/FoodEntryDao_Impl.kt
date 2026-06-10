package com.imagecaltracker.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class FoodEntryDao_Impl(
  __db: RoomDatabase,
) : FoodEntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfFoodEntry: EntityInsertAdapter<FoodEntry>

  private val __deleteAdapterOfFoodEntry: EntityDeleteOrUpdateAdapter<FoodEntry>

  private val __updateAdapterOfFoodEntry: EntityDeleteOrUpdateAdapter<FoodEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfFoodEntry = object : EntityInsertAdapter<FoodEntry>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `food_entries` (`id`,`name`,`calories`,`proteinG`,`carbsG`,`fatsG`,`timestampMillis`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: FoodEntry) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.calories.toLong())
        statement.bindLong(4, entity.proteinG.toLong())
        statement.bindLong(5, entity.carbsG.toLong())
        statement.bindLong(6, entity.fatsG.toLong())
        statement.bindLong(7, entity.timestampMillis)
      }
    }
    this.__deleteAdapterOfFoodEntry = object : EntityDeleteOrUpdateAdapter<FoodEntry>() {
      protected override fun createQuery(): String = "DELETE FROM `food_entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: FoodEntry) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfFoodEntry = object : EntityDeleteOrUpdateAdapter<FoodEntry>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `food_entries` SET `id` = ?,`name` = ?,`calories` = ?,`proteinG` = ?,`carbsG` = ?,`fatsG` = ?,`timestampMillis` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: FoodEntry) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.calories.toLong())
        statement.bindLong(4, entity.proteinG.toLong())
        statement.bindLong(5, entity.carbsG.toLong())
        statement.bindLong(6, entity.fatsG.toLong())
        statement.bindLong(7, entity.timestampMillis)
        statement.bindLong(8, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: FoodEntry): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfFoodEntry.insertAndReturnId(_connection, entry)
    _result
  }

  public override suspend fun delete(entry: FoodEntry): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfFoodEntry.handle(_connection, entry)
  }

  public override suspend fun update(entry: FoodEntry): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfFoodEntry.handle(_connection, entry)
  }

  public override fun observeRange(startMillis: Long, endMillis: Long): Flow<List<FoodEntry>> {
    val _sql: String = """
        |
        |        SELECT * FROM food_entries
        |        WHERE timestampMillis >= ? AND timestampMillis < ?
        |        ORDER BY timestampMillis DESC
        |        
        """.trimMargin()
    return createFlow(__db, false, arrayOf("food_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, startMillis)
        _argIndex = 2
        _stmt.bindLong(_argIndex, endMillis)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCalories: Int = getColumnIndexOrThrow(_stmt, "calories")
        val _columnIndexOfProteinG: Int = getColumnIndexOrThrow(_stmt, "proteinG")
        val _columnIndexOfCarbsG: Int = getColumnIndexOrThrow(_stmt, "carbsG")
        val _columnIndexOfFatsG: Int = getColumnIndexOrThrow(_stmt, "fatsG")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _result: MutableList<FoodEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: FoodEntry
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCalories: Int
          _tmpCalories = _stmt.getLong(_columnIndexOfCalories).toInt()
          val _tmpProteinG: Int
          _tmpProteinG = _stmt.getLong(_columnIndexOfProteinG).toInt()
          val _tmpCarbsG: Int
          _tmpCarbsG = _stmt.getLong(_columnIndexOfCarbsG).toInt()
          val _tmpFatsG: Int
          _tmpFatsG = _stmt.getLong(_columnIndexOfFatsG).toInt()
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          _item = FoodEntry(_tmpId,_tmpName,_tmpCalories,_tmpProteinG,_tmpCarbsG,_tmpFatsG,_tmpTimestampMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAll(): Flow<List<FoodEntry>> {
    val _sql: String = "SELECT * FROM food_entries ORDER BY timestampMillis DESC"
    return createFlow(__db, false, arrayOf("food_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCalories: Int = getColumnIndexOrThrow(_stmt, "calories")
        val _columnIndexOfProteinG: Int = getColumnIndexOrThrow(_stmt, "proteinG")
        val _columnIndexOfCarbsG: Int = getColumnIndexOrThrow(_stmt, "carbsG")
        val _columnIndexOfFatsG: Int = getColumnIndexOrThrow(_stmt, "fatsG")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _result: MutableList<FoodEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: FoodEntry
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCalories: Int
          _tmpCalories = _stmt.getLong(_columnIndexOfCalories).toInt()
          val _tmpProteinG: Int
          _tmpProteinG = _stmt.getLong(_columnIndexOfProteinG).toInt()
          val _tmpCarbsG: Int
          _tmpCarbsG = _stmt.getLong(_columnIndexOfCarbsG).toInt()
          val _tmpFatsG: Int
          _tmpFatsG = _stmt.getLong(_columnIndexOfFatsG).toInt()
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          _item = FoodEntry(_tmpId,_tmpName,_tmpCalories,_tmpProteinG,_tmpCarbsG,_tmpFatsG,_tmpTimestampMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
