package org.simple.clinic.instantsearch

import android.os.Parcelable
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.patient.businessid.Identifier

@Parcelize
data class InstantSearchScreenKey(
    val additionalIdentifier: Identifier?
) : ScreenKey(), Parcelable {

  @IgnoredOnParcel
  override val analyticsName: String = "Instant Search Screen"

  override fun instantiateFragment() = InstantSearchScreen()
}
