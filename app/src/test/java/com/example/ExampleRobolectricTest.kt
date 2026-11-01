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
        assertEquals("土豆雷", appName)
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

    @Test
    fun testAppExitDestroysTaskImmediately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rule = AutomationRule(
            id = 101L,
            title = "抖音5分钟放下手机",
            description = "测试",
            triggerType = TriggerType.APP_LAUNCH,
            triggerPackageName = "com.ss.android.ugc.aweme",
            exitTriggerType = com.example.data.ExitTriggerType.APP_EXIT,
            exitTriggerPackageName = "com.ss.android.ugc.aweme",
            actionType = ActionType.SET_ALARM,
            actionValue = 5
        )

        // 1. 打开应用，启动倒计时
        com.example.engine.ActiveCountdownManager.handleAppOpened(context, rule)
        val activeState = com.example.engine.ActiveCountdownManager.countdownState.value
        assertEquals(true, activeState.isRunning)
        assertEquals(300, activeState.totalSeconds)

        // 2. 模拟用户退出应用，触发退出条件
        com.example.engine.ActiveCountdownManager.handleAppExited(context, "退出应用测试")

        // 3. 验证任务被立即销毁并停止，不再继续计时
        val afterExitState = com.example.engine.ActiveCountdownManager.countdownState.value
        assertEquals(false, afterExitState.isRunning)
        assertEquals(0, afterExitState.remainingSeconds)
    }
}
