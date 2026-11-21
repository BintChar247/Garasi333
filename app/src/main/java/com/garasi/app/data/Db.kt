package com.garasi.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun statusToString(v: ItemStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): ItemStatus = ItemStatus.valueOf(v)
    @TypeConverter fun categoryToString(v: Category): String = v.name
    @TypeConverter fun stringToCategory(v: String): Category = runCatching { Category.valueOf(v) }.getOrDefault(Category.OTHER)
    @TypeConverter fun conditionToString(v: Condition): String = v.name
    @TypeConverter fun stringToCondition(v: String): Condition = Condition.valueOf(v)
    @TypeConverter fun langToString(v: ListingLang): String = v.name
    @TypeConverter fun stringToLang(v: String): ListingLang = ListingLang.valueOf(v)
    @TypeConverter fun platformToString(v: Platform?): String? = v?.name
    @TypeConverter fun stringToPlatform(v: String?): Platform? = v?.let { Platform.valueOf(it) }
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeItems(): Flow<List<Item>>

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeItem(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): Item?

    @Insert suspend fun insert(item: Item): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(item: Item): Long
    @Update suspend fun update(item: Item)
    @Delete suspend fun delete(item: Item)

    @Query("SELECT * FROM photos ORDER BY itemId, position")
    fun observeAllPhotos(): Flow<List<Photo>>

    @Query("SELECT * FROM photos")
    suspend fun allPhotos(): List<Photo>

    @Query("SELECT * FROM photos WHERE itemId = :itemId ORDER BY position")
    fun observePhotos(itemId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE itemId = :itemId ORDER BY position")
    suspend fun photos(itemId: Long): List<Photo>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photo(id: Long): Photo?

    @Insert suspend fun insertPhoto(photo: Photo): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPhoto(photo: Photo): Long
    @Update suspend fun updatePhotoRaw(photo: Photo)
    @Update suspend fun updatePhotosRaw(photos: List<Photo>)

    /** Updates and stamps updatedAt so the change syncs. */
    suspend fun updatePhoto(photo: Photo) = updatePhotoRaw(photo.copy(updatedAt = System.currentTimeMillis()))
    suspend fun updatePhotos(photos: List<Photo>) {
        val now = System.currentTimeMillis()
        updatePhotosRaw(photos.map { it.copy(updatedAt = now) })
    }
    @Delete suspend fun deletePhoto(photo: Photo)

    @Query("SELECT * FROM listings WHERE itemId = :itemId")
    fun observeListings(itemId: Long): Flow<List<PlatformListing>>

    @Query("SELECT * FROM listings")
    fun observeAllListings(): Flow<List<PlatformListing>>

    @Query("SELECT * FROM listings")
    suspend fun allListings(): List<PlatformListing>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListingRaw(listing: PlatformListing)

    suspend fun upsertListing(listing: PlatformListing) = upsertListingRaw(listing.copy(updatedAt = System.currentTimeMillis()))

    @Delete suspend fun deleteListing(listing: PlatformListing)

    @Transaction
    suspend fun replaceAll(items: List<Item>, photos: List<Photo>, listings: List<PlatformListing>) {
        clearItems()
        items.forEach { upsertItem(it) }
        photos.forEach { upsertPhoto(it) }
        listings.forEach { upsertListingRaw(it) }
    }

    @Query("DELETE FROM items") suspend fun clearItems()

    // ---- sync
    @Query("SELECT * FROM tombstones") suspend fun allTombstones(): List<Tombstone>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTombstone(t: Tombstone)
    @Query("SELECT * FROM items WHERE uid = :uid") suspend fun itemByUid(uid: String): Item?
    @Query("SELECT * FROM photos WHERE uid = :uid") suspend fun photoByUid(uid: String): Photo?
    @Query("DELETE FROM items WHERE uid = :uid") suspend fun deleteItemByUid(uid: String)
    @Query("DELETE FROM photos WHERE uid = :uid") suspend fun deletePhotoByUid(uid: String)
    @Query("DELETE FROM listings WHERE itemId = :itemId AND platform = :platform") suspend fun deleteListing(itemId: Long, platform: Platform)
}

/** v1 → v2: stable ids, timestamps and tombstones for PC sync. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE items SET uid = lower(hex(randomblob(16)))")
        db.execSQL("ALTER TABLE photos ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE photos ADD COLUMN itemUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE photos ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE photos SET uid = lower(hex(randomblob(16))), " +
                "itemUid = (SELECT uid FROM items WHERE items.id = photos.itemId), " +
                "updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000"
        )
        db.execSQL("ALTER TABLE listings ADD COLUMN itemUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE listings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE listings SET itemUid = (SELECT uid FROM items WHERE items.id = listings.itemId), " +
                "updatedAt = postedAt"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `tombstones` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
    }
}

@Database(entities = [Item::class, Photo::class, PlatformListing::class, Tombstone::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GarasiDb : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        @Volatile private var instance: GarasiDb? = null
        fun get(context: Context): GarasiDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GarasiDb::class.java, "garasi.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
