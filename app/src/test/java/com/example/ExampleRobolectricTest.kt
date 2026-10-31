package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActionType
import com.example.data.AppDatabase
import com.example.data.AutomationRule
import com.example.data.TriggerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AutoFlow", appName)
    }

    @Test
    fun insertAndQueryAutomationRule() = runBlocking {
        val dao = database.automationDao()
        val rule = AutomationRule(
            title = "App Launch Rule Test",
            description = "Test description",
            triggerType = TriggerType.APP_LAUNCH,
            actionType = ActionType.SET_ALARM
        )
        val id = dao.insertRule(rule)
        val queried = dao.getRuleById(id)
        assertNotNull(queried)
        assertEquals("App Launch Rule Test", queried?.title)
    }
}
