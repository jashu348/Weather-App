package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey
    val id: String, // String ID: "lat_lon" or geocoding id
    val name: String,
    val country: String? = null,
    val admin1: String? = null, // State or province
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean = false,
    val isGps: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY isGps DESC, isFavorite DESC, timestamp DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteLocationById(id: String)

    @Query("SELECT * FROM saved_locations WHERE isGps = 1 LIMIT 1")
    suspend fun getGpsLocation(): SavedLocation?

    @Query("DELETE FROM saved_locations WHERE isGps = 1")
    suspend fun deleteGpsLocations()
}

@Database(entities = [SavedLocation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weather_alerts_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
