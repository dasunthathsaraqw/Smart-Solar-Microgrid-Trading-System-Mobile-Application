package com.example.smartmicrogrid.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * File: MainDispatcherRule.kt
 * Purpose: A JUnit rule that lets ViewModel code that uses viewModelScope run inside a plain JVM
 *          test.
 * Author: Mobile Team
 * Date: 2026
 *
 * WHY IT'S NEEDED. viewModelScope launches its coroutines on Android's "Main" thread. A unit test
 * runs on a plain JVM where there is no Android main thread, so without this the very first
 * viewModelScope.launch would crash. Before each test this rule swaps "Main" for a test stand-in;
 * after each test it puts things back.
 *
 * WHY "UNCONFINED". An unconfined test dispatcher runs launched coroutines immediately, on the
 * spot, instead of queueing them. So a test can write `viewModel.login(...)` and check the result
 * on the very next line with no waiting or timing tricks. (If the code being tested is waiting on
 * something the test controls — see the "gate" in the ViewModel tests — it pauses exactly there.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
