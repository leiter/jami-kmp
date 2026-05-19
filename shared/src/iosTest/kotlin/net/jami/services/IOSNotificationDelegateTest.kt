package net.jami.services

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.jami.model.Uri
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNTextInputNotificationResponse

@ExperimentalCoroutinesApi
class IOSNotificationDelegateTest : KoinTest {

    private val callService: CallService by inject()
    private val conversationFacade: ConversationFacade by inject()

    private val delegate = IOSNotificationDelegate()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        startKoin {
            modules(module {
                single { mockk<CallService>(relaxed = true) }
                single { mockk<ConversationFacade>(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    private fun mockResponse(
        actionIdentifier: String,
        userInfo: Map<Any?, Any?>,
        isTextInput: Boolean = false,
        userText: String? = null
    ): UNNotificationResponse {
        val content = mockk<UNMutableNotificationContent>()
        every { content.userInfo } returns userInfo

        val request = mockk<UNNotificationRequest>()
        every { request.content } returns content
        every { request.identifier } returns "test_notif_id"

        val notification = mockk<UNNotification>()
        every { notification.request } returns request

        val response = if (isTextInput) {
            mockk<UNTextInputNotificationResponse>()
        } else {
            mockk<UNNotificationResponse>()
        }
        every { response.actionIdentifier } returns actionIdentifier
        every { response.notification } returns notification
        if (isTextInput && response is UNTextInputNotificationResponse) {
            every { response.userText } returns (userText ?: "")
        }

        return response
    }

    @Test
    fun `didReceive with answer call action calls acceptCall`() {
        val userInfo = mapOf(
            IOSNotificationDelegate.KEY_ACCOUNT_ID to "test_account",
            IOSNotificationDelegate.KEY_CALL_ID to "test_call_id"
        )
        val response = mockResponse(IOSNotificationDelegate.ACTION_ANSWER_CALL, userInfo)

        delegate.userNotificationCenter(mockk(), response) {}
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { callService.acceptCall("test_call_id") }
    }

    @Test
    fun `didReceive with decline call action calls hangupCall`() {
        val userInfo = mapOf(
            IOSNotificationDelegate.KEY_ACCOUNT_ID to "test_account",
            IOSNotificationDelegate.KEY_CALL_ID to "test_call_id"
        )
        val response = mockResponse(IOSNotificationDelegate.ACTION_DECLINE_CALL, userInfo)

        delegate.userNotificationCenter(mockk(), response) {}
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { callService.hangupCall("test_call_id") }
    }

    @Test
    fun `didReceive with mark read action calls markConversationAsRead`() {
        val userInfo = mapOf(
            IOSNotificationDelegate.KEY_ACCOUNT_ID to "test_account",
            IOSNotificationDelegate.KEY_CONVERSATION_ID to "test_conv_uri"
        )
        val response = mockResponse(IOSNotificationDelegate.ACTION_MARK_READ, userInfo)

        delegate.userNotificationCenter(mockk(), response) {}
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { conversationFacade.markConversationAsRead("test_account", Uri("test_conv_uri")) }
    }

    @Test
    fun `didReceive with reply action calls sendMessage`() {
        val userInfo = mapOf(
            IOSNotificationDelegate.KEY_ACCOUNT_ID to "test_account",
            IOSNotificationDelegate.KEY_CONVERSATION_ID to "test_conv_uri"
        )
        val response = mockResponse(
            IOSNotificationDelegate.ACTION_REPLY_MESSAGE,
            userInfo,
            isTextInput = true,
            userText = "Hello there"
        )

        delegate.userNotificationCenter(mockk(), response) {}
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { conversationFacade.sendMessage("test_account", "test_conv_uri", "Hello there") }
    }
}
