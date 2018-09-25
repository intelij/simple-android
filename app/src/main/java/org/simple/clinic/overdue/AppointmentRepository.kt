package org.simple.clinic.overdue

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.user.UserSession
import org.threeten.bp.Clock
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import java.util.UUID
import javax.inject.Inject

class AppointmentRepository @Inject constructor(
    private val appointmentDao: Appointment.RoomDao,
    private val overdueDao: OverdueAppointment.RoomDao,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val clock: Clock
) : SynceableRepository<Appointment, AppointmentPayload> {

  fun schedule(patientUuid: UUID, appointmentDate: LocalDate): Completable {
    val newAppointmentStream = facilityRepository
        .currentFacility(userSession)
        .take(1)
        .map { facility ->
          Appointment(
              uuid = UUID.randomUUID(),
              patientUuid = patientUuid,
              facilityUuid = facility.uuid,
              scheduledDate = appointmentDate,
              status = Appointment.Status.SCHEDULED,
              cancelReason = null,
              agreedToVisit = null,
              remindOn = null,
              syncStatus = SyncStatus.PENDING,
              createdAt = Instant.now(),
              updatedAt = Instant.now())
        }
        .flatMapCompletable { save(listOf(it)) }

    return markScheduledAppointmentsAsVisited(patientUuid).andThen(newAppointmentStream)
  }

  private fun markScheduledAppointmentsAsVisited(patientId: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markScheduledAppointmentAsVisited(
          patientId = patientId,
          updatedStatus = Appointment.Status.VISITED,
          scheduledStatus = Appointment.Status.SCHEDULED,
          newSyncStatus = SyncStatus.PENDING)
    }
  }

  fun createReminderForAppointment(appointmentUUID: UUID, reminderDate: LocalDate): Completable {
    return Completable.fromAction {
      appointmentDao.createReminderForAppointment(appointmentUUID, reminderDate)
    }
  }

  fun agreedToVisit(appointmentUUID: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markAgreedToVisit(
          appointmentUUID = appointmentUUID,
          reminderDate = LocalDate.now(clock).plusDays(30),
          agreed = true)
    }
  }

  fun markAppointmentAsVisited(appointmentUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markAppointmentAsVisited(
          appointmentUuid = appointmentUuid,
          newStatus = Appointment.Status.VISITED,
          newSyncStatus = SyncStatus.PENDING)
    }
  }

  fun cancelAppointmentWithReason(appointmentUuid: UUID, reason: Appointment.CancelReason): Completable {
    return Completable.fromAction {
      appointmentDao.cancelAppointmentWithReason(
          appointmentUuid = appointmentUuid,
          cancelReason = reason,
          newStatus = Appointment.Status.CANCELLED,
          newSyncStatus = SyncStatus.PENDING)
    }
  }

  override fun save(records: List<Appointment>): Completable {
    return Completable.fromAction {
      appointmentDao.save(records)
    }
  }

  override fun recordCount(): Single<Int> {
    return appointmentDao.count().firstOrError()
  }

  fun overdueAppointments(): Observable<List<OverdueAppointment>> {
    return facilityRepository.currentFacility(userSession)
        .map { it.uuid }
        .flatMap { facilityUuid ->
          overdueDao.appointmentsForFacility(
              facilityUuid = facilityUuid,
              scheduledStatus = Appointment.Status.SCHEDULED,
              dateNow = LocalDate.now(UTC)
          ).toObservable()
        }
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<Appointment>> {
    return appointmentDao.recordsWithSyncStatus(syncStatus).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return Completable.fromAction { appointmentDao.updateSyncStatus(from, to) }
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError()
    }
    return Completable.fromAction { appointmentDao.updateSyncStatus(ids, to) }
  }

  override fun mergeWithLocalData(payloads: List<AppointmentPayload>): Completable {
    val newOrUpdatedAppointments = payloads
        .filter { payload: AppointmentPayload ->
          val localCopy = appointmentDao.getOne(payload.uuid)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { toDatabaseModel(it, SyncStatus.DONE) }
        .toList()

    return Completable.fromAction { appointmentDao.save(newOrUpdatedAppointments) }
  }

  private fun toDatabaseModel(payload: AppointmentPayload, syncStatus: SyncStatus): Appointment {
    return payload.run {
      Appointment(
          uuid = uuid,
          facilityUuid = facilityUuid,
          patientUuid = patientUuid,
          scheduledDate = date,
          status = status,
          cancelReason = cancelReason,
          remindOn = remindOn,
          agreedToVisit = agreedToVisit,
          syncStatus = syncStatus,
          createdAt = createdAt,
          updatedAt = updatedAt)
    }
  }
}
