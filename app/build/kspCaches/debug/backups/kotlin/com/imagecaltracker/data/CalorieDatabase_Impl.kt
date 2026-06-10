package com.imagecaltracker.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class CalorieDatabase_Impl : CalorieDatabase() {
  private val _foodEntryDao: Lazy<FoodEntryDao> = lazy {
    FoodEntryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "de877b0db351221318879dfd143f5974", "05aa070b1f01650035e8ac812d33bb0e") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `food_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `calories` INTEGER NOT NULL, `proteinG` INTEGER NOT NULL, `carbsG` INTEGER NOT NULL, `fatsG` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'de877b0db351221318879dfd143f5974')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `food_entries`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsFoodEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsFoodEntries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("calories", TableInfo.Column("calories", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("proteinG", TableInfo.Column("proteinG", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("carbsG", TableInfo.Column("carbsG", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("fatsG", TableInfo.Column("fatsG", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsFoodEntries.put("timestampMillis", TableInfo.Column("timestampMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysFoodEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesFoodEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoFoodEntries: TableInfo = TableInfo("food_entries", _columnsFoodEntries, _foreignKeysFoodEntries, _indicesFoodEntries)
        val _existingFoodEntries: TableInfo = read(connection, "food_entries")
        if (!_infoFoodEntries.equals(_existingFoodEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |food_entries(com.imagecaltracker.data.FoodEntry).
              | Expected:
              |""".trimMargin() + _infoFoodEntries + """
              |
              | Found:
              |""".trimMargin() + _existingFoodEntries)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "food_entries")
  }

  public override fun clearAllTables() {
    super.performClear(false, "food_entries")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(FoodEntryDao::class, FoodEntryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun foodEntryDao(): FoodEntryDao = _foodEntryDao.value
}
