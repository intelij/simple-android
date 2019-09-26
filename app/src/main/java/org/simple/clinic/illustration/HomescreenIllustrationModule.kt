package org.simple.clinic.illustration

import android.app.Application
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import org.simple.clinic.AppDatabase
import org.simple.clinic.remoteconfig.ConfigReader
import org.threeten.bp.Month
import retrofit2.Retrofit
import java.io.File
import javax.inject.Named

@Module
class HomescreenIllustrationModule {

  @Provides
  fun illustrationDao(appDatabase: AppDatabase) = appDatabase.illustrationDao()

  @Provides
  fun illustrations(
      configReader: ConfigReader,
      moshi: Moshi
  ): List<HomescreenIllustration> {
    return listOf(
        HomescreenIllustration(
            eventId = "valmiki-jayanti.png",
            illustrationUrl = "https://firebasestorage.googleapis.com/v0/b/simple-org.appspot.com/o/image%20(4).png?alt=media&token=1b123ed1-e644-46d4-b4cd-f0ed54a90420",
            from = DayOfMonth(20, Month.SEPTEMBER),
            to = DayOfMonth(30, Month.SEPTEMBER)
        )
    )
  }

  @Provides
  @Named("homescreen-illustration-folder")
  fun illustrationsFolder(context: Application) = File(context.filesDir, "homescreen-illustrations")

  @Provides
  fun fileDownloadService(retrofit: Retrofit): FileDownloadService = retrofit.create(FileDownloadService::class.java)
}
