package net.jami.android.service

import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.jami.services.CallService
import net.jami.services.DaemonBridge
import net.jami.services.AndroidNotificationService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

@ExperimentalCoroutinesApi
class NotificationActionReceiverTest : KoinTest {

    private val daemonBridge: DaemonBridge by inject()
    private val callService: CallService by inject()

    private val receiver = NotificationActionReceiver()
    private val context: Context = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(RemoteInput::class)

        startKoin {
            modules(module {
                single { mockk<DaemonBridge>(relaxed = true) }
                single { mockk<CallService>(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `onReceive with answer call action calls acceptCall`() {
        val intent = Intent(AndroidNotificationService.ACTION_ANSWER_CALL).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "test_account")
            putExtra(AndroidNotificationService.KEY_CALL_ID, "test_call_id")
        }

        receiver.onReceive(context, intent)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { callService.acceptCall("test_call_id") }
    }

    @Test
    fun `onReceive with decline call action calls hangupCall`() {
        val intent = Intent(AndroidNotificationService.ACTION_DECLINE_CALL).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "test_account")
            putExtra(AndroidNotificationService.KEY_CALL_ID, "test_call_id")
        }

        receiver.onReceive(context, intent)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { callService.hangupCall("test_call_id") }
    }

    @Test
    fun `onReceive with mark read action calls setConversationPreferences`() {
        val intent = Intent(AndroidNotificationService.ACTION_MARK_READ).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "test_account")
            putExtra(AndroidNotificationService.KEY_CONVERSATION_ID, "test_conv_id")
        }

        receiver.onReceive(context, intent)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { daemonBridge.setConversationPreferences("test_account", "test_conv_id", mapOf("read" to "true")) }
    }
}
