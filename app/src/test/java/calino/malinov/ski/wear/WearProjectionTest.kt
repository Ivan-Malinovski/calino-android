package calino.malinov.ski.wear

import calino.malinov.ski.data.model.*
import calino.malinov.ski.data.repository.*
import calino.malinov.ski.wearcontract.MAX_SNAPSHOT_BYTES
import calino.malinov.ski.wearcontract.WearCodec
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WearProjectionTest {
    private val today=LocalDate.of(2026,5,18)
    @Test fun filtersVisibilityTaskPreferenceAndWindow(){
        val calendars=listOf(CalinoCalendar("yes","Visible",1),CalinoCalendar("hidden","Hidden",2,visible=false),CalinoCalendar("events","Events",3,showTasksInViews=false))
        val snapshot=CalinoSnapshot(
            events=listOf(CalEvent("in","In",1,today.atTime(9,0),60,calendarId="yes"),CalEvent("out","Out",1,today.plusDays(7).atTime(9,0),60,calendarId="yes"),CalEvent("hidden","Secret",1,today.atTime(9,0),60,calendarId="hidden")),
            tasks=listOf(CalTask("ok","Open",1,today,calendarId="yes"),CalTask("old","Old",1,today.minusDays(31),calendarId="yes"),CalTask("suppressed","No",1,today,calendarId="events"),CalTask("undated","Later",1,null,calendarId="yes")),journals=emptyList(),calendars=calendars)
        val result=WearProjection.build(snapshot,today,ZoneId.of("Europe/Copenhagen"),"epoch",1,true)
        assertEquals(listOf("In"),result.events.map{it.title});assertEquals(setOf("Open","Later"),result.tasks.map{it.title}.toSet());assertTrue(WearCodec.encodeSnapshot(result).size<MAX_SNAPSHOT_BYTES)
    }
    @Test fun recurrenceUsesOccurrenceIdentity(){val event=CalEvent("series","Standup",1,today.atTime(9,0),30,recurrence="FREQ=DAILY",calendarId="yes");val result=WearProjection.build(CalinoSnapshot(listOf(event),emptyList(),emptyList(),calendars=listOf(CalinoCalendar("yes","Yes",1))),today,ZoneId.of("UTC"),"e",2,true);assertEquals(7,result.events.size);assertEquals(7,result.events.map{it.occurrenceId}.distinct().size)}
    @Test fun recurringMultiDayEndIsRelativeToOccurrence(){val event=CalEvent("series","Trip",1,null,null,true,"FREQ=DAILY",calendarId="yes",date=today,endDate=today.plusDays(2));val result=WearProjection.build(CalinoSnapshot(listOf(event),emptyList(),emptyList(),calendars=listOf(CalinoCalendar("yes","Yes",1))),today,ZoneId.of("UTC"),"e",2,true);result.events.forEach{assertEquals(2,it.endEpochDay-it.startEpochDay)}}
    @Test fun fixtureOrIdleSourceIsNeverAuthoritative(){assertFalse(isAuthoritativeWearSource(true,true,SyncState.Ready(java.time.Instant.EPOCH)));assertFalse(isAuthoritativeWearSource(true,false,SyncState.Idle));assertTrue(isAuthoritativeWearSource(true,false,SyncState.Ready(java.time.Instant.EPOCH)))}
}
