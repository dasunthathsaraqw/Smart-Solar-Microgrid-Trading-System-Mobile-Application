package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.smartmicrogrid.data.remote.dto.LoginResponse
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.AuthRepository
import com.example.smartmicrogrid.testutil.MainDispatcherRule
import com.example.smartmicrogrid.testutil.sampleLoginResponse
import com.example.smartmicrogrid.testutil.sampleProsumer
import com.example.smartmicrogrid.testutil.validRegisterRequest
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * File: AuthViewModelTest.kt
 * Purpose: Unit tests for AuthViewModel — the logic behind the Login and Register screens: what it
 *          rejects before bothering the server, what it does with the server's answer, and what
 *          state the screen is told to show.
 * Author: Mobile Team
 * Date: 2026
 *
 * WHAT "MOCKING" IS DOING HERE. AuthViewModel needs three helpers: a repository (talks to the real
 * server), a SessionManager (reads and writes the phone's storage) and a cache wiper (empties a
 * real database). None of those exist in a plain test — there is no server, no phone, no database.
 * So the test hands the ViewModel FAKE versions instead ("mocks", from the MockK library). A fake
 * does two jobs:
 *   1. It answers when asked, with whatever the test decides: `coEvery { repo.login(...) } returns X`
 *      means "when the ViewModel calls login, pretend the server replied X".
 *   2. It remembers how it was used, so the test can ask afterwards: `verify { ... }` means "was
 *      this called, with exactly these values?" (and `exactly = 0` means "was it NEVER called?").
 * That is why none of these tests needs a backend running — and why they finish in milliseconds
 * and give the same result every time. We are testing AuthViewModel's own decisions, in isolation.
 *
 * TWO RULES make LiveData and viewModelScope work without an Android phone:
 *   - InstantTaskExecutorRule: LiveData updates happen immediately, on the spot.
 *   - MainDispatcherRule:      viewModelScope coroutines run immediately (see that file).
 */
class AuthViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // A "relaxed" mock quietly accepts any call. The repository is deliberately NOT relaxed: if
    // the ViewModel calls it without the test having said what to answer, the test fails loudly.
    private val application = mockk<Application>(relaxed = true)
    private val repo = mockk<AuthRepository>()
    private val session = mockk<SessionManager>(relaxed = true)

    private var cacheWipes = 0
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        // The last argument is the "clear the offline cache" step: instead of touching a database,
        // the test just counts how many times it was asked to run.
        viewModel = AuthViewModel(application, repo, session) { cacheWipes++ }
    }

    // ==================== initial state ====================

    // Real-world bug caught: a screen that starts in an error or loading state, so the login form
    // opens disabled or already showing a message.
    @Test
    fun `both screens start Idle`() {
        assertEquals(AuthState.Idle, viewModel.loginState.value)
        assertEquals(AuthState.Idle, viewModel.registerState.value)
    }

    // ==================== login: validation (the repository must NOT be called) ====================

    // Real-world bug caught: sending an empty email to the server (wasted request, confusing
    // server error) instead of telling the user right away.
    @Test
    fun `login with an empty email shows an error and never calls the repository`() {
        viewModel.login("", "secret1")

        assertEquals(AuthState.Error("Email is required."), viewModel.loginState.value)
        coVerify(exactly = 0) { repo.login(any(), any()) }
    }

    // Real-world bug caught: forgetting to trim, so "   " (only spaces) counts as a real email
    // and gets past the "required" check.
    @Test
    fun `login with an email of only spaces counts as empty`() {
        viewModel.login("   ", "secret1")

        assertEquals(AuthState.Error("Email is required."), viewModel.loginState.value)
        coVerify(exactly = 0) { repo.login(any(), any()) }
    }

    // Real-world bug caught: the format check being skipped or wrongly disabled, so obvious typos
    // ("kamal.example.com") reach the server.
    @Test
    fun `login with a badly formed email shows the format error`() {
        viewModel.login("not-an-email", "secret1")

        assertEquals(AuthState.Error("Enter a valid email address."), viewModel.loginState.value)
        coVerify(exactly = 0) { repo.login(any(), any()) }
    }

    // Real-world bug caught: an empty password slipping through.
    @Test
    fun `login with an empty password shows an error and never calls the repository`() {
        viewModel.login("kamal@example.com", "")

        assertEquals(AuthState.Error("Password is required."), viewModel.loginState.value)
        coVerify(exactly = 0) { repo.login(any(), any()) }
    }

    // ==================== login: what gets sent ====================

    // Real-world bug caught: the ViewModel calling the repository with the wrong values (swapped,
    // or the untrimmed email — which the server would treat as a different, unknown user).
    @Test
    fun `login calls the repository once with the trimmed email and the password untouched`() {
        coEvery { repo.login(any(), any()) } returns
            ApiResult.Success(sampleLoginResponse(role = Constants.ROLE_OPERATOR, stationId = "station-1"))

        viewModel.login("  kamal@example.com  ", " secret1 ")

        // The email is trimmed. The password is NOT: spaces can be a real part of a password.
        coVerify(exactly = 1) { repo.login("kamal@example.com", " secret1 ") }
    }

    // ==================== login: what the server answers ====================

    // Real-world bug caught: a prosumer logging in but ending up with no NIC on the home screen,
    // or the wrong role/name being reported so the Activity routes them to the wrong home.
    @Test
    fun `a prosumer login succeeds, fetches the profile, and stores the session with the NIC`() {
        val login = sampleLoginResponse(role = Constants.ROLE_PROSUMER)
        coEvery { repo.login(any(), any()) } returns ApiResult.Success(login)
        coEvery { repo.getMyProfile() } returns ApiResult.Success(sampleProsumer(nic = "200012345678"))

        viewModel.login("kamal@example.com", "secret1")

        assertEquals(
            AuthState.Success(role = Constants.ROLE_PROSUMER, name = "Kamal Perera"),
            viewModel.loginState.value
        )
        // The session is saved twice: first without the NIC (so the next request has a token),
        // then again once the profile has supplied the NIC.
        verify(exactly = 1) { saveSessionCall(login, nic = null) }
        verify(exactly = 1) { saveSessionCall(login, nic = "200012345678") }
    }

    // Real-world bug caught: an operator's login making a pointless profile request (operators
    // have no profile endpoint, so it would fail), or losing the station id that scopes them.
    @Test
    fun `an operator login stores the station id and never fetches a profile`() {
        val login = sampleLoginResponse(role = Constants.ROLE_OPERATOR, stationId = "station-42")
        coEvery { repo.login(any(), any()) } returns ApiResult.Success(login)

        viewModel.login("operator@smartsolar.com", "secret1")

        assertEquals(
            AuthState.Success(role = Constants.ROLE_OPERATOR, name = "Kamal Perera"),
            viewModel.loginState.value
        )
        coVerify(exactly = 0) { repo.getMyProfile() }
        verify(exactly = 1) { saveSessionCall(login, nic = null) }
    }

    // Real-world bug caught: a flaky profile request (the NIC is display-only) failing the whole
    // login. The user has valid credentials; they must get in.
    @Test
    fun `a failed profile fetch does not fail the login`() {
        val login = sampleLoginResponse(role = Constants.ROLE_PROSUMER)
        coEvery { repo.login(any(), any()) } returns ApiResult.Success(login)
        coEvery { repo.getMyProfile() } returns
            ApiResult.Error("Network error.", code = null, isNetworkError = true)

        viewModel.login("kamal@example.com", "secret1")

        assertEquals(
            AuthState.Success(role = Constants.ROLE_PROSUMER, name = "Kamal Perera"),
            viewModel.loginState.value
        )
        verify(exactly = 1) { session.saveSession(any(), any(), any(), any(), any(), any(), any()) }
    }

    // Real-world bug caught: the offline cache surviving into a new login, so the new user could
    // briefly be shown the previous user's bookings.
    @Test
    fun `a successful login wipes the offline cache exactly once`() {
        coEvery { repo.login(any(), any()) } returns
            ApiResult.Success(sampleLoginResponse(role = Constants.ROLE_OPERATOR))

        viewModel.login("operator@smartsolar.com", "secret1")

        assertEquals(1, cacheWipes)
    }

    // Real-world bug caught: the server's own message ("Invalid email or password.") being
    // replaced by something generic, or a failed login still saving a session.
    @Test
    fun `a rejected login shows the server message and stores nothing`() {
        coEvery { repo.login(any(), any()) } returns ApiResult.Error("Invalid email or password.", 401)

        viewModel.login("kamal@example.com", "wrong-password")

        assertEquals(AuthState.Error("Invalid email or password."), viewModel.loginState.value)
        verify(exactly = 0) { session.saveSession(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals("a failed login must not wipe the cache", 0, cacheWipes)
    }

    // Real-world bug caught: a double-tap on Login firing two requests (the second could even
    // overwrite the first's session). While the first is in flight the second must be ignored.
    // The "gate" is how a test freezes the fake server mid-request so the in-between state can
    // be inspected, then lets it answer.
    @Test
    fun `login shows Loading while the request is in flight and ignores a second tap`() {
        val gate = CompletableDeferred<ApiResult<LoginResponse>>()
        coEvery { repo.login(any(), any()) } coAnswers { gate.await() }

        viewModel.login("operator@smartsolar.com", "secret1")
        assertEquals(AuthState.Loading, viewModel.loginState.value)

        viewModel.login("operator@smartsolar.com", "secret1") // impatient second tap
        coVerify(exactly = 1) { repo.login(any(), any()) }

        gate.complete(ApiResult.Success(sampleLoginResponse(role = Constants.ROLE_OPERATOR)))
        assertEquals(
            AuthState.Success(role = Constants.ROLE_OPERATOR, name = "Kamal Perera"),
            viewModel.loginState.value
        )
    }

    // Real-world bug caught: after a handled error/success the state can't be cleared, so a
    // screen rotation replays the same toast or navigation.
    @Test
    fun `resetLoginState returns the login screen to Idle`() {
        coEvery { repo.login(any(), any()) } returns ApiResult.Error("Invalid email or password.", 401)
        viewModel.login("kamal@example.com", "wrong-password")

        viewModel.resetLoginState()

        assertEquals(AuthState.Idle, viewModel.loginState.value)
    }

    // ==================== register: validation (the repository must NOT be called) ====================

    // Real-world bug caught: any of the required fields going unchecked, so an incomplete
    // registration is sent to the server.
    @Test
    fun `register rejects each empty required field with its own message`() {
        assertRegisterRejected(validRegisterRequest(nic = ""), "NIC is required.")
        assertRegisterRejected(validRegisterRequest(name = ""), "Full name is required.")
        assertRegisterRejected(validRegisterRequest(email = ""), "Email is required.")
        assertRegisterRejected(validRegisterRequest(contactNumber = ""), "Contact number is required.")
        assertRegisterRejected(validRegisterRequest(address = ""), "Address is required.")
    }

    // Real-world bug caught: forgetting to trim, so a field containing only spaces counts as filled.
    @Test
    fun `register treats fields of only spaces as empty`() {
        assertRegisterRejected(validRegisterRequest(name = "   "), "Full name is required.")
        assertRegisterRejected(validRegisterRequest(address = "  \t "), "Address is required.")
    }

    // Real-world bug caught: the email format check missing on the register form (it's separate
    // code from the login form's check).
    @Test
    fun `register rejects a badly formed email`() {
        assertRegisterRejected(validRegisterRequest(email = "not-an-email"), "Enter a valid email address.")
    }

    // Real-world bug caught: a "greater than zero" rule written as "not negative", letting a 0 kW
    // solar installation through. Zero AND negative must both be refused.
    @Test
    fun `register rejects a panel capacity of zero or less`() {
        val message = "Panel capacity must be greater than 0."

        assertRegisterRejected(validRegisterRequest(panelCapacityKw = 0.0), message)
        assertRegisterRejected(validRegisterRequest(panelCapacityKw = -1.5), message)
    }

    // Real-world bug caught: an off-by-one in the length rule ("<= 6" instead of "< 6"), which
    // would refuse a perfectly valid 6-character password — or accept a 5-character one.
    // Boundary values are where those mistakes live, so we test both sides of the line.
    @Test
    fun `register enforces the minimum password length at the boundary`() {
        val message = "Password must be at least ${Constants.MIN_PASSWORD_LENGTH} characters."
        val justTooShort = "a".repeat(Constants.MIN_PASSWORD_LENGTH - 1)
        val justLongEnough = "a".repeat(Constants.MIN_PASSWORD_LENGTH)

        assertRegisterRejected(validRegisterRequest(password = justTooShort), message)

        coEvery { repo.registerProsumer(any()) } returns ApiResult.Success(sampleProsumer())
        viewModel.registerProsumer(validRegisterRequest(password = justLongEnough))
        coVerify(exactly = 1) { repo.registerProsumer(any()) }
    }

    // Real-world bug caught: when several things are wrong, the user seeing a random one instead
    // of a stable, top-to-bottom order matching the form.
    @Test
    fun `register reports the first problem when there are several`() {
        assertRegisterRejected(validRegisterRequest(nic = "", name = "", password = "x"), "NIC is required.")
    }

    // ==================== register: what gets sent and what comes back ====================

    // Real-world bug caught: untrimmed values reaching the server (a NIC with a trailing space is
    // a different NIC), or the password being altered.
    @Test
    fun `register sends trimmed values and leaves the password untouched`() {
        coEvery { repo.registerProsumer(any()) } returns ApiResult.Success(sampleProsumer())
        val padded = validRegisterRequest(
            nic = "  200012345678 ",
            name = " Kamal Perera  ",
            email = " kamal@example.com ",
            contactNumber = " 0771234567 ",
            address = "  123, Galle Road, Colombo ",
            password = " secret1 "
        )

        viewModel.registerProsumer(padded)

        coVerify(exactly = 1) { repo.registerProsumer(validRegisterRequest(password = " secret1 ")) }
    }

    // Real-world bug caught: registration saving a session. A new account is "pending" until
    // Backoffice approves it, so it must NOT log the user in.
    @Test
    fun `a successful registration reports success and stores no session`() {
        coEvery { repo.registerProsumer(any()) } returns ApiResult.Success(sampleProsumer(name = "Nimal Silva"))

        viewModel.registerProsumer(validRegisterRequest())

        assertEquals(
            AuthState.Success(role = Constants.ROLE_PROSUMER, name = "Nimal Silva"),
            viewModel.registerState.value
        )
        verify(exactly = 0) { session.saveSession(any(), any(), any(), any(), any(), any(), any()) }
    }

    // Real-world bug caught: the server's reason ("Email already registered.") being lost.
    @Test
    fun `a rejected registration passes the server message through`() {
        coEvery { repo.registerProsumer(any()) } returns ApiResult.Error("Email already registered.", 409)

        viewModel.registerProsumer(validRegisterRequest())

        assertEquals(AuthState.Error("Email already registered."), viewModel.registerState.value)
    }

    // Real-world bug caught: the two forms sharing state, so a failed registration flashes an
    // error on the login screen (or vice versa).
    @Test
    fun `register and login keep separate state`() {
        viewModel.registerProsumer(validRegisterRequest(nic = ""))

        assertEquals(AuthState.Error("NIC is required."), viewModel.registerState.value)
        assertEquals(AuthState.Idle, viewModel.loginState.value)
    }

    @Test
    fun `resetRegisterState returns the register screen to Idle`() {
        viewModel.registerProsumer(validRegisterRequest(nic = ""))

        viewModel.resetRegisterState()

        assertEquals(AuthState.Idle, viewModel.registerState.value)
    }

    // ==================== helpers ====================

    /** Runs the register form with [request] and checks it was refused with [expectedMessage], server untouched. */
    private fun assertRegisterRejected(request: RegisterProsumerRequest, expectedMessage: String) {
        viewModel.registerProsumer(request)

        assertEquals(AuthState.Error(expectedMessage), viewModel.registerState.value)
        coVerify(exactly = 0) { repo.registerProsumer(any()) }
    }

    /** The exact saveSession call the ViewModel is expected to make for [login], with the given NIC. */
    private fun saveSessionCall(login: LoginResponse, nic: String?) =
        session.saveSession(
            jwt = login.token,
            userType = login.role,
            email = login.email,
            name = login.name,
            nic = nic,
            stationId = login.stationId,
            expiresAt = login.expiresAt
        )
}
