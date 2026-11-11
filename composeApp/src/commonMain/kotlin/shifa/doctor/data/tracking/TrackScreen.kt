package shifa.doctor.data.tracking

/**
 * Every screen a screen view may name. A fixed list on purpose: routes and web paths hold names, e-mail addresses and
 * ids (00-plan.md §2.5), so a screen view sends one of these ids and nothing read from a route. Sheets, trays and
 * dialogs are not screens; their events cover them. The debug menu is never tracked.
 */
enum class TrackScreen(
    val id: String,
) {
    // Signed out (App.kt's `Screen`). The e-mail and code screens say their mode as a property, never the address.
    Splash("splash"),
    SignIn("sign_in"),
    EmailEntry("email_entry"),
    EmailCode("email_code"),
    ResetPassword("reset_password"),
    VerifyKey("verify_key"),
    Register("register"),
    Lock("lock"),

    // The shell's tabs (`ShellTab`).
    Home("home"),
    Calendar("calendar"),
    Patients("patients"),
    Chat("chat"),
    Clinic("clinic"),
    Tasks("tasks"),
    Reports("reports"),
    Notifications("notifications"),
    Profile("profile"),

    // Pushed screens (`ShellRoute`) and Shifa Intelligence.
    Intelligence("intelligence"),
    Language("language"),
    Conversation("conversation"),
    PatientChat("patient_chat"),
    PatientSearch("patient_search"),
    Task("task"),
    TaskCreate("task_create"),
    TaskTemplates("task_templates"),
    Schedule("schedule"),
    Patient("patient"),
    Locations("locations"),
    Appointment("appointment"),
    Visit("visit"),
    Block("block"),
    PlanDetail("plan_detail"),
    PlanWizard("plan_wizard"),
    FindPartner("find_partner"),
    Partnerships("partnerships"),
    Partnership("partnership"),
    DoctorSchedule("doctor_schedule"),
    Booking("booking"),
    Form("form"),
    Document("document"),

    // `ShellRoute.Profile(section)`.
    ProfilePersonalInfo("profile_personal_info"),
    ProfileContact("profile_contact"),
    ProfileTimezone("profile_timezone"),
    ProfileExtended("profile_extended"),
    ProfileServices("profile_services"),
    ProfileSubscription("profile_subscription"),
    ProfileSettings("profile_settings"),
    ProfileSecurity("profile_security"),
    ProfileAbout("profile_about"),
    ProfilePrivacy("profile_privacy"),

    // Views inside one route, sent by their feature group: the patient record's tabs (`RecordTab`), …
    PatientOverview("patient_overview"),
    PatientMedical("patient_medical"),
    PatientAppointments("patient_appointments"),
    PatientDocuments("patient_documents"),
    PatientForms("patient_forms"),
    PatientActivity("patient_activity"),

    // … the clinic workspace's sections (`ClinicSection`) …
    ClinicOverview("clinic_overview"),
    ClinicCalendar("clinic_calendar"),
    ClinicDoctors("clinic_doctors"),
    ClinicInvitations("clinic_invitations"),
    ClinicPatients("clinic_patients"),
    ClinicPlans("clinic_plans"),
    ClinicFinance("clinic_finance"),
    ClinicServices("clinic_services"),
    ClinicSettings("clinic_settings"),

    // … and the finance views (`FinanceView`).
    FinanceDashboard("finance_dashboard"),
    FinanceLedger("finance_ledger"),
    FinanceInstallments("finance_installments"),
    FinanceEarnings("finance_earnings"),
    FinanceRecords("finance_records"),
    FinancePayments("finance_payments"),
}
