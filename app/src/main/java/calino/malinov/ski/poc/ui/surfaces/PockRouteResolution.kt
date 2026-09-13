package calino.malinov.ski.poc.ui.surfaces

import calino.malinov.ski.poc.state.PocReturnTarget

/** Resolves the root surface that sits behind an event detail overlay. */
internal fun detailOriginRootRoute(
    origin: PocReturnTarget,
    searchOriginRoute: PockRoute,
): PockRoute = when (origin) {
    PocReturnTarget.Agenda -> PockRoute.Agenda
    PocReturnTarget.Range -> PockRoute.Range
    PocReturnTarget.Tasks -> PockRoute.Tasks
    PocReturnTarget.Journal -> PockRoute.Journal
    PocReturnTarget.Contacts -> PockRoute.Contacts
    PocReturnTarget.Search -> searchOriginRoute
    else -> PockRoute.Day
}
