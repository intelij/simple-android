package org.simple.clinic.newentry.country

import org.simple.clinic.newentry.form.InputField

/**
 * This class is only created to hold the property of type List<InputField<*>>
 *   because Dagger does not seem to support injection of types with wildcard generics.
 **/
data class InputFields(
    val fields: List<InputField<*>>
)
