package org.simple.clinic.appupdate

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import org.simple.clinic.BuildConfig
import org.simple.clinic.appupdate.AppUpdateState.AppUpdateStateError
import org.simple.clinic.appupdate.AppUpdateState.DontShowAppUpdate
import org.simple.clinic.appupdate.AppUpdateState.ShowAppUpdate
import org.simple.clinic.feature.Feature.NotifyAppUpdateAvailable
import org.simple.clinic.feature.Features
import javax.inject.Inject

class CheckAppUpdateAvailability @Inject constructor(
    private val appContext: Context,
    private val appUpdateManager: AppUpdateManager,
    private val config: Observable<AppUpdateConfig>,
    private val versionUpdateCheck: (Int, Context, AppUpdateConfig) -> Boolean = isVersionApplicableForUpdate,
    private val features: Features
) {

  fun listen(): Observable<AppUpdateState> {
    return shouldNudgeForUpdate()
        .onErrorReturn(::AppUpdateStateError)
  }

  fun listenAllUpdates(): Observable<AppUpdateState> {
    return appUpdateCallback()
        .map {
          if (it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && it.isUpdateTypeAllowed(FLEXIBLE)) {
            ShowAppUpdate
          } else {
            DontShowAppUpdate
          }
        }
        .onErrorReturn(::AppUpdateStateError)
  }

  private fun appUpdateCallback(): Observable<AppUpdateInfo> {
    val appUpdateInfoTask = appUpdateManager.appUpdateInfo

    return Observable.create { emitter ->
      var cancelled = false

      appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
        emitter.onNext(appUpdateInfo)
      }

      appUpdateInfoTask.addOnFailureListener { exception ->
        if (cancelled.not()) {
          emitter.onError(exception)
        }
      }

      emitter.setCancellable { cancelled = true }
    }
  }

  @VisibleForTesting(otherwise = PRIVATE)
  fun shouldNudgeForUpdate(): Observable<AppUpdateState> {
    val appUpdateInfo = appUpdateCallback()

    val checkUpdate = Observables
        .combineLatest(appUpdateInfo, config)
        .map { (appUpdateInfo, appUpdateConfig) ->
          checkForUpdate(appUpdateInfo, appUpdateConfig)
        }

    val shouldShow = checkUpdate
        .filter { showUpdate -> showUpdate }
        .map { ShowAppUpdate }

    val doNotShow = checkUpdate
        .filter { showUpdate -> showUpdate.not() }
        .map { DontShowAppUpdate }

    return Observable.mergeArray(shouldShow, doNotShow)
  }

  private fun checkForUpdate(appUpdateInfo: AppUpdateInfo, config: AppUpdateConfig): Boolean {
    return features.isEnabled(NotifyAppUpdateAvailable)
        && appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        && appUpdateInfo.isFlexibleUpdateAllowed
        && versionUpdateCheck(appUpdateInfo.availableVersionCode(), appContext, config)
  }
}

private val isVersionApplicableForUpdate = { availableVersionCode: Int, appContext: Context, config: AppUpdateConfig ->
  val packageInfo = appContext.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    val longVersionCode = packageInfo.longVersionCode
    val versionCode = longVersionCode.and(0xffffffff)
    availableVersionCode.minus(versionCode) >= config.differenceBetweenVersionsToNudge
  } else {
    availableVersionCode.minus(packageInfo.versionCode) >= config.differenceBetweenVersionsToNudge
  }
}
