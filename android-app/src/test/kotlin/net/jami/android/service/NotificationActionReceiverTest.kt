package net.jami.android.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import net.jami.model.Call
import net.jami.services.AndroidNotificationService
import net.jami.services.CallService
import net.jami.services.ConversationFacade
import net.jami.services.DaemonBridge
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The notification action buttons. Broadcasts are delivered for real (Robolectric), so
 * [NotificationActionReceiver]'s goAsync() has a pending result to finish. A plain Application
 * keeps JamiApplication — and the daemon it starts — out of the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationActionReceiverTest {

    private val daemonBridge = mockk<DaemonBridge>(relaxed = true)
    private val callService = mockk<CallService>(relaxed = true)
    private val conversationFacade = mockk<ConversationFacade>(relaxed = true)

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { daemonBridge }
                single { callService }
                single { conversationFacade }
                // The manifest's CallActionReceiver hears the call actions too.
                single<net.jami.services.NotificationService> { mockk(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    private fun deliver(intent: Intent) {
        val app = RuntimeEnvironment.getApplication()
        app.registerReceiver(NotificationActionReceiver(), IntentFilter(intent.action), Context.RECEIVER_NOT_EXPORTED)
        app.sendBroadcast(intent.setPackage(app.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun aCall(accountId: String): Call = mockk(relaxed = true) {
        every { account } returns accountId
    }

    @Test
    fun answerAcceptsTheCall() {
        every { callService.getCall("call1") } returns aCall("acc")
        deliver(Intent(AndroidNotificationService.ACTION_ANSWER).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "acc")
            putExtra(AndroidNotificationService.KEY_CALL_ID, "call1")
        })
        coVerify(timeout = 2000) { callService.accept("acc", "call1") }
    }

    @Test
    fun declineRefusesTheCall() {
        every { callService.getCall("call1") } returns aCall("acc")
        deliver(Intent(AndroidNotificationService.ACTION_DECLINE).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "acc")
            putExtra(AndroidNotificationService.KEY_CALL_ID, "call1")
        })
        coVerify(timeout = 2000) { callService.refuse("acc", "call1") }
    }

    /**
     * "Mark as read" used to write a "read" conversation preference, which the daemon does not
     * know: nothing was marked and the conversation kept its badge (2026-11-18).
     */
    @Test
    fun markReadMarksTheConversationRead() {
        coEvery { conversationFacade.markConversationRead(any(), any()) } returns true
        deliver(Intent(AndroidNotificationService.ACTION_MARK_READ).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "acc")
            putExtra(AndroidNotificationService.KEY_CONVERSATION_ID, "conv1")
        })
        coVerify(timeout = 2000) { conversationFacade.markConversationRead("acc", "conv1") }
        verify(exactly = 0) { daemonBridge.setConversationPreferences(any(), any(), any()) }
    }

    @Test
    fun acceptingARequestAcceptsItInTheDaemon() {
        deliver(Intent(AndroidNotificationService.ACTION_ACCEPT_REQUEST).apply {
            putExtra(AndroidNotificationService.KEY_ACCOUNT_ID, "acc")
            putExtra(AndroidNotificationService.KEY_CONVERSATION_ID, "conv1")
        })
        coVerify(timeout = 2000) { daemonBridge.acceptConversationRequest("acc", "conv1") }
    }
}
