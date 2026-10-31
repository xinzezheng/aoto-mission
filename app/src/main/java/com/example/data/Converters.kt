package com.example.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromTriggerType(value: TriggerType?): String {
        return value?.name ?: TriggerType.APP_LAUNCH.name
    }

    @TypeConverter
    fun toTriggerType(value: String?): TriggerType {
        return try {
            if (value.isNullOrEmpty()) TriggerType.APP_LAUNCH else TriggerType.valueOf(value)
        } catch (_: Exception) {
            TriggerType.APP_LAUNCH
        }
    }

    @TypeConverter
    fun fromExitTriggerType(value: ExitTriggerType?): String {
        return value?.name ?: ExitTriggerType.NONE.name
    }

    @TypeConverter
    fun toExitTriggerType(value: String?): ExitTriggerType {
        return try {
            if (value.isNullOrEmpty()) ExitTriggerType.NONE else ExitTriggerType.valueOf(value)
        } catch (_: Exception) {
            ExitTriggerType.NONE
        }
    }

    @TypeConverter
    fun fromActionType(value: ActionType?): String {
        return value?.name ?: ActionType.SET_ALARM.name
    }

    @TypeConverter
    fun toActionType(value: String?): ActionType {
        return try {
            if (value.isNullOrEmpty()) ActionType.SET_ALARM else ActionType.valueOf(value)
        } catch (_: Exception) {
            ActionType.SET_ALARM
        }
    }
}
