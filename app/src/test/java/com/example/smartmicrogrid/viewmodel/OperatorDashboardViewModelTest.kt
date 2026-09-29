package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.smartmicrogrid.data.remote.dto.IdentityResponse
import com.example.smartmicrogrid.data.remote.dto.OperatorDashboardResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.AuthRepository
import com.example.smartmicrogrid.data.repository.DashboardRepository
import com.example.smartmicrogrid.testutil.MainDispatcherRule
import com.example.smartmicrogrid.utils.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OperatorDashboardViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val authRepo = mockk<AuthRepository>()
    private val dashboardRepo = mockk<DashboardRepository>()
    private val session = mockk<SessionManager>(relaxed = true)

    private val dashboard = OperatorDashboardResponse(1, 2, 3, 4, emptyList())

    @Test
    fun `refreshes assigned station before loading dashboard`() {
        coEvery { authRepo.getMe() } returns ApiResult.Success(identity())
        coEvery { dashboardRepo.getOperatorDashboard() } returns ApiResult.Success(dashboard)

        val viewModel = OperatorDashboardViewModel(application, authRepo, dashboardRepo, session)
        viewModel.loadDashboard()

        coVerifyOrder { authRepo.getMe(); dashboardRepo.getOperatorDashboard() }
        verify { session.updateOperatorIdentity("Operator", "operator@example.com", "station-2") }
        assertEquals(OperatorDashboardState.Success(dashboard), viewModel.state.value)
    }

    @Test
    fun `changed role never loads operator dashboard`() {
        coEvery { authRepo.getMe() } returns ApiResult.Success(identity(role = "Prosumer"))

        val viewModel = OperatorDashboardViewModel(application, authRepo, dashboardRepo, session)
        viewModel.loadDashboard()

        assertEquals(OperatorDashboardState.AccessChanged, viewModel.state.value)
        coVerify(exactly = 0) { dashboardRepo.getOperatorDashboard() }
        verify(exactly = 0) { session.updateOperatorIdentity(any(), any(), any()) }
    }

    @Test
    fun `expired identity request keeps HTTP 401 for activity logout handling`() {
        coEvery { authRepo.getMe() } returns ApiResult.Error("Expired", 401, false)

        val viewModel = OperatorDashboardViewModel(application, authRepo, dashboardRepo, session)
        viewModel.loadDashboard()

        assertEquals(OperatorDashboardState.Error("Expired", 401), viewModel.state.value)
        coVerify(exactly = 0) { dashboardRepo.getOperatorDashboard() }
    }

    private fun identity(role: String = "GridOperator") = IdentityResponse(
        id = "operator-1",
        name = "Operator",
        email = "operator@example.com",
        role = role,
        stationId = "station-2"
    )
}
