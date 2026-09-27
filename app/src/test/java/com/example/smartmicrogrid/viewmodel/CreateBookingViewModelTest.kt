package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import com.example.smartmicrogrid.testutil.MainDispatcherRule
import com.example.smartmicrogrid.testutil.sampleReservationAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * File: CreateBookingViewModelTest.kt
 * Purpose: Unit tests for CreateBookingViewModel — the last step of booking a charging slot: it
 *          must send the right station and slot, never send twice, and report the server's answer
 *          (a confirmation, or the reason it was refused) faithfully.
 * Author: Mobile Team
 * Date: 2026
 *
 * This ViewModel does no validation of its own — every booking rule (slot free, in the future,
 * within 7 days...) belongs to the server. So what's worth testing is the plumbing: the right
 * values go out, the answer comes back unchanged, and a double-tap can never create two bookings.
 * The repository is a fake (see AuthViewModelTest for how mocking works), so no server is needed.
 */
class CreateBookingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val repo = mockk<ReservationRepository>()
    private lateinit var viewModel: CreateBookingViewModel

    @Before
    fun setUp() {
        viewModel = CreateBookingViewModel(application, repo)
    }

    // Real-world bug caught: a screen that opens already showing a spinner or an old result.
    @Test
    fun `starts Idle`() {
        assertEquals(BookingActionState.Idle, viewModel.state.value)
    }

    // Real-world bug caught: stationId and slotId swapped when handed to the repository (easy
    // to do — they are both plain strings), which books nothing or the wrong thing.
    @Test
    fun `confirmBooking sends the station id and slot id to the repository`() {
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Success(sampleReservationAction())

        viewModel.confirmBooking(stationId = "station-7", slotId = "slot-9")

        coVerify(exactly = 1) { repo.createReservation("station-7", "slot-9") }
    }

    // Real-world bug caught: the confirmation details (message, hours until the slot, whether it
    // can still be changed) being dropped or altered on the way to the summary screen.
    @Test
    fun `a successful booking becomes Success carrying the server response unchanged`() {
        val response = sampleReservationAction(
            message = "Reservation created successfully.",
            hoursUntilSlot = 52.4,
            canStillModify = true
        )
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Success(response)

        viewModel.confirmBooking("station-7", "slot-9")

        assertEquals(BookingActionState.Success(response), viewModel.state.value)
    }

    // Real-world bug caught: the "slot already booked" refusal being shown as a generic failure,
    // or its status code (409) being lost so the screen can't tell it from a network problem.
    @Test
    fun `a refused booking becomes Error with the server message and status code`() {
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Error("Slot is already booked.", 409)

        viewModel.confirmBooking("station-7", "slot-9")

        assertEquals(BookingActionState.Error("Slot is already booked.", 409), viewModel.state.value)
    }

    // Real-world bug caught: a network failure being reported with a made-up status code. With no
    // connection there is no HTTP code at all, and the code must stay null.
    @Test
    fun `a network failure becomes Error with no status code`() {
        coEvery { repo.createReservation(any(), any()) } returns
            ApiResult.Error("Network error. Please check your connection.", code = null, isNetworkError = true)

        viewModel.confirmBooking("station-7", "slot-9")

        assertEquals(
            BookingActionState.Error("Network error. Please check your connection.", null),
            viewModel.state.value
        )
    }

    // Real-world bug caught: THE important one — a double-tap on "Confirm" creating two bookings.
    // The gate freezes the fake server mid-request; the second tap must be ignored.
    @Test
    fun `a second tap while the request is in flight is ignored`() {
        val gate = CompletableDeferred<ApiResult<ReservationActionResponse>>()
        coEvery { repo.createReservation(any(), any()) } coAnswers { gate.await() }

        viewModel.confirmBooking("station-7", "slot-9")
        assertEquals(BookingActionState.Loading, viewModel.state.value)

        viewModel.confirmBooking("station-7", "slot-9")

        coVerify(exactly = 1) { repo.createReservation(any(), any()) }

        gate.complete(ApiResult.Success(sampleReservationAction()))
        assertEquals(BookingActionState.Success(sampleReservationAction()), viewModel.state.value)
    }

    // Real-world bug caught: after a successful booking, a stray extra tap (the screen is about to
    // close) submitting the same booking again. It stays blocked until the Activity has handled
    // the result and reset the state — and only THEN can a new booking be made.
    @Test
    fun `after a success further taps are ignored until the state is reset`() {
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Success(sampleReservationAction())
        viewModel.confirmBooking("station-7", "slot-9")

        viewModel.confirmBooking("station-7", "slot-9") // ignored: success not yet handled
        coVerify(exactly = 1) { repo.createReservation(any(), any()) }

        viewModel.resetState()
        viewModel.confirmBooking("station-7", "slot-9") // allowed again
        coVerify(exactly = 2) { repo.createReservation(any(), any()) }
    }

    // Real-world bug caught: an error permanently disabling the Confirm button. After a refusal
    // (say a network blip) the user must be able to try again without leaving the screen.
    @Test
    fun `after an error the user can try again`() {
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Error("Slot is already booked.", 409)
        viewModel.confirmBooking("station-7", "slot-9")

        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Success(sampleReservationAction())
        viewModel.confirmBooking("station-7", "slot-9")

        coVerify(exactly = 2) { repo.createReservation(any(), any()) }
        assertEquals(BookingActionState.Success(sampleReservationAction()), viewModel.state.value)
    }

    // Real-world bug caught: a handled result that can't be cleared, replaying the same toast or
    // a second navigation to the summary screen after a rotation.
    @Test
    fun `resetState returns to Idle`() {
        coEvery { repo.createReservation(any(), any()) } returns ApiResult.Error("Slot is already booked.", 409)
        viewModel.confirmBooking("station-7", "slot-9")

        viewModel.resetState()

        assertEquals(BookingActionState.Idle, viewModel.state.value)
    }
}
