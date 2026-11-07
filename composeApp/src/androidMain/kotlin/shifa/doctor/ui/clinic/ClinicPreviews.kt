package shifa.doctor.ui.clinic

import androidx.compose.runtime.Composable
import shifa.doctor.data.clinics.ClinicsState
import shifa.doctor.data.model.ClinicOverviewStats
import shifa.doctor.data.model.MyClinic
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.treatmentPlanWizardCouldNotSaveServices
import shifa.doctor.ui.theme.PreviewTheme

internal val previewClinics =
    ClinicsState(
        listOf(
            MyClinic(
                2,
                "Shifa Dev Clinic",
                timeZone = "Asia/Tashkent",
                phone = "+998712000000",
                email = "clinic@shifa.dev",
                address = "Amir Temur 1, Tashkent",
                membershipRole = "OWNER",
                isPracticeClinic = true,
                defaultDoctorRevenueSharePercent = 60,
                canManageFinanceSettings = true,
            ),
            MyClinic(5, "Smile Clinic Samarkand", timeZone = "Asia/Samarkand", membershipRole = "DOCTOR"),
        ),
        selectedId = 2,
        known = true,
    )

@Composable
private fun Workspace(
    state: ClinicUiState,
    section: @Composable androidx.compose.foundation.layout.ColumnScope.(ClinicSection, MyClinic) -> Unit = { _, _ -> },
) = PreviewTheme { ClinicWorkspaceContent(state, false, {}, {}, {}, {}, {}, ClinicActions(newPlan = {}), section) }

// CLINIC-02/04 (board `DaClinic`, flows 09/01, 09/20): the header with two clinics, the Overview, Settings.
@Composable
internal fun ClinicOverviewPreview() = Workspace(ClinicUiState(previewClinics, stats = ClinicOverviewStats(4, 3, 12)))

@Composable
internal fun ClinicSettingsPreview() = Workspace(ClinicUiState(previewClinics, section = ClinicSection.Settings))

@Composable
internal fun ClinicEmptyPreview() = Workspace(ClinicUiState(ClinicsState(known = true)))

private val previewPeople =
    ClinicPeopleUiState(
        members =
            listOf(
                shifa.doctor.data.model.ClinicMember(21, 101, "Aziza Karimova", "OWNER", null, 60),
                shifa.doctor.data.model.ClinicMember(22, 102, "Bobur Aliyev", "DOCTOR", 70, 70),
                shifa.doctor.data.model.ClinicMember(null, 103, "Dilfuza Yusupova", "RECEPTIONIST"),
            ),
        invitations =
            listOf(
                shifa.doctor.data.model.ClinicInvitation(5, "anna@clinic.uz", "2999-01-10T13:00:00Z", pending = true),
                shifa.doctor.data.model.ClinicInvitation(6, "olim@clinic.uz", "2026-10-01T13:00:00Z", consumed = true),
            ),
    )

// CLINIC-05 (flow 09/02): the owner's hint and Default share, chips from the roles, shares (a receptionist has none).
@Composable
internal fun ClinicDoctorsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Doctors)) { _, clinic ->
        DoctorsContent(clinic, previewPeople, PeopleActions()) {}
    }

// CLINIC-08 (flow 09/19): Invite receptionist, a pending invitation with Revoke, a used one.
@Composable
internal fun ClinicInvitationsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Invitations)) { _, clinic ->
        InvitationsContent(clinic, previewPeople, PeopleActions())
    }

// CLINIC-06 (board `DaClinic` Patients): search, the total, patients with phone and email (a dash without a phone), Load more.
@Composable
internal fun ClinicPatientsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Patients)) { _, _ ->
        ClinicPatientsContent(
            ClinicPatientsUiState(
                rows =
                    listOf(
                        shifa.doctor.data.model.ClinicPatient(
                            15,
                            "Gulnora Rashidova",
                            "+998 90 123 45 67",
                            "gulnora@mail.uz",
                        ),
                        shifa.doctor.data.model.ClinicPatient(16, "Jasur Karimov", "+998 93 555 12 34"),
                        shifa.doctor.data.model.ClinicPatient(17, "Malika Tursunova", null, "malika@mail.uz"),
                    ),
                total = 64,
                last = false,
            ),
            ClinicPatientsActions(),
        )
    }

// CLINIC-06: nothing matches the search.
@Composable
internal fun ClinicPatientsEmptyPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Patients)) { _, _ ->
        ClinicPatientsContent(ClinicPatientsUiState(typed = "zzz", rows = emptyList()), ClinicPatientsActions())
    }

private val previewServices =
    ClinicServicesUiState(
        services =
            listOf(
                shifa.doctor.data.model.PlanServiceOption(
                    "catalog:1",
                    "CLINIC_CATALOG",
                    1,
                    title = "Root canal treatment",
                    code = "D3310",
                    defaultPriceMinor = 80_000_000,
                    offeredByDoctorNames = listOf("Aziza Karimova", "Bobur Aliyev"),
                ),
                shifa.doctor.data.model.PlanServiceOption(
                    "catalog:2",
                    "CLINIC_CATALOG",
                    2,
                    title = "Ceramic crown",
                    defaultPriceMinor = 150_000_000,
                    active = false,
                    offeredByDoctorNames = listOf("Aziza Karimova"),
                ),
                shifa.doctor.data.model.PlanServiceOption(
                    "doctor:22:service:3",
                    "DOCTOR_SERVICE",
                    doctorServiceId = 3,
                    title = "Cleaning",
                    defaultPriceMinor = 2_550,
                    currency = "USD",
                    offeredByDoctorNames = listOf("Bobur Aliyev"),
                ),
            ),
        catalog =
            listOf(
                shifa.doctor.data.model.CatalogItem(1, 1, "D3310", "Root canal treatment", 80_000_000),
                shifa.doctor.data.model.CatalogItem(
                    2,
                    1,
                    null,
                    "Ceramic crown",
                    150_000_000,
                    active = false,
                    appliesToAllDoctors = false,
                    assignedDoctorProfileIds = listOf(21),
                ),
            ),
    )

// CLINIC-07 (board `DaClinic` Services, flow 09/04): search and Add, status chips, a clinic row for all doctors, an
// inactive one (grey italic), a doctor's own service (USD, info button).
@Composable
internal fun ClinicServicesPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Services)) { _, _ ->
        ServicesContent(previewServices, ServicesActions())
    }

// CLINIC-07: the editor for a service of chosen doctors, tried with a missing price and a code already used.
@Composable
internal fun ClinicServiceEditorPreview() =
    PreviewTheme {
        ServiceEditorForm(
            ServiceEditor(
                title = "Implant",
                code = "D3310",
                price = "",
                all = false,
                doctorIds = setOf(22L),
                tried = true,
            ),
            previewServices.copy(
                doctors =
                    listOf(
                        shifa.doctor.data.model.ClinicMember(21, 101, "Aziza Karimova", "OWNER"),
                        shifa.doctor.data.model.ClinicMember(22, 102, "Bobur Aliyev", "DOCTOR"),
                    ),
            ),
            ServicesActions(),
        )
    }

// CLINIC-09 (board `DaClinic` Calendar): the clinic's zone, a card per doctor (colour, name, role, Open schedule);
// the receptionist has no calendar.
@Composable
internal fun ClinicCalendarPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Calendar)) { _, clinic ->
        ClinicCalendarContent(clinic, previewPeople, {})
    }

private val previewLedger =
    listOf(
        shifa.doctor.data.model.LedgerRow(
            101, "2026-11-04T05:00:00Z", 11, "Dilnoza Rahimova", 21, "Aziza Karimova", 501,
            listOf(shifa.doctor.data.model.LedgerService("Root canal treatment", 80_000_000)), 80_000_000, 50_000_000,
            planSimplePaymentStatus = "PARTIAL",
        ),
        shifa.doctor.data.model.LedgerRow(
            201, "2026-11-05T05:00:00Z", 13, "Malika Tursunova", 22, "Bobur Aliyev", 501,
            listOf(shifa.doctor.data.model.LedgerService("Braces check", 30_000_000)), 30_000_000, 0,
            planSimplePaymentStatus = "UNPAID",
        ),
    )

@Composable
private fun Finance(state: ClinicFinanceUiState) =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Finance)) { _, clinic ->
        FinanceContent(state, clinic, FinanceActions())
    }

// FIN-02 (board `DaClinic` Finance, Dashboard): the month, Export, four KPIs, Outstanding selected with its visits.
@Composable
internal fun FinanceDashboardPreview() =
    Finance(
        ClinicFinanceUiState(
            month = null,
            kpi = FinanceKpi.Outstanding,
            dashboard = shifa.doctor.data.model.FinanceDashboard(130_000_000, 60_000_000, 1, 0.68),
            balances =
                listOf(
                    Owed(null, "Malika Tursunova", "Braces", 30_000_000, "UZS", planId = 502),
                    Owed(null, "Dilnoza Rahimova", "Molar restoration", 30_000_000, "UZS", planId = 501),
                ),
        ),
    )

// FIN-04: visits by appointment, Showing 2 of 2, partial and unpaid with the pay menu caret.
@Composable
internal fun FinanceLedgerPreview() =
    Finance(
        ClinicFinanceUiState(
            view = FinanceView.Ledger,
            month = shifa.doctor.data.finance.FinanceMonth(2026, 11),
            ledger = previewLedger,
            ledgerTotal = 2,
        ),
    )

private fun installment(
    id: Long,
    seq: Int,
    due: String,
    status: String,
) = shifa.doctor.data.model.InstallmentRow(
    id, 61, 501, "Molar restoration", 11, "Dilnoza Rahimova", seq, 40_000_000, "UZS", due, status,
)

// FIN-05: installments with the totals strip, an overdue one with the reminder bell, a paid one locked.
@Composable
internal fun FinanceInstallmentsPreview() =
    Finance(
        ClinicFinanceUiState(
            view = FinanceView.Installments,
            installments =
                listOf(
                    installment(801, 1, "2026-10-15", "PAID"),
                    installment(802, 2, "2026-10-30", "OVERDUE"),
                    installment(803, 3, "2026-12-15", "PENDING"),
                ),
        ),
    )

// FIN-03: a new record, tried without a patient.
@Composable
internal fun FinanceRecordSheetPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column {
            FinanceContent(
                ClinicFinanceUiState(
                    view = FinanceView.Records,
                    records = emptyList(),
                    record =
                        RecordSheet(
                            amount = "250000",
                            due = kotlinx.datetime.LocalDate(2026, 11, 20),
                            tried = true,
                        ),
                ),
                previewClinics.clinics.first(),
                FinanceActions(),
            )
        }
    }

private val previewPlan =
    shifa.doctor.data.model.TreatmentPlanSummary(
        501, 2, 12, "Jasur Karimov", attendingDoctorName = "Aziza Karimova", title = "Treatment of 36 and 46",
        diagnosis = "K04.0 Pulpitis", statusRaw = "ACTIVE", totalMinor = 160_000_000, paidMinor = 100_000_000,
        owedMinor = 60_000_000, visitCount = 2, linesCompletedCount = 1, linesTotalCount = 3,
    )

private val previewVisits =
    listOf(
        shifa.doctor.data.model.PlanVisit(
            101,
            "2026-11-04T05:00:00Z",
            "2026-11-04T05:30:00Z",
            "COMPLETED",
            21,
            "Aziza Karimova",
            services = listOf("Root canal treatment"),
            timing = "PAST",
        ),
        shifa.doctor.data.model.PlanVisit(
            201,
            "2026-11-12T06:00:00Z",
            "2026-11-12T06:30:00Z",
            "CONFIRMED",
            22,
            "Bobur Aliyev",
            services = listOf("Ceramic crown"),
            timing = "UPCOMING",
        ),
    )

// TP-02 (board `DaClinic` Plans): search, New plan, status chips, a plan with its visits open.
@Composable
internal fun ClinicPlansPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Plans)) { _, clinic ->
        PlansContent(
            ClinicPlansUiState(
                plans =
                    listOf(
                        previewPlan,
                        previewPlan.copy(
                            id = 502,
                            title = "Braces",
                            statusRaw = "ON_HOLD",
                            owedMinor = 0,
                            paidMinor = 160_000_000,
                        ),
                    ),
                open = setOf(501),
                visits = mapOf(501L to previewVisits),
            ),
            clinic,
            PlansActions(newPlan = {}),
        )
    }

// TP-03 (board `DaClinic` plan detail): overview with Record payment, the chart, the lines, visits, installments.
@Composable
internal fun PlanDetailPreview() =
    PreviewTheme {
        PlanDetailContent(
            PlanDetailUiState(
                detail =
                    shifa.doctor.data.model.TreatmentPlanDetail(
                        previewPlan,
                        listOf(
                            shifa.doctor.data.model.PlanLine(
                                1,
                                title = "Root canal treatment",
                                lineTotalMinor = 80_000_000,
                                statusRaw = "COMPLETED",
                                specialtyMetadata = """{"fdi":"36"}""",
                            ),
                            shifa.doctor.data.model.PlanLine(
                                2,
                                title = "Ceramic crown",
                                lineTotalMinor = 60_000_000,
                                statusRaw = "SCHEDULED",
                                specialtyMetadata = """{"fdi":"46"}""",
                            ),
                            shifa.doctor.data.model.PlanLine(
                                3,
                                title = "Hygiene",
                                lineTotalMinor = 20_000_000,
                                statusRaw = "PLANNED",
                            ),
                        ),
                        installmentPlans =
                            listOf(
                                shifa.doctor.data.model.InstallmentPlan(
                                    61,
                                    items =
                                        listOf(
                                            shifa.doctor.data.model.InstallmentItem(
                                                801,
                                                1,
                                                30_000_000,
                                                dueDate = "2026-11-15",
                                                status = "PAID",
                                            ),
                                            shifa.doctor.data.model.InstallmentItem(
                                                802,
                                                2,
                                                30_000_000,
                                                dueDate = "2026-12-15",
                                                status = "PENDING",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                visits = previewVisits,
                loading = false,
            ),
            previewClinics.clinics.first(),
            PlanDetailActions(),
        )
    }

private val wizardServices =
    listOf(
        shifa.doctor.data.model.PlanServiceOption(
            "c1",
            catalogItemId = 1,
            title = "Root canal treatment",
            defaultPriceMinor = 80_000_000,
        ),
        shifa.doctor.data.model.PlanServiceOption(
            "c2",
            catalogItemId = 2,
            title = "Ceramic crown",
            defaultPriceMinor = 150_000_000,
        ),
    )

private val wizardBase =
    PlanWizardUiState(
        firstStep = 2,
        patient = shifa.doctor.data.model.ClinicPatient(12, "Jasur Karimov"),
        title = "Treatment of 36 and 46",
        doctors =
            listOf(
                shifa.doctor.data.model.ClinicMember(21, displayName = "Aziza Karimova"),
                shifa.doctor.data.model.ClinicMember(22, displayName = "Bobur Aliyev"),
            ),
        picked = listOf(21L),
        services = wizardServices,
        lines = listOf(WizardLine(wizardServices[0], "36"), WizardLine(wizardServices[1], "46")),
        initial = "300 000",
        useInstallments = true,
        installments =
            listOf(
                WizardInstallment(kotlinx.datetime.LocalDate(2026, 12, 6), "1 000 000"),
                WizardInstallment(kotlinx.datetime.LocalDate(2027, 1, 5), "900 000"),
            ),
    )

private val wizardToday = kotlinx.datetime.LocalDate(2026, 11, 6)

@Composable
internal fun PlanWizardPatientPreview() =
    PreviewTheme {
        PlanWizardContent(
            PlanWizardUiState(
                query = "kar",
                matches =
                    listOf(
                        shifa.doctor.data.model.ClinicPatient(12, "Jasur Karimov", "+998 90 123 45 67"),
                        shifa.doctor.data.model.ClinicPatient(14, "Bekzod Karimov", email = "bekzod@mail.uz"),
                    ),
            ),
            wizardToday,
            PlanWizardActions(),
        )
    }

@Composable
internal fun PlanWizardTeamPreview() =
    PreviewTheme {
        PlanWizardContent(wizardBase.copy(step = 3, tooth = "19", toothTried = true), wizardToday, PlanWizardActions())
    }

@Composable
internal fun PlanWizardPaymentPreview() =
    PreviewTheme {
        PlanWizardContent(wizardBase.copy(step = 4, tried = setOf(4)), wizardToday, PlanWizardActions())
    }

@Composable
internal fun PlanWizardReviewPreview() =
    PreviewTheme {
        PlanWizardContent(
            wizardBase.copy(
                step = 5,
                installments =
                    wizardBase.installments.take(
                        1,
                    ) + WizardInstallment(kotlinx.datetime.LocalDate(2027, 1, 5), "1 000 000"),
                planId = 601,
                done = setOf(SaveStep.Plan),
                error =
                    shifa.doctor.ui.emailcode.CodeBanner(
                        Res.string.treatmentPlanWizardCouldNotSaveServices,
                        requestId = "req-7f3a",
                    ),
            ),
            wizardToday,
            PlanWizardActions(),
        )
    }
