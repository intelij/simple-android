package org.simple.clinic.appupdate

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.appupdate.AppUpdateState.DontShowAppUpdate
import org.simple.clinic.appupdate.AppUpdateState.ShowAppUpdate
import org.simple.clinic.feature.Feature
import org.simple.clinic.feature.Features
import org.simple.clinic.remoteconfig.DefaultValueConfigReader
import org.simple.clinic.remoteconfig.NoOpRemoteConfigService

@RunWith(JUnitParamsRunner::class)
class CheckAppUpdateAvailabilityTest {

  private val configProvider = PublishSubject.create<AppUpdateConfig>()
  private val currentAppVersionCode = 1
  private val differenceInVersionsToShowUpdate = 1

  lateinit var fakeAppUpdateManager: FakeAppUpdateManager
  lateinit var checkUpdateAvailable: CheckAppUpdateAvailability

  @Test
  @Parameters(method = "params for checking app update")
  fun `when app update is available and it is eligible for updates, then the user should be nudged for an app update`(
      availableVersionCode: Int,
      @UpdateAvailability updateAvailabilityState: Int,
      isInAppUpdateEnabled: Boolean,
      appUpdateState: AppUpdateState
  ) {

    setup(isFeatureEnabled = isInAppUpdateEnabled)

    if (updateAvailabilityState == UpdateAvailability.UPDATE_AVAILABLE) {
      fakeAppUpdateManager.setUpdateAvailable(availableVersionCode)
    } else {
      fakeAppUpdateManager.setUpdateNotAvailable()
    }

    configProvider.onNext(AppUpdateConfig(1))

    val testObserver = checkUpdateAvailable
        .listen()
        .test()

    with(testObserver) {
      assertNoErrors()
      assertSubscribed()
      assertValue(appUpdateState)
    }
  }

  fun `params for checking app update`(): List<List<Any?>> {

    fun testCase(
        versionCode: Int,
        updateAvailabilityState: Int,
        isInAppUpdateEnabled: Boolean,
        appUpdateState: AppUpdateState
    ): List<Any?> {
      return listOf(
          versionCode,
          updateAvailabilityState,
          isInAppUpdateEnabled,
          appUpdateState
      )
    }

    return listOf(
        testCase(
            versionCode = 1,
            updateAvailabilityState = UpdateAvailability.UPDATE_AVAILABLE,
            isInAppUpdateEnabled = true,
            appUpdateState = DontShowAppUpdate
        ),
        testCase(
            versionCode = 2111,
            updateAvailabilityState = UpdateAvailability.UPDATE_NOT_AVAILABLE,
            isInAppUpdateEnabled = true,
            appUpdateState = DontShowAppUpdate
        ),
        testCase(
            versionCode = 2000,
            updateAvailabilityState = UpdateAvailability.UPDATE_AVAILABLE,
            isInAppUpdateEnabled = true,
            appUpdateState = DontShowAppUpdate
        ),
        testCase(
            versionCode = 1000,
            updateAvailabilityState = UpdateAvailability.UPDATE_AVAILABLE,
            isInAppUpdateEnabled = false,
            appUpdateState = DontShowAppUpdate
        ),
        testCase(
            versionCode = 2,
            updateAvailabilityState = UpdateAvailability.UPDATE_AVAILABLE,
            isInAppUpdateEnabled = true,
            appUpdateState = ShowAppUpdate
        ),
        testCase(
            versionCode = 2111,
            updateAvailabilityState = UpdateAvailability.UPDATE_AVAILABLE,
            isInAppUpdateEnabled = true,
            appUpdateState = ShowAppUpdate
        )
    )
  }

  private fun setup(
      isFeatureEnabled: Boolean
  ) {
    val versionCodeCheck = { versionCode: Int, _: Context, _: AppUpdateConfig ->
      versionCode.minus(currentAppVersionCode) >= differenceInVersionsToShowUpdate
    }

    val features = Features(
        remoteConfigService = NoOpRemoteConfigService(DefaultValueConfigReader()),
        overrides = mapOf(Feature.NotifyAppUpdateAvailable to isFeatureEnabled)
    )

    val context = mock<Context>()
    val packageName = "org.simple.clinic"

    whenever(context.packageName) doReturn packageName

    fakeAppUpdateManager = FakeAppUpdateManager(context)
    checkUpdateAvailable = CheckAppUpdateAvailability(
        appContext = context,
        appUpdateManager = fakeAppUpdateManager,
        config = configProvider,
        versionUpdateCheck = versionCodeCheck,
        features = features
    )
  }
}
