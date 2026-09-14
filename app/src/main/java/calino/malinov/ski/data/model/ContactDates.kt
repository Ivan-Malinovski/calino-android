package calino.malinov.ski.data.model

import java.time.LocalDate

/** Date-only arithmetic; a vCard birthday has no timezone to convert. */
fun contactAge(birthday: LocalDate, today: LocalDate): Int {
    var age = today.year - birthday.year
    // Match the web's date-only semantics: a Feb 29 occurrence rolls to
    // March 1 in a non-leap year, without ever involving a timezone.
    val monthDayThisYear = LocalDate.of(today.year, birthday.month, 1).plusDays(birthday.dayOfMonth - 1L)
    if (today < monthDayThisYear) age--
    return age
}

fun daysUntilNextContactDate(date: LocalDate, today: LocalDate): Long {
    fun inYear(year: Int): LocalDate = LocalDate.of(year, date.month, 1).plusDays(date.dayOfMonth - 1L)
    val thisYear = inYear(today.year)
    val target = if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
    return target.toEpochDay() - today.toEpochDay()
}
