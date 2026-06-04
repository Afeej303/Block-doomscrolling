package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM app_block_config")
    fun getAllConfigsFlow(): Flow<List<AppBlockConfig>>

    @Query("SELECT * FROM app_block_config")
    suspend fun getAllConfigs(): List<AppBlockConfig>

    @Query("SELECT * FROM app_block_config WHERE packageName = :packageName")
    suspend fun getConfigForApp(packageName: String): AppBlockConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: AppBlockConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfigs(configs: List<AppBlockConfig>)

    @Query("SELECT * FROM security_settings WHERE id = 1")
    fun getSecuritySettingsFlow(): Flow<SecuritySettings?>

    @Query("SELECT * FROM security_settings WHERE id = 1")
    suspend fun getSecuritySettings(): SecuritySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSecurity(settings: SecuritySettings)
}
