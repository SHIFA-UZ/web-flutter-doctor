import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/localization/uzbek_latin_to_cyrillic.dart';

class AppLocalizations {
  final Locale locale;

  AppLocalizations(this.locale);

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  static final Map<String, Map<String, String>> _localizedValues = {
    'en': {
      // Common
      'appName': 'Shifa Doctor',
      'loading': 'Loading...',
      'error': 'Error',
      'retry': 'Retry',
      'unauthorized': 'Unauthorized. Please login again.',
      'networkError': 'Network error. Please check your connection.',
      'requestTimeout': 'Request timed out. Please try again.',
      'accessDenied': 'Access denied',
      'notFound': 'Resource not found',
      'serverError': 'Server error. Please try again later.',
      'somethingWentWrong': 'Something went wrong',
      'cancel': 'Cancel',
      'save': 'Save',
      'delete': 'Delete',
      'edit': 'Edit',
      'back': 'Back',
      'next': 'Next',
      'complete': 'Complete',
      'submit': 'Submit',
      'close': 'Close',
      'yes': 'Yes',
      'no': 'No',
      'ok': 'OK',
      'confirm': 'Confirm',
      'discard': 'Discard',
      'search': 'Search',
      'filter': 'Filter',
      'apply': 'Apply',
      'dismiss': 'Dismiss',
      'saveDraftNote': 'Save as Draft Note',
      'newSession': 'New Session',
      'draftActions': 'Draft actions',
      'draftSavedAsConsultationNote': 'Draft saved as consultation note',
      'failedToSaveDraft': 'Failed to save draft',
      'setPracticeTimezoneHint':
          'Set your practice timezone in Profile (e.g. Europe/Berlin) so appointment times are correct.',
      'refresh': 'Refresh',
      'noData': 'No data available',
      'required': 'Required',
      'doctor': 'Doctor',
      'patient': 'Patient',
      'admin': 'Admin',
      'paymentsOpsTitle': 'Payments Ops',
      'failedToLoadFailedWebhooks': 'Failed to load failed webhooks: {{error}}',
      'noFailedOrUnprocessedStripeWebhooks':
          'No failed or unprocessed Stripe webhook events.',
      'selectedCount': '{{count}} selected',
      'retrySelected': 'Retry selected',
      'retrying': 'Retrying...',
      'statusFailed': 'FAILED',
      'statusUnprocessed': 'UNPROCESSED',
      'eventIdLabel': 'eventId: {{eventId}}',
      'createdLabel': 'created: {{created}}',
      'retryMetaLine':
          'retryCount: {{retryCount}} · lastRetryAt: {{lastRetryAt}} · retriedByAdminUserId: {{retriedByAdminUserId}}',
      'notAvailableShort': 'N/A',
      'retryWebhookEventTitle': 'Retry webhook event?',
      'retryWebhookEventBody':
          'This will reprocess the stored Stripe webhook payload.\n\n'
              'eventType: {{eventType}}\n'
              'eventId: {{eventId}}',
      'retrySelectedWebhookEventsTitle': 'Retry selected webhook events?',
      'retrySelectedWebhookEventsBody':
          'You are about to retry {{count}} webhook event(s). Each selected event will be replayed from stored payload.',
      'webhookRetriedSuccessfully': 'Webhook retried successfully.',
      'retryStillFailing': 'Retry attempted but still failing.',
      'bulkRetryComplete':
          'Bulk retry complete: {{successCount}} succeeded, {{failCount}} failed.',
      'paymentLabel': 'PAYMENT: {{status}}',
      'paymentUnknown': 'Unknown',
      'paymentStateRaw': '{{state}}',
      'paymentPaid': 'Paid',
      'paymentPending': 'Pending',
      'paymentFailed': 'Failed',
      'paymentNotRequired': 'Not required',
      'appointmentPlaceLockedHint':
          'Booked appointment location is informational and cannot be changed here.',
      'encouragePayment': 'Remind patient to pay',
      'paymentReminderSent': 'Reminder sent to the patient',
      'paymentReminderFailed': 'Could not send reminder: {{error}}',

      // Navigation
      'chat': 'Chat',
      'home': 'Home',
      'calendar': 'Calendar',
      'patients': 'Patients',
      'tasks': 'Tasks',
      'profile': 'Profile',
      'signOut': 'Sign Out',
      'signOutConfirm': 'Are you sure you want to sign out?',

      // Auth
      'login': 'Login',
      'signIn': 'Sign In',
      'phoneOrEmail': 'Phone Number or Email',
      'emailOrPhone': 'Email or Phone number',
      'password': 'Password',
      'forgotPassword': 'Forgot Password?',
      'createAccount': 'Create account',
      'adminPanel': 'Admin Panel',
      'createAdminUser': 'Create admin user',
      'createAdminUserDescription':
          'Create a new admin user. They will be able to sign in to the admin panel.',
      'adminUserCreated': 'Admin user created successfully',
      'enterValidEmail': 'Enter a valid email address',
      'passwordMinLength': 'Password must be at least 8 characters',
      'enterEmailOrPhone': 'Enter email or phone',
      'verify': 'Verify',
      'oneTimeKey': 'One-time key',
      'pleaseEnterOneTimeKey': 'Please enter your one-time key.',
      'keyVerified': 'Key verified',
      'firstName': 'First name',
      'lastName': 'Last name',
      'emailOptional': 'Email (optional)',
      'confirmPassword': 'Confirm password',
      'enterFirstName': 'Enter first name',
      'enterLastName': 'Enter last name',
      'enterPhoneNumber': 'Enter phone number',
      'optional': 'Optional',
      'pleaseVerifyInvitationKeyFirst':
          'Please verify your invitation key first.',
      'accountInformation': 'Account Information',
      'dateOfBirth': 'Date of Birth',
      'clinic': 'Clinic',
      'profession': 'Profession',
      'generalPractitioner': 'General Practitioner',
      'cardiologist': 'Cardiologist',
      'dermatologist': 'Dermatologist',
      'pediatrician': 'Pediatrician',
      'accountCreatedPleaseSignIn': 'Account created! Please sign in.',
      'existingPatientCreatingDoctorAccount':
          'There is already a patient with this information. We are creating a doctor account for this patient.',
      'confirmRegistration': 'Confirm registration',
      'signInToManageSystem': 'Sign in to manage the system',
      'goToDoctorLogin': 'Go to Doctor Login',
      'adminEmailVerificationSent':
          'A 6-digit verification code was sent to {hint}. Enter it below to finish signing in.',
      'adminEnterVerificationCode': 'Verification code',
      'adminVerifyAndSignIn': 'Verify and sign in',
      'adminResendVerificationCode': 'Resend code',
      'adminChangeAccount': 'Use a different account',
      // Phone OTP & Forgot password
      'signInWithPhone': 'Sign in with Phone Number',
      'signInWithEmail': 'Sign in with Email',
      'enterEmailForOtp': 'Enter your email address to receive a verification code.',
      'enterEmail': 'Enter email address',
      'otpSentToEmail': 'A 6-digit code has been sent to {email}. Check your inbox.',
      'continue': 'Continue',
      'enterOtp': 'Enter OTP',
      'resendCode': 'Resend code',
      'resendCodeIn': 'Resend code in {{time}}',
      'tooManyRequests': 'Too many requests. Please try again later.',
      'invalidOtp': 'Invalid or expired OTP.',
      'resetPassword': 'Reset Password',
      'passwordMismatch': 'Passwords do not match.',
      'passwordTooWeak':
          'Password must be at least 8 characters with 1 uppercase and 1 number.',
      'accessRestricted': 'Access restricted to doctors.',
      'accountPending': 'Your account is pending approval.',
      'accountBlocked': 'Your account has been blocked.',
      'otpSent': 'Verification code sent.',
      'otpResent': 'Code resent.',
      'otpResendHint': 'To get a new code, go back and tap Continue again.',
      'detecting': 'Detecting…',
      'practiceTimezonePlaceholder': 'Practice timezone (e.g. Europe/Berlin)',

      // Profile
      'editProfile': 'Edit Profile',
      'language': 'Language',
      'settings': 'Settings',
      'english': 'English',
      'uzbek': 'Uzbek',
      'uzbekCyrillicMenu': 'Uzbek (Cyrillic)',
      'selectLanguage': 'Select Language',
      'languageChanged': 'Language changed successfully',
      'biography': 'Biography',
      'services': 'Services',
      'certificates': 'Certificates',
      'telegram': 'Telegram',
      'instagram': 'Instagram',
      'uploadCertificate': 'Upload Certificate',
      'addService': 'Add Service',
      'removeService': 'Remove Service',
      'openServicesPricingToManageEntries':
          'Open Services & Pricing to manage entries',
      'enterService': 'Enter service name',
      'servicesPricing': 'Services & Pricing',
      'servicesPricingSubtitle':
          'Manage service titles, prices, currencies and descriptions',
      'servicesPricingPanelDesc':
          'Define billable services with descriptions and multi-currency prices.',
      'openServicesPricing': 'Open Services & Pricing',
      'newService': 'New Service',
      'editService': 'Edit Service',
      'serviceTitleLabel': 'Title',
      'serviceDescriptionLabel': 'Description',
      'servicePriceLabel': 'Price amount (e.g. 25.00)',
      'serviceCurrencyLabel': 'Currency (EUR/UZS/USD)',
      'serviceFreeConsultation': 'Free consultation (video)',
      'serviceFreeConsultationHint':
          'Patients who choose this service for a video visit are confirmed immediately with no payment.',
      'serviceGroupsTitle': 'Service groups',
      'serviceGroupsHint':
          'Use groups to organize services on your profile. Lower sort order appears first.',
      'serviceGroupLabel': 'Group',
      'serviceGroupNone': 'No group',
      'servicePricesSection':
          'Prices: add a default row for all locations, or separate rows to override specific clinics.',
      'priceScopeLabel': 'Applies to',
      'priceScopeAllLocations': 'All locations (default)',
      'addPriceRow': 'Add price',
      'editGroup': 'Edit group',
      'groupName': 'Group name',
      'sortOrder': 'Sort order',
      'newGroup': 'New group',
      'addGroup': 'Add group',
      'profileInformation': 'Profile Information',
      'contactDetails': 'Contact Details',
      'paymentAndInvoicing': 'Payment and Invoicing',
      'profileInformationSaved': 'Profile information saved',
      'contactDetailsSaved': 'Contact details saved',
      'paymentAndInvoicingSaved': 'Payment & invoicing saved',
      'settingsSaved': 'Settings saved',
      'passwordUpdated': 'Password updated',
      'passwordUpdatedSuccessfully': 'Password updated successfully',
      'newPasswordConfirmationMismatchError':
          'New password and confirmation do not match',
      'currentPasswordIsRequired': 'Current password is required',
      'pleaseConfirmNewPasswordError': 'Please confirm new password',
      'currentPassword': 'Current Password',
      'newPassword': 'New Password',
      'confirmNewPassword': 'Confirm New Password',
      'billingName': 'Billing Name',
      'billingEmail': 'Billing Email',
      'on': 'On',
      'off': 'Off',
      'fullName': 'Full Name',
      'country': 'Country',
      'twoFactorAuthentication': 'Two-factor Authentication',
      'encryptedDocuments': 'Encrypted Documents',
      'updateOrChangeSchedule': 'Update or change your schedule',
      'changeOrResetPassword': 'Change or reset your password here',
      'settingsSubtitle': 'Country, Language, 2FA, Encrypted Docs',
      'extendedProfileSubtitle':
          'Biography, Services, Certificates, Social Media',
      'phone': 'Phone',
      'yourName': 'Your Name',

      // Home
      'dashboard': 'Dashboard',
      'todayAppointments': 'Today\'s Appointments',
      'upcomingAppointments': 'Upcoming Appointments',
      'recentPatients': 'Recent Patients',
      'analytics': 'Analytics',

      // Calendar
      'appointments': 'Appointments',
      'freeSlots': 'Free Slots',
      'date': 'Date',
      'time': 'Time',
      'duration': 'Duration',
      'place': 'Place',
      'changeSlot': 'Change Slot',
      'cancelAppointment': 'Cancel Appointment',
      'cancelConfirm': 'Are you sure you want to cancel this appointment?',
      'appointmentCancelled': 'Appointment cancelled successfully',
      'pastAppointmentNoChange':
          'Past appointments cannot be changed or cancelled.',
      'pastSlotCannotAssign':
          'This slot is in the past. You cannot assign a patient.',
      'slotChanged': 'Slot changed successfully',
      'makeAppointment': 'Make appointment',
      'selectDate': 'Select Date',
      'selectTime': 'Select Time',
      'availableSlots': 'Available Slots',
      'appointmentType': 'Appointment type',
      'noSlotsAvailable': 'No slots available',
      'noAppointments': 'No appointments',
      'noFreeSlots': 'No free slots',
      'noItemsForThisDay': 'No items for this day',
      'selectDatesToSeeSchedule': 'Select dates to see your schedule',
      'showAppointments': 'Show Appointments',
      'showFreeSlots': 'Show Free Slots',
      'goToSchedule': 'Go To Schedule',
      'updateScheduleMessage':
          'Update schedule - Your calendar does not provide booking slots this far ahead',
      'slotDetails': 'Slot Details',
      'choosePlace': 'Choose Place',
      'reason': 'Reason for visit',
      'failedToLoad': 'Failed to load',
      'failedToChangeSlot': 'Failed to change slot',
      'monthJanuary': 'January',
      'monthFebruary': 'February',
      'monthMarch': 'March',
      'monthApril': 'April',
      'monthMay': 'May',
      'monthJune': 'June',
      'monthJuly': 'July',
      'monthAugust': 'August',
      'monthSeptember': 'September',
      'monthOctober': 'October',
      'monthNovember': 'November',
      'monthDecember': 'December',

      // Patients
      'patient': 'Patient',
      'patientList': 'Patient List',
      'searchPatients': 'Search patients...',
      'noPatientsFound': 'No patients found',
      'patientDetails': 'Patient Details',
      'generalInformation': 'General Information',
      'documents': 'Documents',
      'loadingPatients': 'Loading patients…',
      'noPatientsAvailable': 'No patients available',
      'noPatientSelected': 'No patient selected',
      'assignPatient': 'Assign Patient',
      'patientAssigned': 'Patient assigned',
      'failedToAssign': 'Failed to assign',
      'clinicAddress': 'Clinic Address',
      'city': 'City',
      'dateAndTime': 'Date and Time',
      'notSelected': 'Not selected',
      'selected': 'Selected',
      'saved': 'Saved',
      'pleaseSelectDateFirst': 'Please select a date first',
      'chronicDisease': 'Chronic Disease',
      'selectChronicDisease': 'Select Chronic Disease',
      'noChronicDisease': 'No chronic disease',
      'chronicDiseaseWarning':
          'Warning: This patient has a severe/chronic disease. Please take extra care.',
      'createTask': 'Create Task',
      'assignResult': 'Assign Result',
      'startAppointment': 'Start Appointment',
      'phoneNumber': 'Phone Number',
      'phoneNumberRequired': 'Phone number is required',
      'email': 'Email',
      'address': 'Address',
      'location': 'Location',
      'latitude': 'Latitude',
      'longitude': 'Longitude',
      'getCurrentLocation': 'Get Current Location',
      'saveLocation': 'Save Location',
      'locationSaved': 'Location saved',
      'invalidCoordinates': 'Please enter valid coordinates',
      'locationFeatureComingSoon':
          'Location feature coming soon. Please enter coordinates manually.',
      'selectLocationOnMap': 'Select Location on Map',
      'currentLocation': 'Current Location',
      'addressFromCoordinates': 'Address from Coordinates',
      'coordinatesFromAddress': 'Coordinates from Address',
      'enterAddressToFindCoordinates': 'Enter address to find coordinates',
      'addressFound': 'Address found',
      'addressNotFound': 'Address not found',
      'pleaseEnterAddress': 'Please enter an address',
      'locationServicesDisabled':
          'Location services are disabled. Please enable them.',
      'locationPermissionDenied': 'Location permissions are denied.',
      'locationPermissionDeniedForever':
          'Location permissions are permanently denied. Please enable them in settings.',
      'selectedLocation': 'Selected Location',
      'selectLocation': 'Select location',
      'primary': 'Primary',
      'manage': 'Manage',
      'label': 'Label',
      'manageLocations': 'Manage locations',
      'addLocation': 'Add location',
      'editLocation': 'Edit location',
      'deleteLocation': 'Delete location',
      'deleteLocationConfirm':
          'Delete "{label}"? Schedule rules and appointments at this location must be removed first.',
      'noLocationsYet':
          'No locations yet. Tap "Add location" to create your first one.',
      'labelRequired': 'Label is required',
      'exampleMainClinic': 'e.g. Main Clinic',
      'setAsPrimary': 'Set as primary',
      'addFirstLocationHint':
          'Add at least one practice location to organize your schedule.',
      'copyFromPreviousDay': 'Copy from previous day',
      'copyFromAnotherDay': 'Copy from another day',
      'copyScheduleFromDay': 'Copy schedule from which day?',
      'noPreviousDayScheduleToCopy':
          'Previous day has no schedule to copy.',
      'scheduleCopiedFromPreviousDay': 'Schedule copied from previous day.',
      'noSourceDaysToCopyFrom': 'No other days have schedule to copy from.',
      'failedToCopySchedule':
          'Failed to copy schedule from selected day.',
      'scheduleCopiedFromDay': 'Schedule copied from {day}.',
      'current': 'Current',
      'birthDate': 'Birth Date',
      'gender': 'Gender',
      'male': 'Male',
      'female': 'Female',
      'other': 'Other',

      // Tasks
      'remoteCareTasks': 'Remote Care Tasks',
      'remoteCareTasksSubtitle': 'Monitor and manage patient follow-ups',
      'createRemoteCareTask': 'Create Remote Care Task',
      'taskTemplates': 'Task Templates',
      'useTemplate': 'Use Templates',
      'activeTasks': 'Active tasks',
      'completedTasks': 'Completed tasks',
      'overdueTasks': 'Overdue tasks',
      'searchTasksOrPatients': 'Search tasks or patients',
      'createFirstRemoteTask': 'Create your first remote care task',
      'taskProgress': 'Task progress',
      'perDay': 'per day',
      'taskName': 'Task Name',
      'description': 'Description',
      'category': 'Category',
      'vital': 'Vital',
      'exercise': 'Exercise',
      'medication': 'Medication',
      'taskOther': 'Other',
      'timesPerDay': 'Times per day',
      'morningTime': 'Morning Time',
      'afternoonTime': 'Afternoon Time',
      'eveningTime': 'Evening Time',
      'startDate': 'Start Date',
      'startTime': 'Start Time',
      'intervalBetweenTasks': 'Interval between tasks',
      'everyNHours': 'Every %d hours',
      'every1Hour': 'Every 1 hour',
      'slotsPreviewLabel': 'Daily slots',
      'slotsPreviewClipped':
          '%d slot(s) won\'t fit before midnight. Pick a smaller interval or earlier start time.',
      'scheduleMode': 'Schedule',
      'scheduleModeEvenSpacing': 'Even spacing',
      'scheduleModeCustomTimes': 'Custom times',
      'customTimesLabel': 'Daily slot times',
      'customTimesHint':
          'Define each slot explicitly to support non-uniform schedules.',
      'customTimesEmpty': 'No slots yet — add one below.',
      'customTimesAddSlot': 'Add time slot',
      'customTimesAddAtLeastOne': 'Add at least one time slot',
      'customTimesCount': '%d slot(s) per day',
      'edit': 'Edit',
      'remove': 'Remove',
      'endDate': 'End Date',
      'durationDays': 'Duration (days)',
      'useEndDate': 'Use end date (otherwise use duration)',
      'inputType': 'Input Type',
      'numeric': 'Numeric',
      'text': 'Text',
      'boolean': 'Yes/No',
      'inputLabel': 'Input Label',
      'notesRequired': 'Notes Required',
      'notesLabel': 'Notes Label',
      'taskCreated': 'Task created successfully',
      'taskUpdated': 'Task updated successfully',
      'taskCancelled': 'Task cancelled successfully',
      'failedToCreateTask': 'Failed to create task',
      'failedToUpdateTask': 'Failed to update task',
      'selectPatient': 'Select Patient',
      'tapToSearch': 'Tap to search and select patient',
      'searchByNameOrId': 'Search by name or ID',
      'taskDetails': 'Task Details',
      'progress': 'Progress',
      'checkInCompleted': 'Completed',
      'pending': 'Pending',
      'missed': 'Missed',
      'checkIns': 'Check-ins',
      'checkInDetails': 'Check-in Details',
      'scheduled': 'Scheduled',
      'submittedAt': 'Submitted at',
      'awaitingSubmission': 'Awaiting submission',
      'noSubmissionReceived': 'No submission received',
      'status': 'Status',
      'active': 'Active',
      'taskCompleted': 'Completed',
      'expired': 'Expired',
      'taskStatusCancelled': 'Cancelled',
      'draft': 'Draft',
      'all': 'All',
      'noTasksFound': 'No tasks found',
      'taskDescription': 'Task description shown to patient',
      'enterTaskName': 'Enter task name',
      'enterInputLabel': 'e.g., Blood Pressure, Weight (kg)',
      'enterNotesLabel': 'e.g., Additional notes',
      'notSet': 'Not set',

      // Documents
      'uploadDocument': 'Upload Document',
      'documentTitle': 'Document Title',
      'enterDocumentTitle': 'Enter document title',
      'selectFile': 'Select File',
      'documentUploaded': 'Document uploaded successfully',
      'uploadFailed': 'Upload failed',
      'noDocuments': 'No documents available',
      'openDocument': 'Open Document',
      'requestAccess': 'Request access',
      'documentLocked': 'Locked',
      'uploadedBy': 'Uploaded by',
      'anotherUser': 'Another user',
      'listRefreshed': 'Patient list refreshed',
      // Document categories / visibility
      'documentCategoryLabel': 'Document type',
      'documentCategorySelect': 'Select a type',
      'documentCategoryHint':
          'Pick a medical-result type to share this document with all doctors of this patient. Internal/private types stay visible only to you.',
      'documentCategoryGroupMedical':
          'Medical results (visible to all doctors)',
      'documentCategoryGroupPrivate': 'Private (visible only to you)',
      'sharedWithTeamTooltip': 'Visible to all doctors of this patient',
      'documentCategory_BLOOD_TEST': 'Blood test',
      'documentCategory_URINE_TEST': 'Urine test',
      'documentCategory_STOOL_TEST': 'Stool test',
      'documentCategory_LAB_RESULT': 'Lab result',
      'documentCategory_MRI': 'MRI',
      'documentCategory_CT_SCAN': 'CT scan',
      'documentCategory_XRAY': 'X-ray',
      'documentCategory_ULTRASOUND': 'Ultrasound',
      'documentCategory_MAMMOGRAPHY': 'Mammography',
      'documentCategory_ECG': 'ECG',
      'documentCategory_EEG': 'EEG',
      'documentCategory_ENDOSCOPY': 'Endoscopy',
      'documentCategory_BIOPSY': 'Biopsy',
      'documentCategory_PATHOLOGY': 'Pathology',
      'documentCategory_IMAGING_OTHER': 'Other imaging',
      'documentCategory_PRESCRIPTION': 'Prescription',
      'documentCategory_VACCINATION_RECORD': 'Vaccination record',
      'documentCategory_DISCHARGE_SUMMARY': 'Discharge summary',
      'documentCategory_REFERRAL': 'Referral',
      'documentCategory_HOSPITAL_REPORT': 'Hospital report',
      'documentCategory_ALLERGY_REPORT': 'Allergy report',
      'documentCategory_OTHER_MEDICAL': 'Other medical result',
      'documentCategory_APPOINTMENT_NOTE': 'Appointment note',
      'documentCategory_REMOTE_TASK_DOCUMENT': 'Remote task document',
      'documentCategory_FORM_025_2': '025-2 form',
      'documentCategory_INTERNAL_NOTE': 'Internal note',
      'documentCategory_OTHER_PRIVATE': 'Other private document',
      'couldNotLoadDocument': 'Could not load document',
      // Appointments
      'appointmentDetails': 'Appointment Details',
      'patientName': 'Patient Name',
      'appointmentDate': 'Appointment Date',
      'appointmentTime': 'Appointment Time',
      'appointmentPlace': 'Place',
      'videoCall': 'Video Call',
      'inClinic': 'In Clinic',
      'notes': 'Notes',
      'enterNotes': 'Enter appointment notes...',
      'beforeTreatment': 'Before Treatment',
      'afterTreatment': 'After Treatment',
      'startAiNotes': 'Start AI Notes',
      'recordingForAiNotes': 'Recording for AI Notes',
      'processRecording': 'Process',
      'aiNotesUploaded':
          'Recording uploaded. Notes will be ready in a few minutes.',
      'aiNotesNotReadyTryLater':
          'AI notes are not ready yet. Please try again in 30 seconds.',
      'uploadPhoto': 'Upload Photo',
      'endAppointment': 'End Appointment',
      'appointmentEnded': 'Appointment ended successfully',
      'documentGenerated': 'Document generated successfully',
      'viewDocument': 'View Document',
      'requestSignature': 'Request Signature',
      'waitingForPatientSignature': 'Waiting for patient signature...',
      'patientSigned': 'Patient Signed ✓',
      'signatureRequestSent': 'Signature request sent to patient',

      // Chat
      'messages': 'Messages',
      'typeMessage': 'Type a message...',
      'send': 'Send',
      'noConversations': 'No conversations yet',
      'newestFirst': 'Newest First',
      'oldestFirst': 'Oldest First',
      'unreadOnlyNewest': 'Unread Only (Newest First)',
      'unreadOnlyOldest': 'Unread Only (Oldest First)',
      'noUnreadConversations': 'No unread conversations',
      'justNow': 'Just now',
      'minuteAgo': '1 minute ago',
      'minutesAgo': '%s minutes ago',
      'hourAgo': '1 hour ago',
      'hoursAgo': '%s hours ago',
      'yesterday': 'Yesterday',
      'isTyping': 'is typing',
      'selectConversation': 'Select a conversation',
      'searchDoctorsAndPatients': 'Search doctors and patients',
      'noUsersFound': 'No users found',
      'attachFile': 'Attach file',
      'selectImage': 'Select Image',
      'takePhoto': 'Take Photo',
      'chooseFromGallery': 'Choose from Gallery',
      'recordVoice': 'Record Voice Message',
      'voiceMessage': 'Voice Message',
      'voiceRecordingFinishHint':
          'Stop when you are done — pauses and filler words are fine.',
      'cancel': 'Cancel',
      'sendVoice': 'Send Voice',
      'selectDocument': 'Select Document',
      'compressingImage': 'Compressing image...',
      'uploadingFile': 'Uploading file...',
      'errorUploadingFile': 'Error uploading file',
      'errorRecordingVoice': 'Error recording voice',
      'noFileSelected': 'No file selected',
      'voiceRecordingNotSupportedOnWeb':
          'Voice recording is not supported on web. Please use the mobile app.',
      'microphonePermissionDenied': 'Microphone permission denied',
      'failedToStartConversation': 'Failed to start conversation',
      'failedToSendMessage': 'Failed to send message',

      // Video call
      'failedToStartVideoCall': 'Failed to start video call',
      'videoCallAvailableFiveMinBefore':
          'You can start 5 minutes before the appointment.',
      'videoCallTooLateAfterOneHour':
          'This video appointment ended more than an hour ago. Open it only if the consultation is already in progress.',
      'videoCallEnded': 'Video call ended',
      'callErrorOccurred': 'Call error occurred',
      'waitingForParticipants': 'Waiting for participants...',
      'joinVideoCall': 'Join Video Call',
      'videoCallReady': 'Video call ready',
      'videoCallErrorTitle': 'Video call error',
      'videoCallConnecting': 'Connecting to video call...',
      'videoCallNotAvailableShort': 'Video call not available',
      'videoCallJoinWindowClosedMessage':
          'Video call has ended. The join window closes 15 minutes after the appointment end.',
      'videoCallNotYetAvailableMessage':
          'Video call is not yet available. You can join 5 minutes before the appointment start.',
      'videoCallPaymentRequiredMessage':
          'Payment is required before joining this video consultation.',
      'clickBelowToJoinCall': 'Click below to join the call',
      'waitingRoom': 'Waiting Room',
      'isWaiting': 'is waiting',
      'openRoomWhenReady': 'Open the room when you\'re ready to start the call',
      'willBeBookedAsVideoCall': 'Will be booked as video call',
      'willBeBookedAtClinic': 'Will be booked at clinic',

      // Admin
      'tokenRevoked': 'Token revoked',
      'tokenRegenerated': 'Token regenerated',
      'configUpdated': 'Config updated',
      'noContact': 'No contact',
      'noEmail': 'No email',
      'noPhone': 'No phone',
      'userManagement': 'User Management',
      'filterByRole': 'Filter by Role',
      'searchUsersPlaceholder': 'Search by name, number, role',
      'allRoles': 'All Roles',
      'doctors': 'Doctors',
      'admins': 'Admins',
      'filterByStatus': 'Filter by Status',
      'enabled': 'Enabled',
      'disabled': 'Disabled',
      'disable': 'Disable',
      'enable': 'Enable',
      'lastLogin': 'Last Login',
      'unlock': 'Unlock',
      'resetPassword': 'Reset Password',
      'passwordReset': 'Password Reset',
      'temporaryPassword': 'Temporary Password',
      'sharePasswordSecurely': 'Share this password securely with the user',
      'forceLogout': 'Force Logout',
      'userLoggedOut': 'User logged out successfully',
      'changeSubscriptionTier': 'Change Subscription Tier',
      'subscriptionTierBasic': 'Basic',
      'subscriptionTierPro': 'Pro',
      'subscriptionTierPremium': 'Premium',
      'subscriptionTierDialogHint':
          'Pick the subscription level for this user. Patients can only be Pro or Premium. The user will be logged out so the new tier takes effect on next sign-in.',
      'subscriptionTierUpdated': 'Subscription tier updated',
      'deleteUser': 'Delete User',
      'deleteUserConfirm':
          'Are you sure you want to delete this user? This action cannot be undone.',
      'userDeleted': 'User deleted successfully',
      'resetDoctorCalendar': 'Reset Doctor Calendar',
      'doctorCalendarResetConfirm':
          'Are you sure you want to reset this doctor\'s calendar? All appointments will be cancelled.',
      'confirmReset': 'Confirm Reset',
      'doctorCalendarResetSuccessfully': 'Doctor calendar reset successfully',
      'doctorProfileIdNotFound': 'Doctor profile ID not found',
      'noUsersFound': 'No users found',
      'page': 'Page',
      'of': 'of',
      'temporaryPassword': 'Temporary password:',
      'sharePasswordSecurely':
          'Please share this password securely with the user.',
      'forceLogout': 'Force Logout',
      'userLoggedOut': 'User logged out',
      'deleteUser': 'Delete user',
      'deleteUserConfirm':
          'Permanently delete this user and all their data (appointments, messages, profile)? The phone number and email can then be used to create a new account. This cannot be undone.',
      'userDeleted': 'User and all data deleted',
      'resetDoctorCalendar': 'Reset Doctor Calendar',
      'confirmReset': 'Confirm Reset',
      'doctorCalendarResetConfirm':
          'This will permanently delete all appointments and availability slots for this doctor. This action cannot be undone. The doctor will keep their account, profile, and credentials; they will need to set up availability again.',
      'doctorCalendarResetSuccessfully': 'Doctor calendar reset successfully',
      'doctorProfileIdNotFound':
          'Error: Doctor profile ID not found. Please refresh the list.',
      'tokenManagement': 'Token Management',
      'consumed': 'Consumed',
      'loadingTokens': 'Loading tokens...',
      'errorLoadingTokens': 'Error loading tokens',
      'noTokensFound': 'No tokens found',
      'generateToken': 'Generate Token',
      'expiresInDaysOptional': 'Expires in (days, optional)',
      'notesOptional': 'Notes (optional)',
      'tokenGeneratedSuccessfully': 'Token generated successfully',
      'generate': 'Generate',
      'copyKey': 'Copy Key',
      'keyCopiedToClipboard': 'Key copied to clipboard',
      'revoke': 'Revoke',
      'regenerate': 'Regenerate',
      'expires': 'Expires',
      'systemConfiguration': 'System Configuration',
      'editKey': 'Edit',
      'enable': 'Enable',
      'disable': 'Disable',
      'page': 'Page',
      'of': 'of',
      'totalDoctors': 'Total Doctors',
      'activeDoctors': 'Active Doctors',
      'totalPatients': 'Total Patients',
      'activeTokens': 'Active Tokens',
      'auditLogs': 'Audit Logs',
      'noLogsFound': 'No logs found',
      'logout': 'Logout',
      'confirmLogout': 'Are you sure you want to log out?',
      'quickActions': 'Quick Actions',
      'viewUsers': 'View Users',
      'viewLogs': 'View Logs',

      // UI tooltips & actions
      'addRow': 'Add row',
      'removeRow': 'Remove row',
      'editRow': 'Edit row',
      'collapse': 'Collapse',
      'expand': 'Expand',
      'formSavedSuccessfully': 'Form saved successfully',
      'errorSavingForm': 'Error saving form',
      'pdfGeneratedPrintingNotImplemented':
          'PDF generated. Printing not yet implemented.',
      'docMode0252': 'Form 025-2',
      'docModeGeneral': 'General Form',
      'docModeDental': 'Dental visit record',
      'dentalDocIntro':
          'Tap a tooth (FDI-style quadrants), add your catalog services, then set discount if needed. Totals use the first currency found on priced lines.',
      'dentalUpperJaw': 'Upper jaw',
      'dentalLowerJaw': 'Lower jaw',
      'dentalDiscountPercent': 'Discount',
      'dentalClinicalNotes': 'Clinical notes',
      'dentalSubtotal': 'Subtotal',
      'dentalDiscount': 'Discount',
      'dentalTotal': 'Total due',
      'dentalLineItems': 'line items',
      'dentalToothServices': 'Services for tooth',
      'dentalAddService': 'Add service',
      'dentalNoServices': 'Define services under Services & Pricing first.',
      'dentalDocSaved': 'Dental visit documentation saved',
      'dentalDocSaveFailed': 'Could not save dental documentation',
      'dentalPdfHeader': 'DENTAL VISIT — Procedures by tooth',
      'openForm0252': 'Open Form 025-2',
      'fileAttachmentComingSoon': 'File attachment coming soon',

      // Errors & Validation
      'invalidEmail': 'Please enter a valid email address',
      'invalidPhone': 'Please enter a valid phone number',
      'passwordTooShort': 'Password must be at least 8 characters',
      'passwordTooLong': 'Password must be at most 128 characters',
      'passwordsDoNotMatch': 'Passwords do not match',
      'passwordRequired': 'Password is required',
      'pleaseConfirmPassword': 'Please confirm your password',
      'passwordRequirementMinLength': 'At least 8 characters',
      'passwordRequirementMaxLength': 'At most 128 characters',
      'passwordRequirementUppercase': 'One uppercase letter',
      'passwordRequirementLowercase': 'One lowercase letter',
      'passwordRequirementDigit': 'One number',
      'passwordRequirementSpecialChar':
          'One special character (!@#\$%^&* etc.)',
      'fieldRequired': 'This field is required',
      'pleaseSelectPatient': 'Please select a patient',
      'pleaseEnterTaskName': 'Task name is required',
      'unauthorized': 'Unauthorized. Please login again.',
      'networkError': 'Network error. Please check your connection.',
      'unknownError': 'An unknown error occurred',

      // Notifications
      'notifications': 'Notifications',
      'noNotifications': 'No notifications',
      'markAllAsRead': 'Mark all as read',
      'approve': 'Approve',
      'reject': 'Reject',
      'approved': 'Approved',
      'rejected': 'Rejected',
      'documentAccessApproved': 'Access granted',
      'documentAccessRejected': 'Access request rejected',
      'documentAccessRequest': 'Document access request',
      'documentAccessRequestDetail':
          '{doctorName} requested access to "{documentTitle}" for {patientName}.',
      'documentAccessApprovedDetail':
          'Your request for access to "{documentTitle}" for {patientName} was approved.',
      'documentAccessRejectedDetail':
          'Your request for access to "{documentTitle}" for {patientName} was rejected.',
      'notificationGeneric': 'Notification',
      'somethingWentWrong': 'Something went wrong',
      'imageNotAvailable': 'Image not available',
      'failedToLoadImage': 'Failed to load image',
      'requestAccessSent': 'Request sent',
      'notificationFilterAll': 'All',
      'notificationFilterAppointments': 'Appointments',
      'notificationFilterTasks': 'Tasks',
      'notificationFilterMessages': 'Messages',
      'notificationSettings': 'Settings',
      'notificationViewResult': 'View Result',
      'notificationViewAppointment': 'View Appointment',
      'notificationOpenCalendar': 'Open Calendar',
      'notificationReschedule': 'Reschedule',
      'notificationEmptyFilter': 'No notifications in this category',
      'notificationEmptyFilterHint': 'Try another filter or check back later.',
      'notificationEmptyBody':
          'You\'ll see appointment updates, messages and patient activity here.',
      'timeJustNow': 'Just now',
      'timeMinAgo': '{n} min ago',
      'timeYesterday': 'Yesterday {time}',
      'monthJan': 'Jan',
      'monthFeb': 'Feb',
      'monthMar': 'Mar',
      'monthApr': 'Apr',
      'monthMay': 'May',
      'monthJun': 'Jun',
      'monthJul': 'Jul',
      'monthAug': 'Aug',
      'monthSep': 'Sep',
      'monthOct': 'Oct',
      'monthNov': 'Nov',
      'monthDec': 'Dec',
      'notificationTypeAppointmentBooked': 'Appointment booked',
      'notificationTypeAppointmentCancelled': 'Appointment cancelled',
      'notificationTypeTaskCompleted': 'Task completed',
      'notificationTypeTaskAssigned': 'Task assigned',
      'notificationTypeDocumentAccessRequest': 'Document access request',
      'notificationTypeDocumentAccessApproved': 'Document access approved',
      'notificationTypeDocumentAccessRejected': 'Document access rejected',
      'notificationTypeAiScribeReady': 'AI Scribe summary ready',
      'notificationMessagePatientBookedAppointment':
          'Patient {name} booked an appointment.',
      'notificationMessageAppointmentReminder':
          'Your appointment is in about 1 hour. Please be ready.',
      'patientBriefingTitle': 'Patient briefing',
      'patientBriefingError': 'Could not generate briefing.',
      'patientBriefingSources': 'Based on {n} document(s).',
      'patientBriefingSourcesWithAppointments':
          'Based on {docs} document(s) and {appts} appointment(s).',
      'patientBriefingSourcesAppointmentsOnly': 'Based on {n} appointment(s).',
      'patientBriefingCopied': 'Briefing copied to clipboard.',
      'patientBriefingCopy': 'Copy',
      'generateBriefing': 'Generate briefing',
      'patientBriefingGenerating':
          'Reading documents and generating briefing...',
      'aiFollowupRefineDiagnosis': 'Refine diagnosis',
      'aiFollowupTreatmentOptions': 'Treatment options',
      'aiFollowupWhenToWorry': 'When to worry',
      'aiFollowupPromptRefineDiagnosis':
          'Refine diagnosis based on your previous answer and provide differential prioritization.',
      'aiFollowupPromptTreatmentOptions':
          'Provide treatment options and first-line management based on your previous assessment.',
      'aiFollowupPromptWhenToWorry':
          'Expand the red flags and when urgent or emergency escalation is required.',
      'showAiAndFormNotes': 'Show',
      'hideAiAndFormNotes': 'Hide',
      'notesSectionsHidden':
          'AI and form notes are hidden. Use ⋮ → Show to display.',
      'unsavedChangesSwitch':
          'You have unsaved changes. Save before switching?',
      'saveAndSwitch': 'Save and switch',
      'discardAndSwitch': 'Discard and switch',

      // Chronic Diseases
      'aids': 'AIDS',
      'diabetes': 'Diabetes',
      'hypertension': 'Hypertension',
      'heartDisease': 'Heart Disease',
      'cancer': 'Cancer',
      'kidneyDisease': 'Kidney Disease',
      'liverDisease': 'Liver Disease',
      'asthma': 'Asthma',
      'copd': 'COPD',
      'epilepsy': 'Epilepsy',

      // Additional task-related
      'taskNotFound': 'Task not found',
      'noCheckInsFound': 'No check-ins found',
      'cancelTask': 'Cancel Task',
      'cancelTaskConfirm': 'Are you sure you want to cancel this task?',
      'yesCancel': 'Yes, Cancel',
      'failedToCancel': 'Failed to cancel',
      'days': 'days',
      'value': 'Value',
      'newPatient': 'New Patient',
      'createNewPatient': 'Create New Patient',
      'createPatient': 'Create Patient',
      'patientCreated': 'Patient created',
      'createFailed': 'Create failed',
      'documentHistory': 'Document History',
      'createForm': 'Create Form',
      'selectFormTemplate': 'Select Form Template',
      'uploadPdf': 'Upload PDF',
      'takePhoto': 'Take Photo',
      'chooseFromGallery': 'Choose from Gallery',
      'scanMultiPage': 'Scan (multi-page)',
      'add': 'Add',
      'uploading': 'Uploading...',
      'noFileData': 'No file data to upload',
      'document': 'Document',
      'uploaded': 'uploaded',
      'uploadError': 'Upload error',
      'addAnotherPage': 'Add another page?',
      'pagesScanned': 'Pages scanned',
      'finish': 'Finish',
      'addPage': 'Add page',
      'scannedDocument': 'Scanned document',
      'pages': 'pages',
      'pageDocument': 'page document',
      'scanFailed': 'Scan failed',
      'appointmentsLast7Days': 'Appointments (Last 7 Days)',
      'visitTypeDistribution': 'Visit Type Distribution',
      'inPerson': 'In-person',
      'appointmentsToday': 'Appointments Today',
      'completedToday': 'Completed Today',
      'cancelledToday': 'Cancelled / No-show',
      'newPatientsToday': 'New Patients Today',
      'activePatients': 'Active Patients (30d)',
      'documentsReceived': 'Documents Received (30d)',
      'engagementSummary': 'Patient Engagement',
      'analyticsNoData': 'No data yet',
      'extendedProfile': 'Extended Profile',
      'schedule': 'Schedule',
      'profession': 'Profession',
      'selectDateHint': 'Select Date of Birth',
      'ibanAccountNumber': 'IBAN / Account number',
      'taxIdVatId': 'Tax ID / VAT ID',
      'certificateUploaded': 'Certificate uploaded',
      'minimum6Characters': 'Minimum 6 characters',
      'doctor': 'Doctor',
      'noMessages': 'No messages',
      'photoUpdated': 'Photo updated',
      'extendedProfileSaved': 'Extended profile saved',
      'socialMedia': 'Social Media',
      'socialMediaHint': '@username or URL',
      'patientWithChronicDisease': 'Patient with Chronic Disease',
      'dealingWithChronicDiseasePatient':
          'You are dealing with a patient who has a chronic disease:',
      'takeExtraCareForChronicDiseasePatient':
          'Please be careful and double-check all procedures, medications, and treatments you are planning for this patient.',
      'iUnderstand': 'I Understand',
      'patientAppAccess': 'Patient App Access',
      'createPatientAccount': 'Create Patient Account',
      'accountCreated': 'Account Created',
      'accountAlreadyAvailable': 'Account already available',
      'noAccountYet': 'No account yet',
      'shareCredentialsWithPatient':
          'Please share these credentials with the patient. They will be required to change their password on first login.',
      'username': 'Username',
      'oneTimePassword': 'One-time Password',
      'forSecurityPasswordShownOnce':
          '⚠️ For security, this password is shown only once.',
      'errorCreatingAccount': 'Error creating account',
      'copiedToClipboard': 'copied to clipboard',
      'copy': 'Copy',
      'patientIdNotAvailable': 'Patient ID not available',
      'cannotSaveNotes': 'Cannot save notes.',
      'noItemsToSave': 'No items to save. Appointment ended.',
      'appointmentEndedDocumentationSaved':
          'Appointment ended. Documentation saved.',
      'errorSavingDocumentation': 'Error saving documentation',
      'errorPickingImage': 'Error picking image',
      'errorLoadingPatientId': 'Error loading patient ID',
      'useEndAppointmentToSave':
          'Use "End Appointment" to save all documentation',
      'documentsFinalizeHint':
          'Final notes and PDFs are saved when you end the appointment. New uploads appear here immediately.',
      'documentsEmptyHint':
          'Uploaded files and scans will appear here so you can open them during the visit.',
      'consultationScheduleLine': '{start} – {end} • Consultation',
      'patientIdLabel': 'ID #{id}',
      'patientAgeYears': '{age} years',
      'appointmentStatusRequested': 'Requested',
      'appointmentStatusConfirmed': 'Confirmed',
      'appointmentStatusCancelled': 'Cancelled',
      'appointmentStatusCompleted': 'Completed',
      'appointmentStatusInProgress': 'In progress',
      'docSectionLaboratory': 'Laboratory',
      'docSectionImaging': 'Imaging',
      'docSectionClinical': 'Clinical & prescriptions',
      'docSectionForms': 'Forms & notes',
      'docSectionPrivate': 'Private',
      'docSectionOther': 'Other',
      'docSectionUncategorized': 'Uncategorized',
      'consultationDocumentsDropHint':
          'Drop files here or tap to upload (saved as a medical document)',
      'consultationDocumentsUploading': 'Uploading…',
      'consultationUploadNoBytes':
          'Could not read file data. Try a smaller file or another format.',
      'consultationUploadFailed': 'Upload failed',
      // Success snack uses [consultationUploadSuccess] (proper plurals); kept for reference tools.
      'consultationUploadSuccess': 'Uploaded {count} file(s).',
      'soapNotesSectionTitle': 'Structured notes (SOAP)',
      'soapNotesSectionSubtitle':
          'Optional sections — included in the saved consultation PDF.',
      'soapSubjective': 'Subjective',
      'soapObjective': 'Objective',
      'soapAssessment': 'Assessment',
      'soapPlan': 'Plan',
      'consultationFocusModeTooltip': 'Focus mode (large notes)',
      'consultationFocusModeTitle': 'Consultation notes',
      'typeANote': 'Type a note',
      'appointmentDocumentation': 'Appointment Documentation',
      'errorOpeningDocument': 'Error opening document',
      'cannotOpenDocumentUrl': 'Cannot open document URL',
      'errorSaving': 'Error saving',
      'openRoom': 'Open Room',
      'chronicDiseaseUpdated': 'Chronic disease updated',
      // Chronic disease names (for Patient screen dropdown and display)
      'chronicDisease_none': 'None',
      'chronicDisease_diabetesType1': 'Diabetes (Type 1)',
      'chronicDisease_diabetesType2': 'Diabetes (Type 2)',
      'chronicDisease_hivAids': 'HIV/AIDS',
      'chronicDisease_hypertension': 'Hypertension',
      'chronicDisease_heartDisease': 'Heart Disease',
      'chronicDisease_chronicKidneyDisease': 'Chronic Kidney Disease',
      'chronicDisease_chronicLiverDisease': 'Chronic Liver Disease',
      'chronicDisease_asthma': 'Asthma',
      'chronicDisease_copd': 'COPD',
      'chronicDisease_cancer': 'Cancer',
      'chronicDisease_epilepsy': 'Epilepsy',
      'chronicDisease_multipleSclerosis': 'Multiple Sclerosis',
      'chronicDisease_parkinsonsDisease': 'Parkinson\'s Disease',
      'chronicDisease_rheumatoidArthritis': 'Rheumatoid Arthritis',
      'chronicDisease_lupus': 'Lupus',
      'chronicDisease_crohnsDisease': 'Crohn\'s Disease',
      'chronicDisease_ulcerativeColitis': 'Ulcerative Colitis',
      'chronicDisease_hemophilia': 'Hemophilia',
      'chronicDisease_sickleCellDisease': 'Sickle Cell Disease',
      'chronicDisease_thalassemia': 'Thalassemia',
      'chronicDisease_other': 'Other',
      'failedToUpdate': 'Failed to update',
      'errorLoadingSlots': 'Error loading slots',
      'id': 'ID',

      // Form 025-2 translations
      'form0252': 'Form 025-2',
      'form0252MedicalDocument': '025-2 raqamli tibbiy hujjat',
      'patientId': 'Patient ID',
      'job': 'Job',
      'diagnosis': 'Diagnosis',
      'complaints': 'Complaints',
      'otherIllnessesAndComplications': 'Other illnesses and complications',
      'moreDetailsOnAbove': 'More details on above',
      'visualCheckup': 'Visual checkup',
      'occlusionBiteType': 'Occlusion / Bite type',
      'oralCavityCondition':
          'Condition of the oral cavity, gums, alveoli, palate, and oral mucosa',
      'xrayLabExaminationData': 'X-ray and laboratory examination data',
      'treatment': 'Treatment',
      'treatmentResultProgress': 'Treatment result (progress/dynamics)',
      'recommendationsInstructions': 'Recommendations / Instructions',
      'returnVisits': 'Return visits',
      'clinicalFindingsConclusion': 'Clinical findings & conclusion',
      'doctorsSurname': 'Doctor\'s surname',
      'noReturnVisitsAddedYet': 'No return visits added yet',
      'addReturnVisit': 'Add return visit',
      'saveForm': 'Save Form',
      'patientFormSignatureSectionTitle': 'Patient signature (form 025-2)',
      'requestPatientFormSignature': 'Request patient signature',
      'patientSignaturePending':
          'A signature request was sent. The patient is notified in the mobile app.',
      'patientSignatureRequestSent': 'The patient has been notified to review and sign the form.',
      'patientFormSignatureReceived': 'Patient signature received.',
      'patientFormSignedAtPrefix': 'Signed at',
      'patientFormSaveAgainToRefreshPdf':
          'Save the form again to refresh the PDF so it includes the patient signature.',
      'dentalChart': 'Dental Chart',
      'icd10SearchHint': 'Search ICD-10 code or title',
      'speakToType': 'Speak to type',
      'speechToTextRequiresPro':
          'Speech-to-text requires a Pro subscription.',
      'transcribing': 'Transcribing…',
      'transcriptionAdded': 'Transcription added',
      'noSpeechDetected': 'No speech detected',
      'dentalLegend':
          'Legend: K=caries, P=filling, pulpitis, periodontitis, crown, post, missing, prosthetic.',
      'toothMap': 'Tooth map',
      'toothMapState': 'Tooth map',
      'willBeSetAutomaticallyOnSave': 'Will be set automatically on save',
      'couldNotGetAddressDetails':
          'Could not get address details. Please try selecting a different location.',
      'errorGettingCurrentLocation': 'Error getting current location',
      'germany': 'Germany',
      'uzbekistan': 'Uzbekistan',
      'usa': 'USA',
      'otherCountry': 'Other',
      'russian': 'Russian',
      'german': 'German',
      'selectProfession': 'Select Profession',
      'searchProfession': 'Search profession...',
      'noProfessionsFound': 'No professions found',
      'region': 'Region',
      'district': 'District',
      'postalCode': 'Postal Code',
      'streetAddress': 'Street Address',
      'personalInformation': 'Personal Information',
      'workplaceInformation': 'Workplace Information',
      'clinicOrWorkplaceName': 'Clinic / Workplace Name',
      'enterClinicOrWorkplaceName': 'Enter your clinic or workplace name',
      'enterStreetAddress': 'Enter street address, building name, floor, etc.',
      'streetAddressHelper':
          'You can edit this field to add building details, floor, room number, etc.',

      // Schedule
      'setupYourSchedule': 'Setup your Schedule',
      'selectWorkingDaysAndDefineSlots':
          'Select working days and define bookable time slots for your patients.',
      'scheduleValidFrom': 'Schedule valid from:',
      'scheduleValidUntil': 'Schedule valid until:',
      'existingCalendarPeriods': 'Existing calendar periods',
      'newPeriodMustNotOverlap':
          'A new period must not overlap any existing period.',
      'scheduleValidFromNew': 'New period from',
      'selectScheduleStartDate': 'Select schedule start date',
      'selectScheduleEndDate': 'Select schedule end date',
      'scheduleSaved': 'Schedule saved!',
      'errorWhileSaving': 'Error while saving',
      'scheduleOverlapsExisting': 'Schedule overlaps with existing schedule',
      'existingSchedule': 'Existing schedule',
      'newScheduleMustStartAfter': 'New schedule must start after',
      'newScheduleMustBeBeforeOrAfter':
          'New schedule must be entirely before (end by) or entirely after (start from)',
      'before': 'before',
      'orAfter': 'or after',
      'failedToLoadSchedule': 'Failed to load schedule',
      'failedToLoadRules': 'Failed to load rules',
      'unauthorizedPleaseLoginAgain': 'Unauthorized: please login again.',
      'endTimeMustBeAfterStartTime': 'End time must be after start time.',
      'thisTimeOverlapsExistingSlot': 'This time overlaps an existing slot.',
      'monday': 'Monday',
      'tuesday': 'Tuesday',
      'wednesday': 'Wednesday',
      'thursday': 'Thursday',
      'friday': 'Friday',
      'saturday': 'Saturday',
      'sunday': 'Sunday',
      'daySlots': 'slots',
      'noSlotsYet': 'No slots yet',
      'timePeriod': 'Time Period',
      'slotTimeframe': 'Slot timeframe',
      'minutes': 'minutes',
      'start': 'Start',
      'today': 'Today',
      'noAppointmentsToday': 'No appointments today',
      'scheduleIsClear': 'Your schedule is clear',
      'allPatients': 'All patients',
      'askShifaAi': 'Ask Shifa AI',
      'aiWillRespondHere': 'AI will respond here…',
      'fromShifaAi': 'From Shifa AI',
      'previous': 'Previous',
      'next': 'Next',
      'addToNotes': 'Add to notes',
      'addedToNotes': 'Added to notes',
      'fromLast0252Form': 'From last 025-2 form',
      'expandScheduleForDates': 'Expand schedule for specific dates',
      'expandScheduleHint':
          'Add extra hours after your existing schedule (e.g. 5PM–11PM). You cannot override already defined slots.',
      'fromDate': 'From date',
      'toDate': 'To date',
      'addExpansion': 'Add expansion',
      'noDateSpecificRules': 'No date-specific expansions yet.',
      'expansionAdded': 'Schedule expansion added.',
      'expandOnlyAfterExisting':
          'Start time must match when your existing schedule ends. You can only add slots after that.',
      'failedToLoadPatients': 'Failed to load patients. Please try again.',
    },
    'uz': {
      // Common
      'appName': 'Shifa Doctor',
      'loading': 'Yuklanmoqda...',
      'error': 'Xato',
      'retry': 'Qayta urinish',
      'unauthorized': 'Ruxsat berilmagan. Iltimos qaytadan kiring.',
      'networkError': 'Tarmoq xatosi. Internetni tekshiring.',
      'requestTimeout': 'So\'rov vaqti tugadi. Qayta urinib ko\'ring.',
      'accessDenied': 'Kirish taqiqlangan',
      'notFound': 'Resurs topilmadi',
      'serverError': 'Server xatosi. Keyinroq urinib ko\'ring.',
      'somethingWentWrong': 'Nimadir noto\'g\'ri ketdi',
      'cancel': 'Bekor qilish',
      'save': 'Saqlash',
      'delete': 'O\'chirish',
      'edit': 'Tahrirlash',
      'back': 'Orqaga',
      'next': 'Keyingi',
      'complete': 'Tugallandi',
      'submit': 'Yuborish',
      'close': 'Yopish',
      'yes': 'Ha',
      'no': 'Yo\'q',
      'ok': 'OK',
      'confirm': 'Tasdiqlash',
      'discard': 'Yopish',
      'search': 'Qidirish',
      'filter': 'Filtr',
      'apply': 'Qo\'llash',
      'saveDraftNote': 'Qoralama Qayd Sifatida Saqlash',
      'newSession': 'Yangi sessiya',
      'draftActions': 'Qoralama amallari',
      'draftSavedAsConsultationNote':
          'Qoralama konsultatsiya qayd sifatida saqlandi',
      'failedToSaveDraft': 'Qoralamani saqlash amalga oshmadi',
      'refresh': 'Yangilash',
      'noData': 'Ma\'lumot mavjud emas',
      'required': 'Majburiy',
      'doctor': 'Shifokor',
      'patient': 'Bemor',
      'admin': 'Administrator',
      'paymentsOpsTitle': 'To\'lov operatsiyalari',
      'failedToLoadFailedWebhooks':
          'Muvaffaqiyatsiz webhooklarni yuklab bo\'lmadi: {{error}}',
      'noFailedOrUnprocessedStripeWebhooks':
          'Muvaffaqiyatsiz yoki qayta ishlanmagan Stripe webhook hodisalari yo\'q.',
      'selectedCount': '{{count}} tanlandi',
      'retrySelected': 'Tanlanganlarni qayta urinish',
      'retrying': 'Qayta urinilmoqda...',
      'statusFailed': 'XATO',
      'statusUnprocessed': 'QAYTA ISHLANMAGAN',
      'eventIdLabel': 'eventId: {{eventId}}',
      'createdLabel': 'yaratilgan: {{created}}',
      'retryMetaLine':
          'retryCount: {{retryCount}} · lastRetryAt: {{lastRetryAt}} · retriedByAdminUserId: {{retriedByAdminUserId}}',
      'notAvailableShort': 'Mavjud emas',
      'retryWebhookEventTitle': 'Webhook hodisasini qayta urinishmi?',
      'retryWebhookEventBody':
          'Bu saqlangan Stripe webhook payloadini qayta ishlaydi.\n\n'
              'eventType: {{eventType}}\n'
              'eventId: {{eventId}}',
      'retrySelectedWebhookEventsTitle':
          'Tanlangan webhook hodisalarini qayta urinishmi?',
      'retrySelectedWebhookEventsBody':
          '{{count}} ta webhook hodisasini qayta urinish arafasidasiz. Har bir tanlangan hodisa saqlangan payload orqali qayta ijro etiladi.',
      'webhookRetriedSuccessfully':
          'Webhook muvaffaqiyatli qayta ishlatildi.',
      'retryStillFailing':
          'Qayta urinish bajarildi, lekin hali ham xato qaytmoqda.',
      'bulkRetryComplete':
          'Ommaviy qayta urinish yakunlandi: {{successCount}} muvaffaqiyatli, {{failCount}} xato.',
      'paymentLabel': 'TO\'LOV: {{status}}',
      'paymentUnknown': 'Noma\'lum',
      'paymentStateRaw': '{{state}}',
      'paymentPaid': 'To\'langan',
      'paymentPending': 'Kutilmoqda',
      'paymentFailed': 'Xato',
      'paymentNotRequired': 'Talab qilinmaydi',
      'appointmentPlaceLockedHint':
          'Band qilingan joy ma\'lumot xarakterida va bu yerda o\'zgartirib bo\'lmaydi.',
      'encouragePayment': 'Bemorga to\'lovni eslatish',
      'paymentReminderSent': 'Eslatma bemorga yuborildi',
      'paymentReminderFailed': 'Eslatma yuborilmadi: {{error}}',

      // Navigation
      'chat': 'Suhbat',
      'home': 'Bosh sahifa',
      'calendar': 'Taqvim',
      'patients': 'Bemorlar',
      'tasks': 'Vazifalar',
      'profile': 'Profil',
      'signOut': 'Chiqish',
      'signOutConfirm': 'Chiqishni xohlaysizmi?',

      // Auth
      'login': 'Kirish',
      'signIn': 'Tizimga kirish',
      'phoneOrEmail': 'Telefon raqami yoki Email',
      'emailOrPhone': 'Email yoki telefon raqami',
      'password': 'Parol',
      'forgotPassword': 'Parolni unutdingizmi?',
      'createAccount': 'Hisob yaratish',
      'adminPanel': 'Admin panel',
      'createAdminUser': 'Admin foydalanuvchi yaratish',
      'createAdminUserDescription':
          'Yangi admin foydalanuvchi yarating. Ular admin panelga kira oladi.',
      'adminUserCreated': 'Admin foydalanuvchi muvaffaqiyatli yaratildi',
      'enterValidEmail': 'To\'g\'ri email kiriting',
      'passwordMinLength': 'Parol kamida 8 ta belgidan iborat bo\'lishi kerak',
      'enterEmailOrPhone': 'Email yoki telefon raqamini kiriting',
      'verify': 'Tasdiqlash',
      'oneTimeKey': 'Bir martalik kalit',
      'pleaseEnterOneTimeKey': 'Iltimos, bir martalik kalitingizni kiriting.',
      'keyVerified': 'Kalit tasdiqlandi',
      'firstName': 'Ism',
      'lastName': 'Familiya',
      'emailOptional': 'Email (ixtiyoriy)',
      'confirmPassword': 'Parolni tasdiqlang',
      'enterFirstName': 'Ismni kiriting',
      'enterLastName': 'Familiyani kiriting',
      'enterPhoneNumber': 'Telefon raqamini kiriting',
      'optional': 'Ixtiyoriy',
      'pleaseVerifyInvitationKeyFirst':
          'Iltimos, avval taklifnoma kalitini tasdiqlang.',
      'accountInformation': 'Hisob ma\'lumotlari',
      'dateOfBirth': 'Tug\'ilgan sana',
      'clinic': 'Klinika',
      'profession': 'Kasb',
      'generalPractitioner': 'Umumiy amaliyotchi',
      'cardiologist': 'Kardiolog',
      'dermatologist': 'Dermatolog',
      'pediatrician': 'Pediatr',
      'accountCreatedPleaseSignIn': 'Hisob yaratildi! Iltimos, tizimga kiring.',
      'existingPatientCreatingDoctorAccount':
          'Ushbu ma\'lumotlar bilan bemor allaqachon mavjud. Ushbu bemor uchun shifokor hisobini yaratamiz.',
      'confirmRegistration': 'Ro\'yxatdan o\'tishni tasdiqlash',
      'signInToManageSystem': 'Tizimni boshqarish uchun kiring',
      'goToDoctorLogin': 'Shifokor kirishiga o\'tish',
      'adminEmailVerificationSent':
          '6 xonali tasdiqlash kodi {hint} manziliga yuborildi. Kirishni tugatish uchun quyiga kiriting.',
      'adminEnterVerificationCode': 'Tasdiqlash kodi',
      'adminVerifyAndSignIn': 'Tasdiqlash va kirish',
      'adminResendVerificationCode': 'Kodni qayta yuborish',
      'adminChangeAccount': 'Boshqa hisob',
      // Phone OTP & Forgot password
      'signInWithPhone': 'Telefon raqami bilan kirish',
      'signInWithEmail': 'Email orqali kirish',
      'enterEmailForOtp': 'Tasdiqlash kodi olish uchun email manzilingizni kiriting.',
      'enterEmail': 'Email manzilini kiriting',
      'otpSentToEmail': '6 xonali kod {email} manziliga yuborildi. Pochtangizni tekshiring.',
      'continue': 'Davom etish',
      'enterOtp': 'OTP kiriting',
      'resendCode': 'Kodni qayta yuborish',
      'resendCodeIn': 'Kodni {{time}} da qayta yuborish',
      'tooManyRequests': 'Juda ko\'p so\'rovlar. Keyinroq urinib ko\'ring.',
      'invalidOtp': 'Noto\'g\'ri yoki muddati o\'tgan kod.',
      'resetPassword': 'Parolni tiklash',
      'passwordMismatch': 'Parollar mos kelmadi.',
      'passwordTooWeak':
          'Parol kamida 8 belgidan, 1 bosh harf va 1 raqamdan iborat bo\'lishi kerak.',
      'accessRestricted': 'Faqat shifokorlar uchun ruxsat.',
      'accountPending': 'Hisobingiz tasdiq kutyapti.',
      'accountBlocked': 'Hisobingiz bloklangan.',
      'otpSent': 'Tasdiqlash kodi yuborildi.',
      'otpResent': 'Kod qayta yuborildi.',
      'otpResendHint':
          'Yangi kod olish uchun orqaga qayting va Davom etishni bosing.',
      'detecting': 'Aniqlanmoqda…',
      'practiceTimezonePlaceholder':
          'Amaliyot vaqt zonasi (masalan, Europe/Berlin)',

      // Profile
      'editProfile': 'Profilni tahrirlash',
      'language': 'Til',
      'settings': 'Sozlamalar',
      'english': 'Inglizcha',
      'uzbek': 'O\'zbekcha',
      'uzbekCyrillicMenu': 'Ўзбекча (кирилл)',
      'selectLanguage': 'Tilni tanlash',
      'languageChanged': 'Til muvaffaqiyatli o\'zgartirildi',
      'biography': 'Biografiya',
      'services': 'Xizmatlar',
      'certificates': 'Sertifikatlar',
      'telegram': 'Telegram',
      'instagram': 'Instagram',
      'uploadCertificate': 'Sertifikat yuklash',
      'addService': 'Xizmat qo\'shish',
      'removeService': 'Xizmatni olib tashlash',
      'openServicesPricingToManageEntries':
          'Yozuvlarni boshqarish uchun Xizmatlar va narxlar sahifasini oching',
      'enterService': 'Xizmat nomini kiriting',
      'servicesPricing': 'Xizmatlar va narxlar',
      'servicesPricingSubtitle':
          'Xizmat nomlari, narxlari, valyutalari va tavsiflarini boshqaring',
      'servicesPricingPanelDesc':
          'Tavsif va ko\'p valyutali narxlarga ega hisob-fakturali xizmatlarni belgilang.',
      'openServicesPricing': 'Xizmatlar va narxlarni ochish',
      'newService': 'Yangi xizmat',
      'editService': 'Xizmatni tahrirlash',
      'serviceTitleLabel': 'Nomi',
      'serviceDescriptionLabel': 'Tavsif',
      'servicePriceLabel': 'Narx miqdori (masalan, 25.00)',
      'serviceCurrencyLabel': 'Valyuta (EUR/UZS/USD)',
      'serviceFreeConsultation': 'Bepul maslahat (video)',
      'serviceFreeConsultationHint':
          'Bemorlar ushbu xizmatni video qabul uchun tanlasalar, to\'lovsiz darhol tasdiqlanadi.',
      'serviceGroupsTitle': 'Xizmat guruhlari',
      'serviceGroupsHint':
          'Xizmatlarni profilda tartiblash uchun guruhlardan foydalaning. Kichik tartib raqami birinchi chiqadi.',
      'serviceGroupLabel': 'Guruh',
      'serviceGroupNone': 'Guruhsiz',
      'servicePricesSection':
          'Narxlar: barcha joylar uchun standart qator qo\'shing yoki alohida klinikalarni almashtirish uchun.',
      'priceScopeLabel': 'Qo\'llaniladi',
      'priceScopeAllLocations': 'Barcha joylar (standart)',
      'addPriceRow': 'Narx qo\'shish',
      'editGroup': 'Guruhni tahrirlash',
      'groupName': 'Guruh nomi',
      'sortOrder': 'Tartib raqami',
      'newGroup': 'Yangi guruh',
      'addGroup': 'Guruh qo\'shish',
      'profileInformation': 'Profil ma\'lumotlari',
      'contactDetails': 'Aloqa ma\'lumotlari',
      'paymentAndInvoicing': 'To\'lov va hisob-faktura',
      'profileInformationSaved': 'Profil ma\'lumotlari saqlandi',
      'contactDetailsSaved': 'Aloqa ma\'lumotlari saqlandi',
      'paymentAndInvoicingSaved': 'To\'lov va hisob-faktura saqlandi',
      'settingsSaved': 'Sozlamalar saqlandi',
      'passwordUpdatedSuccessfully': 'Parol muvaffaqiyatli yangilandi',
      'newPasswordConfirmationMismatchError':
          'Yangi parol va tasdiqlash mos kelmaydi',
      'currentPasswordIsRequired': 'Joriy parol talab qilinadi',
      'pleaseConfirmNewPasswordError': 'Iltimos, yangi parolni tasdiqlang',
      'currentPassword': 'Joriy parol',
      'newPassword': 'Yangi parol',
      'confirmNewPassword': 'Yangi parolni tasdiqlash',
      'billingName': 'Hisob-faktura nomi',
      'billingEmail': 'Hisob-faktura email',
      'on': 'Yoqilgan',
      'off': 'O\'chirilgan',
      'fullName': 'F.I.O', // Form 025-2 uses F.I.O instead of To'liq ism
      'country': 'Mamlakat',
      'twoFactorAuthentication': 'Ikki bosqichli autentifikatsiya',
      'encryptedDocuments': 'Shifrlangan hujjatlar',
      'updateOrChangeSchedule': 'Jadvalni yangilang yoki o\'zgartiring',
      'changeOrResetPassword': 'Parolni o\'zgartiring yoki tiklang',
      'settingsSubtitle':
          'Mamlakat, Til, Ikki bosqichli autentifikatsiya, Shifrlangan hujjatlar',
      'extendedProfileSubtitle':
          'Biografiya, Xizmatlar, Sertifikatlar, Ijtimoiy tarmoqlar',
      'phone': 'Telefon',
      'yourName': 'Sizning ismingiz',

      // Home
      'dashboard': 'Boshqaruv paneli',
      'todayAppointments': 'Bugungi uchrashuvlar',
      'upcomingAppointments': 'Kutilayotgan uchrashuvlar',
      'recentPatients': 'So\'nggi bemorlar',
      'analytics': 'Tahlillar',

      // Calendar
      'appointments': 'Uchrashuvlar',
      'freeSlots': 'Bo\'sh vaqtlar',
      'date': 'Sana',
      'time': 'Vaqt',
      'duration': 'Davomiylik',
      'place': 'Joy',
      'changeSlot': 'Vaqtni o\'zgartirish',
      'cancelAppointment': 'Uchrashuvni bekor qilish',
      'cancelConfirm': 'Ushbu uchrashuvni bekor qilishni xohlaysizmi?',
      'appointmentCancelled': 'Uchrashuv muvaffaqiyatli bekor qilindi',
      'pastAppointmentNoChange':
          'O\'tgan uchrashuvlarni o\'zgartirish yoki bekor qilish mumkin emas.',
      'pastSlotCannotAssign': 'Bu vaqt o\'tgan. Bemorni tayinlash mumkin emas.',
      'slotChanged': 'Vaqt muvaffaqiyatli o\'zgartirildi',
      'makeAppointment': 'Randevu yaratish',
      'selectDate': 'Sanani tanlash',
      'selectTime': 'Vaqtni tanlash',
      'availableSlots': 'Mavjud vaqtlar',
      'appointmentType': 'Uchrashuv turi',
      'noSlotsAvailable': 'Mavjud vaqtlar yo\'q',
      'noAppointments': 'Uchrashuvlar yo\'q',
      'noFreeSlots': 'Bo\'sh vaqtlar yo\'q',
      'monthJanuary': 'Yanvar',
      'monthFebruary': 'Fevral',
      'monthMarch': 'Mart',
      'monthApril': 'Aprel',
      'monthMay': 'May',
      'monthJune': 'Iyun',
      'monthJuly': 'Iyul',
      'monthAugust': 'Avgust',
      'monthSeptember': 'Sentabr',
      'monthOctober': 'Oktabr',
      'monthNovember': 'Noyabr',
      'monthDecember': 'Dekabr',

      // Patients
      'patient': 'Bemor',
      'patientList': 'Bemorlar ro\'yxati',
      'searchPatients': 'Bemorlarni qidirish...',
      'noPatientsFound': 'Bemorlar topilmadi',
      'patientDetails': 'Bemor tafsilotlari',
      'generalInformation': 'Umumiy ma\'lumotlar',
      'documents': 'Hujjatlar',
      'chronicDisease': 'Surunkali kasallik',
      'selectChronicDisease': 'Surunkali kasallikni tanlash',
      'noChronicDisease': 'Surunkali kasallik yo\'q',
      'chronicDiseaseWarning':
          'Ogohlantirish: Bu bemorda og\'ir/surunkali kasallik bor. Iltimos, qo\'shimcha ehtiyot bo\'ling.',
      'createTask': 'Vazifa yaratish',
      'assignResult': 'Natijani tayinlash',
      'startAppointment': 'Uchrashuvni boshlash',
      'phoneNumber': 'Telefon raqami',
      'phoneNumberRequired': 'Telefon raqami kiritilishi shart',
      'email': 'Email',
      'address': 'Manzili', // Form 025-2 uses "Manzili"
      'location': 'Joylashuv',
      'latitude': 'Kenglik',
      'longitude': 'Uzunlik',
      'getCurrentLocation': 'Joriy joylashuvni olish',
      'saveLocation': 'Joylashuvni saqlash',
      'locationSaved': 'Joylashuv saqlandi',
      'invalidCoordinates': 'Iltimos, to\'g\'ri koordinatalarni kiriting',
      'locationFeatureComingSoon':
          'Joylashuv funksiyasi tez orada qo\'shiladi. Iltimos, koordinatalarni qo\'lda kiriting.',
      'selectLocationOnMap': 'Xaritada joylashuvni tanlash',
      'currentLocation': 'Joriy joylashuv',
      'addressFromCoordinates': 'Koordinatalardan manzil',
      'coordinatesFromAddress': 'Manzildan koordinatalar',
      'enterAddressToFindCoordinates':
          'Koordinatalarni topish uchun manzilni kiriting',
      'addressFound': 'Manzil topildi',
      'addressNotFound': 'Manzil topilmadi',
      'pleaseEnterAddress': 'Iltimos, manzilni kiriting',
      'city': 'Shahar',
      'region': 'Viloyat',
      'district': 'Tuman',
      'postalCode': 'Pochta indeksi',
      'streetAddress': 'Ko\'cha manzili',
      'locationServicesDisabled':
          'Joylashuv xizmatlari o\'chirilgan. Iltimos, ularni yoqing.',
      'locationPermissionDenied': 'Joylashuv ruxsatlari rad etilgan.',
      'locationPermissionDeniedForever':
          'Joylashuv ruxsatlari doimiy ravishda rad etilgan. Iltimos, sozlamalarda yoqing.',
      'selectedLocation': 'Tanlangan joylashuv',
      'selectLocation': 'Joylashuvni tanlang',
      'primary': 'Asosiy',
      'manage': 'Boshqarish',
      'label': 'Nomi',
      'manageLocations': 'Joylashuvlarni boshqarish',
      'addLocation': 'Joylashuv qo\'shish',
      'editLocation': 'Joylashuvni tahrirlash',
      'deleteLocation': 'Joylashuvni o\'chirish',
      'deleteLocationConfirm':
          '"{label}" joylashuvini o\'chirasizmi? Bu joylashuvdagi jadval qoidalari va uchrashuvlar avval olib tashlanishi kerak.',
      'noLocationsYet':
          'Hali joylashuvlar yo\'q. Birinchisini yaratish uchun "Joylashuv qo\'shish"ni bosing.',
      'labelRequired': 'Nomi majburiy',
      'exampleMainClinic': 'masalan: Asosiy klinika',
      'setAsPrimary': 'Asosiy qilib belgilash',
      'addFirstLocationHint':
          'Jadvalni tartibga solish uchun kamida bitta amaliyot joylashuvini qo\'shing.',
      'copyFromPreviousDay': 'Oldingi kundan nusxa olish',
      'copyFromAnotherDay': 'Boshqa kundan nusxa olish',
      'copyScheduleFromDay': 'Jadvalni qaysi kundan nusxa olinsin?',
      'noPreviousDayScheduleToCopy':
          'Oldingi kunda nusxa olish uchun jadval yo\'q.',
      'scheduleCopiedFromPreviousDay':
          'Jadval oldingi kundan nusxa olindi.',
      'noSourceDaysToCopyFrom':
          'Nusxa olish uchun boshqa kunlarda jadval yo\'q.',
      'failedToCopySchedule':
          'Tanlangan kundan jadval nusxalanmadi.',
      'scheduleCopiedFromDay': 'Jadval {day} dan nusxa olindi.',
      'current': 'Joriy',
      'birthDate': 'Tug\'ilgan sana',
      'gender': 'Jins',
      'male': 'Erkak',
      'female': 'Ayol',
      'other': 'Boshqa',

      // Tasks
      'remoteCareTasks': 'Masofaviy parvarish vazifalari',
      'remoteCareTasksSubtitle':
          'Bemor kuzatuvlarini nazorat qiling va boshqaring',
      'createRemoteCareTask': 'Masofaviy parvarish vazifasi yaratish',
      'taskTemplates': 'Vazifa shablonlari',
      'useTemplate': 'Shablonlardan foydalanish',
      'activeTasks': 'Faol vazifalar',
      'completedTasks': 'Bajarilgan vazifalar',
      'overdueTasks': 'Muddati o\'tgan vazifalar',
      'searchTasksOrPatients': 'Vazifa yoki bemorlarni qidirish',
      'createFirstRemoteTask': 'Birinchi masofaviy vazifani yarating',
      'taskProgress': 'Vazifa jarayoni',
      'perDay': 'kuniga',
      'taskName': 'Vazifa nomi',
      'description': 'Tavsif',
      'category': 'Kategoriya',
      'vital': 'Hayotiy',
      'exercise': 'Mashq',
      'medication': 'Dori',
      'taskOther': 'Boshqa',
      'timesPerDay': 'Kuniga necha marta',
      'morningTime': 'Ertalabki vaqt',
      'afternoonTime': 'Tushdan keyingi vaqt',
      'eveningTime': 'Kechki vaqt',
      'startDate': 'Boshlanish sanasi',
      'startTime': 'Boshlanish vaqti',
      'intervalBetweenTasks': 'Vazifalar orasidagi interval',
      'everyNHours': 'Har %d soatda',
      'every1Hour': 'Har 1 soatda',
      'slotsPreviewLabel': 'Kunlik vaqtlar',
      'slotsPreviewClipped':
          '%d ta vaqt yarim tungacha sig\'maydi. Kichikroq interval yoki ertaroq boshlanish vaqtini tanlang.',
      'scheduleMode': 'Jadval turi',
      'scheduleModeEvenSpacing': 'Teng oraliq',
      'scheduleModeCustomTimes': 'Maxsus vaqtlar',
      'customTimesLabel': 'Kunlik vaqtlar',
      'customTimesHint':
          'Notekis jadvallarni qo\'llab-quvvatlash uchun har bir vaqtni alohida belgilang.',
      'customTimesEmpty': 'Hali vaqtlar qo\'shilmagan — quyida qo\'shing.',
      'customTimesAddSlot': 'Vaqt qo\'shish',
      'customTimesAddAtLeastOne': 'Kamida bitta vaqt qo\'shing',
      'customTimesCount': 'Kuniga %d ta vaqt',
      'edit': 'Tahrirlash',
      'remove': 'O\'chirish',
      'endDate': 'Tugash sanasi',
      'durationDays': 'Davomiylik (kunlar)',
      'useEndDate': 'Tugash sanasidan foydalanish (aks holda davomiylik)',
      'inputType': 'Kirish turi',
      'numeric': 'Raqamli',
      'text': 'Matn',
      'boolean': 'Ha/Yo\'q',
      'inputLabel': 'Kirish yorlig\'i',
      'notesRequired': 'Izoh talab qilinadi',
      'notesLabel': 'Izoh yorlig\'i',
      'taskCreated': 'Vazifa muvaffaqiyatli yaratildi',
      'taskUpdated': 'Vazifa muvaffaqiyatli yangilandi',
      'taskCancelled': 'Vazifa muvaffaqiyatli bekor qilindi',
      'failedToCreateTask': 'Vazifa yaratishda xatolik',
      'failedToUpdateTask': 'Vazifani yangilashda xatolik',
      'selectPatient': 'Bemorni tanlash',
      'tapToSearch': 'Qidirish va tanlash uchun bosing',
      'searchByNameOrId': 'Ism yoki ID bo\'yicha qidirish',
      'taskDetails': 'Vazifa tafsilotlari',
      'progress': 'Jarayon',
      'checkInCompleted': 'Bajarildi',
      'pending': 'Kutilmoqda',
      'missed': 'O\'tkazib yuborilgan',
      'checkIns': 'Tekshiruvlar',
      'checkInDetails': 'Tekshiruv tafsilotlari',
      'scheduled': 'Rejalashtirilgan',
      'submittedAt': 'Yuborilgan vaqt',
      'awaitingSubmission': 'Yuborish kutilmoqda',
      'noSubmissionReceived': 'Yuborish qabul qilinmadi',
      'status': 'Holat',
      'active': 'Faol',
      'taskCompleted': 'Bajarildi',
      'expired': 'Muddati o\'tgan',
      'taskStatusCancelled': 'Bekor qilingan',
      'draft': 'Qoralama',
      'all': 'Barchasi',
      'noTasksFound': 'Vazifalar topilmadi',
      'taskDescription': 'Bemorga ko\'rsatiladigan vazifa tavsifi',
      'enterTaskName': 'Vazifa nomini kiriting',
      'enterInputLabel': 'Masalan: Qon bosimi, Og\'irlik (kg)',
      'enterNotesLabel': 'Masalan: Qo\'shimcha izohlar',
      'notSet': 'Belgilanmagan',

      // Documents
      'uploadDocument': 'Hujjat yuklash',
      'documentTitle': 'Hujjat nomi',
      'enterDocumentTitle': 'Hujjat nomini kiriting',
      'selectFile': 'Faylni tanlash',
      'documentUploaded': 'Hujjat muvaffaqiyatli yuklandi',
      'uploadFailed': 'Yuklash muvaffaqiyatsiz',
      'noDocuments': 'Hujjatlar mavjud emas',
      // Document categories / visibility
      'documentCategoryLabel': 'Hujjat turi',
      'documentCategorySelect': 'Turini tanlang',
      'documentCategoryHint':
          'Bemorning barcha shifokorlariga ko\'rinishi uchun tibbiy natija turini tanlang. Ichki/maxfiy turlar faqat sizga ko\'rinadi.',
      'documentCategoryGroupMedical':
          'Tibbiy natijalar (barcha shifokorlarga ko\'rinadi)',
      'documentCategoryGroupPrivate': 'Maxfiy (faqat sizga ko\'rinadi)',
      'sharedWithTeamTooltip':
          'Ushbu bemorning barcha shifokorlariga ko\'rinadi',
      'documentCategory_BLOOD_TEST': 'Qon tahlili',
      'documentCategory_URINE_TEST': 'Siydik tahlili',
      'documentCategory_STOOL_TEST': 'Najas tahlili',
      'documentCategory_LAB_RESULT': 'Laboratoriya natijasi',
      'documentCategory_MRI': 'MRT',
      'documentCategory_CT_SCAN': 'KT skaneri',
      'documentCategory_XRAY': 'Rentgen',
      'documentCategory_ULTRASOUND': 'UTT',
      'documentCategory_MAMMOGRAPHY': 'Mammografiya',
      'documentCategory_ECG': 'EKG',
      'documentCategory_EEG': 'EEG',
      'documentCategory_ENDOSCOPY': 'Endoskopiya',
      'documentCategory_BIOPSY': 'Biopsiya',
      'documentCategory_PATHOLOGY': 'Patologiya',
      'documentCategory_IMAGING_OTHER': 'Boshqa tasvirlash',
      'documentCategory_PRESCRIPTION': 'Retsept',
      'documentCategory_VACCINATION_RECORD': 'Emlash hujjati',
      'documentCategory_DISCHARGE_SUMMARY': 'Chiqarish xulosasi',
      'documentCategory_REFERRAL': 'Yo\'llanma',
      'documentCategory_HOSPITAL_REPORT': 'Shifoxona xulosasi',
      'documentCategory_ALLERGY_REPORT': 'Allergiya xulosasi',
      'documentCategory_OTHER_MEDICAL': 'Boshqa tibbiy natija',
      'documentCategory_APPOINTMENT_NOTE': 'Qabul qaydlari',
      'documentCategory_REMOTE_TASK_DOCUMENT': 'Masofaviy vazifa hujjati',
      'documentCategory_FORM_025_2': '025-2 forma',
      'documentCategory_INTERNAL_NOTE': 'Ichki qayd',
      'documentCategory_OTHER_PRIVATE': 'Boshqa maxfiy hujjat',
      'couldNotLoadDocument': 'Hujjatni yuklab bo\'lmadi',
      'openDocument': 'Hujjatni ochish',
      'requestAccess': 'Kirish so\'rovini yuborish',
      'documentLocked': 'Qulflangan',
      'uploadedBy': 'Yuklangan',
      'anotherUser': 'Boshqa foydalanuvchi',
      'listRefreshed': 'Bemorlar ro\'yxati yangilandi',
      // Appointments
      'appointmentDetails': 'Uchrashuv tafsilotlari',
      'patientName': 'Bemor ismi',
      'appointmentDate': 'Uchrashuv sanasi',
      'appointmentTime': 'Uchrashuv vaqti',
      'appointmentPlace': 'Joy',
      'videoCall': 'Video qo\'ng\'iroq',
      'inClinic': 'Klinikada',
      'notes': 'Izohlar',
      'enterNotes': 'Uchrashuv izohlarini kiriting...',
      'beforeTreatment': 'Davolanishdan oldin',
      'afterTreatment': 'Davolanishdan keyin',
      'startAiNotes': 'AI yozuvlarni boshlash',
      'recordingForAiNotes': 'AI yozuvlar uchun yozuv',
      'processRecording': 'Qayta ishlash',
      'aiNotesUploaded':
          'Yozuv yuklandi. Yozuvlar bir necha daqiqada tayyor bo\'ladi.',
      'aiNotesNotReadyTryLater':
          'AI yozuvchining qaydlari hali tayyor emas. Iltimos, 30 soniyadan so\'ng qayta urinib ko\'ring.',
      'uploadPhoto': 'Rasm yuklash',
      'endAppointment': 'Uchrashuvni yakunlash',
      'appointmentEnded': 'Uchrashuv muvaffaqiyatli yakunlandi',
      'documentGenerated': 'Hujjat muvaffaqiyatli yaratildi',
      'viewDocument': 'Hujjatni ko\'rish',
      'requestSignature': 'Imzo so\'rash',
      'waitingForPatientSignature': 'Bemor imzosini kutyapmiz...',
      'patientSigned': 'Bemor imzo qo\'ydi ✓',
      'signatureRequestSent': 'Bemorga imzo so\'rovi yuborildi',

      // Chat
      'messages': 'Xabarlar',
      'typeMessage': 'Xabar yozing...',
      'send': 'Yuborish',
      'noConversations': 'Hali suhbatlar yo\'q',
      'newestFirst': 'Avval Yangilari',
      'oldestFirst': 'Avval Eskilari',
      'unreadOnlyNewest': 'Faqat O\'qilmaganlar (Avval Yangilari)',
      'unreadOnlyOldest': 'Faqat O\'qilmaganlar (Avval Eskilari)',
      'noUnreadConversations': 'O\'qilmagan suhbatlar yo\'q',
      'justNow': 'Hozir',
      'minuteAgo': '1 daqiqa oldin',
      'minutesAgo': '%s daqiqa oldin',
      'hourAgo': '1 soat oldin',
      'hoursAgo': '%s soat oldin',
      'yesterday': 'Kecha',
      'isTyping': 'yozmoqda',
      'selectConversation': 'Suhbatni tanlang',
      'searchDoctorsAndPatients': 'Shifokorlar va bemorlarni qidirish',
      'noUsersFound': 'Foydalanuvchilar topilmadi',
      'attachFile': 'Fayl biriktirish',
      'selectImage': 'Rasm tanlash',
      'takePhoto': 'Foto surat olish',
      'chooseFromGallery': 'Galereyadan tanlash',
      'recordVoice': 'Ovozli xabar yozish',
      'voiceMessage': 'Ovozli xabar',
      'voiceRecordingFinishHint':
          'Tugatgach to\'xtating — pauzalar va to\'ldiruvchi so\'zlar mumkin.',
      'cancel': 'Bekor qilish',
      'sendVoice': 'Ovozli xabarni yuborish',
      'compressingImage': 'Rasm siqilmoqda...',
      'uploadingFile': 'Fayl yuklanmoqda...',
      'errorUploadingFile': 'Fayl yuklashda xatolik',
      'errorRecordingVoice': 'Ovoz yozishda xatolik',
      'selectDocument': 'Hujjat tanlash',
      'voiceRecordingNotSupportedOnWeb':
          'Veb versiyada ovoz yozish qo\'llab-quvvatlanmaydi. Iltimos, mobil ilovadan foydalaning.',
      'microphonePermissionDenied': 'Mikrofon ruxsati rad etilgan',
      'failedToStartConversation': 'Suhbatni boshlashda xatolik',
      'failedToSendMessage': 'Xabarni yuborishda xatolik',
      'failedToStartVideoCall': 'Video qo\'ng\'iroqni boshlashda xatolik',
      'videoCallAvailableFiveMinBefore':
          'Uchrashuvdan 5 daqiqa oldin boshlashingiz mumkin.',
      'videoCallTooLateAfterOneHour':
          'Ushbu video uchrashuv tugaganiga bir soatdan ortiq vaqt o\'tdi. Faqat konsultatsiya allaqachon boshlangan bo\'lsa oching.',
      'videoCallEnded': 'Video qo\'ng\'iroq tugadi',
      'callErrorOccurred': 'Qo\'ng\'iroqda xatolik yuz berdi',
      'waitingForParticipants': 'Ishtirokchilar kutilmoqda...',
      'joinVideoCall': 'Video qo\'ng\'iroqqa qo\'shilish',
      'videoCallReady': 'Video qo\'ng\'iroq tayyor',
      'videoCallErrorTitle': 'Video qo\'ng\'iroq xatosi',
      'videoCallConnecting': 'Video qo\'ng\'iroqqa ulanmoqda...',
      'videoCallNotAvailableShort': 'Video qo\'ng\'iroq mavjud emas',
      'videoCallJoinWindowClosedMessage':
          'Video qo\'ng\'iroq tugagan. Ulanish oynasi uchrashuv tugaganidan keyin 15 daqiqa ichida yopiladi.',
      'videoCallNotYetAvailableMessage':
          'Video qo\'ng\'iroq hali mavjud emas. Uchrashuv boshlanishidan 5 daqiqa oldin ulanishingiz mumkin.',
      'videoCallPaymentRequiredMessage':
          'Ushbu video konsultatsiyaga qo\'shilishdan oldin to\'lov talab qilinadi.',
      'clickBelowToJoinCall':
          'Qo\'ng\'iroqqa qo\'shilish uchun quyidagi tugmani bosing',
      'tokenRevoked': 'Token bekor qilindi',
      'tokenRegenerated': 'Token qayta yaratildi',
      'configUpdated': 'Sozlamalar yangilandi',
      'noContact': 'Aloqa yo\'q',
      'noEmail': 'Email yo\'q',
      'noPhone': 'Telefon yo\'q',
      'userManagement': 'Foydalanuvchilarni boshqarish',
      'filterByRole': 'Rol bo\'yicha filtrlash',
      'searchUsersPlaceholder': 'Ism, raqam yoki rol bo\'yicha qidirish',
      'allRoles': 'Barcha rollar',
      'doctors': 'Shifokorlar',
      'admins': 'Adminlar',
      'filterByStatus': 'Holat bo\'yicha filtrlash',
      'enabled': 'Yoqilgan',
      'disabled': 'O\'chirilgan',
      'lastLogin': 'Oxirgi kirish',
      'unlock': 'Qulflarni ochish',
      'resetPassword': 'Parolni tiklash',
      'passwordReset': 'Parolni qayta o\'rnatish',
      'temporaryPassword': 'Vaqtinchalik parol:',
      'sharePasswordSecurely':
          'Iltimos, bu parolni foydalanuvchi bilan xavfsiz ulashing.',
      'forceLogout': 'Majburan chiqish',
      'userLoggedOut': 'Foydalanuvchi tizimdan chiqdi',
      'resetDoctorCalendar': 'Shifokor taqvimini qayta o\'rnatish',
      'confirmReset': 'Tasdiqlash',
      'doctorCalendarResetConfirm':
          'Bu shifokorning barcha uchrashuvlari va mavjud vaqtlarini butunlay o\'chiradi. Bu amalni qaytarib bo\'lmaydi. Shifokor hisobi, profili va kirish ma\'lumotlari saqlanadi; mavjudlikni qayta sozlashi kerak.',
      'doctorCalendarResetSuccessfully':
          'Shifokor taqvimi muvaffaqiyatli qayta o\'rnatildi',
      'doctorProfileIdNotFound':
          'Xato: Shifokor profil ID topilmadi. Iltimos, ro\'yxatni yangilang.',
      'tokenManagement': 'Tokenlarni boshqarish',
      'consumed': 'Ishlatilgan',
      'loadingTokens': 'Tokenlar yuklanmoqda...',
      'errorLoadingTokens': 'Tokenlarni yuklashda xatolik',
      'noTokensFound': 'Tokenlar topilmadi',
      'generateToken': 'Token yaratish',
      'expiresInDaysOptional': 'Muddati (kunlar, ixtiyoriy)',
      'notesOptional': 'Izohlar (ixtiyoriy)',
      'tokenGeneratedSuccessfully': 'Token muvaffaqiyatli yaratildi',
      'generate': 'Yaratish',
      'copyKey': 'Kalitni nusxalash',
      'keyCopiedToClipboard': 'Kalit buferga nusxalandi',
      'revoke': 'Bekor qilish',
      'regenerate': 'Qayta yaratish',
      'expires': 'Muddati',
      'systemConfiguration': 'Tizim sozlamalari',
      'editKey': 'Tahrirlash',
      'enable': 'Yoqish',
      'disable': 'O\'chirish',
      'page': 'Sahifa',
      'of': '/',
      'totalDoctors': 'Jami shifokorlar',
      'activeDoctors': 'Faol shifokorlar',
      'totalPatients': 'Jami bemorlar',
      'activeTokens': 'Faol tokenlar',
      'auditLogs': 'Audit jurnallari',
      'noLogsFound': 'Yozuvlar topilmadi',
      'logout': 'Chiqish',
      'confirmLogout': 'Chiqishni xohlaysizmi?',
      'quickActions': 'Tezkor amallar',
      'viewUsers': 'Foydalanuvchilar',
      'viewLogs': 'Jurnallar',
      'addRow': 'Qator qo\'shish',
      'removeRow': 'Qatorni olib tashlash',
      'editRow': 'Qatorni tahrirlash',
      'collapse': 'Yig\'ish',
      'expand': 'Yoyish',
      'formSavedSuccessfully': 'Forma muvaffaqiyatli saqlandi',
      'errorSavingForm': 'Formani saqlashda xatolik',
      'pdfGeneratedPrintingNotImplemented':
          'PDF yaratildi. Chop etish hali amalga oshirilmagan.',

      // Errors & Validation
      'invalidEmail': 'Iltimos, to\'g\'ri email manzil kiriting',
      'invalidPhone': 'Iltimos, to\'g\'ri telefon raqam kiriting',
      'passwordTooShort': 'Parol kamida 8 belgidan iborat bo\'lishi kerak',
      'passwordTooLong': 'Parol 128 belgidan oshmasligi kerak',
      'passwordsDoNotMatch': 'Parollar mos kelmaydi',
      'passwordRequired': 'Parol kiritilishi shart',
      'pleaseConfirmPassword': 'Iltimos, parolingizni tasdiqlang',
      'passwordRequirementMinLength': 'Kamida 8 belgi',
      'passwordRequirementMaxLength': '128 belgidan oshmasligi kerak',
      'passwordRequirementUppercase': 'Kamida bitta bosh harf',
      'passwordRequirementLowercase': 'Kamida bitta kichik harf',
      'passwordRequirementDigit': 'Kamida bitta raqam',
      'passwordRequirementSpecialChar':
          'Kamida bitta maxsus belgi (!@#\$%^&* va hokazo)',
      'fieldRequired': 'Bu maydon majburiy',
      'pleaseSelectPatient': 'Iltimos, bemorni tanlang',
      'pleaseEnterTaskName': 'Vazifa nomi majburiy',
      'unauthorized': 'Ruxsatsiz. Iltimos, qayta kiring.',
      'networkError': 'Tarmoq xatosi. Iltimos, ulanishni tekshiring.',
      'unknownError': 'Noma\'lum xatolik yuz berdi',

      // Notifications
      'notifications': 'Bildirishnomalar',
      'noNotifications': 'Bildirishnomalar yo\'q',
      'markAllAsRead': 'Barchasini o\'qilgan deb belgilash',
      'approve': 'Tasdiqlash',
      'reject': 'Rad etish',
      'approved': 'Tasdiqlandi',
      'rejected': 'Rad etildi',
      'documentAccessApproved': 'Kirish ruxsat etildi',
      'documentAccessRejected': 'Kirish so\'rovi rad etildi',
      'documentAccessRequest': 'Hujjatga kirish so\'rovi',
      'documentAccessRequestDetail':
          '{doctorName} \"{documentTitle}\" hujjatiga {patientName} bemori uchun kirish so\'radi.',
      'documentAccessApprovedDetail':
          '"{documentTitle}" hujjatiga {patientName} uchun kirish so\'rovingiz tasdiqlandi.',
      'documentAccessRejectedDetail':
          '"{documentTitle}" hujjatiga {patientName} uchun kirish so\'rovingiz rad etildi.',
      'notificationGeneric': 'Bildirishnoma',
      'somethingWentWrong': 'Nimadir xato ketti',
      'imageNotAvailable': 'Rasm mavjud emas',
      'failedToLoadImage': 'Rasm yuklanmadi',
      'requestAccessSent': 'So\'rov yuborildi',
      'notificationFilterAll': 'Barchasi',
      'notificationFilterAppointments': 'Uchrashuvlar',
      'notificationFilterTasks': 'Vazifalar',
      'notificationFilterMessages': 'Xabarlar',
      'notificationSettings': 'Sozlamalar',
      'notificationViewResult': 'Natijani ko\'rish',
      'notificationViewAppointment': 'Uchrashuvni ko\'rish',
      'notificationOpenCalendar': 'Taqvimni ochish',
      'notificationReschedule': 'Qayta rejalashtirish',
      'notificationEmptyFilter': 'Ushbu bo\'limda bildirishnoma yo\'q',
      'notificationEmptyFilterHint':
          'Boshqa filterni tanlang yoki keyinroq qaytib keling.',
      'notificationEmptyBody':
          'Uchrashuvlar, xabarlar va bemor faoliyati haqida bildirishnomalar shu yerda ko\'rinadi.',
      'timeJustNow': 'Hozir',
      'timeMinAgo': '{n} daqiya oldin',
      'timeYesterday': 'Kecha {time}',
      'monthJan': 'Yanv',
      'monthFeb': 'Fev',
      'monthMar': 'Mart',
      'monthApr': 'Apr',
      'monthMay': 'May',
      'monthJun': 'Iyun',
      'monthJul': 'Iyul',
      'monthAug': 'Avg',
      'monthSep': 'Sen',
      'monthOct': 'Okt',
      'monthNov': 'Noy',
      'monthDec': 'Dek',
      'notificationTypeAppointmentBooked': 'Uchrashuv band qilindi',
      'notificationTypeAppointmentCancelled': 'Uchrashuv bekor qilindi',
      'notificationTypeTaskCompleted': 'Vazifa bajarildi',
      'notificationTypeTaskAssigned': 'Vazifa berildi',
      'notificationTypeDocumentAccessRequest': 'Hujjatga kirish so\'rovi',
      'notificationTypeDocumentAccessApproved': 'Hujjatga kirish tasdiqlandi',
      'notificationTypeDocumentAccessRejected': 'Hujjatga kirish rad etildi',
      'notificationTypeAiScribeReady': 'AI yozuvchi xulosasi tayyor',
      'notificationMessagePatientBookedAppointment':
          'Bemor {name} uchrashuvni band qildi.',
      'notificationMessageAppointmentReminder':
          'Uchrashuvingiz taxminan 1 soatdan keyin. Tayyor bo\'ling.',
      'patientBriefingTitle': 'Bemor brifingi',
      'patientBriefingError': 'Brifing yaratib bo\'lmadi.',
      'patientBriefingSources': '{n} ta hujjat asosida.',
      'patientBriefingSourcesWithAppointments':
          '{docs} ta hujjat va {appts} ta uchrashuv asosida.',
      'patientBriefingSourcesAppointmentsOnly': '{n} ta uchrashuv asosida.',
      'patientBriefingCopied': 'Brifing buferga nusxalandi.',
      'patientBriefingCopy': 'Nusxalash',
      'generateBriefing': 'Brifing yaratish',
      'patientBriefingGenerating':
          'Hujjatlar o\'qilmoqda va brifing yaratilmoqda...',
      'aiFollowupRefineDiagnosis': 'Tashxisni aniqlashtirish',
      'aiFollowupTreatmentOptions': 'Davolash variantlari',
      'aiFollowupWhenToWorry': 'Qachon xavotirlanish kerak',
      'aiFollowupPromptRefineDiagnosis':
          'Oldingi javobingiz asosida tashxisni aniqlashtiring va differensial tashxislarni ustuvor tartibda bering.',
      'aiFollowupPromptTreatmentOptions':
          'Oldingi baholash asosida davolash variantlari va birinchi qator boshqaruv choralarini bering.',
      'aiFollowupPromptWhenToWorry':
          'Xavfli belgilarni kengaytiring va qachon shoshilinch yoki favqulodda yordam kerakligini aniq yozing.',
      'showAiAndFormNotes': 'Ko\'rsatish',
      'hideAiAndFormNotes': 'Yashirish',
      'notesSectionsHidden':
          'AI va shakl qaydlari yashirilgan. Ko\'rsatish uchun ⋮ → Ko\'rsatish.',

      // Chronic Diseases
      'aids': 'OITS',
      'diabetes': 'Qandli diabet',
      'hypertension': 'Gipertoniya',
      'heartDisease': 'Yurak kasalligi',
      'cancer': 'Saraton',
      'kidneyDisease': 'Buyrak kasalligi',
      'liverDisease': 'Jigar kasalligi',
      'asthma': 'Astma',
      'copd': 'XOBIK',
      'epilepsy': 'Epilepsiya',

      // Additional task-related
      'taskNotFound': 'Vazifa topilmadi',
      'noCheckInsFound': 'Tekshiruvlar topilmadi',
      'cancelTask': 'Vazifani bekor qilish',
      'cancelTaskConfirm': 'Ushbu vazifani bekor qilishni xohlaysizmi?',
      'yesCancel': 'Ha, bekor qilish',
      'failedToCancel': 'Bekor qilishda xatolik',
      'days': 'kunlar',
      'value': 'Qiymat',
      'newPatient': 'Yangi bemor',
      'createNewPatient': 'Yangi bemor yaratish',
      'createPatient': 'Bemor yaratish',
      'patientCreated': 'Bemor yaratildi',
      'createFailed': 'Yaratishda xatolik',
      'documentHistory': 'Hujjatlar tarixi',
      'createForm': 'Forma yaratish',
      'selectFormTemplate': 'Forma shablonini tanlang',
      'uploadPdf': 'PDF yuklash',
      'takePhoto': 'Rasm olish',
      'chooseFromGallery': 'Galereyadan tanlash',
      'scanMultiPage': 'Ko\'p sahifali skanerlash',
      'add': 'Qo\'shish',
      'uploading': 'Yuklanmoqda...',
      'noFileData': 'Yuklash uchun fayl ma\'lumotlari yo\'q',
      'document': 'Hujjat',
      'uploaded': 'yuklandi',
      'uploadError': 'Yuklash xatosi',
      'addAnotherPage': 'Yana bir sahifa qo\'shish?',
      'pagesScanned': 'Skanerlangan sahifalar',
      'finish': 'Tugallandi',
      'addPage': 'Sahifa qo\'shish',
      'scannedDocument': 'Skanerlangan hujjat',
      'pages': 'sahifalar',
      'pageDocument': 'sahifali hujjat',
      'scanFailed': 'Skanerlash muvaffaqiyatsiz',
      'failedToLoad': 'Yuklashda xatolik',
      'appointmentsLast7Days': 'Uchrashuvlar (Oxirgi 7 kun)',
      'visitTypeDistribution': 'Uchrashuv turi taqsimoti',
      'inPerson': 'Shaxsan',
      'appointmentsToday': 'Bugungi uchrashuvlar',
      'completedToday': 'Bajarilgan (bugun)',
      'cancelledToday': 'Bekor / kelmadi',
      'newPatientsToday': 'Yangi bemorlar (bugun)',
      'activePatients': 'Faol bemorlar (30 kun)',
      'documentsReceived': 'Qabul qilingan hujjatlar (30 kun)',
      'engagementSummary': 'Bemorlar faoliyati',
      'analyticsNoData': 'Ma\'lumot yo\'q',
      'extendedProfile': 'Kengaytirilgan profil',
      'schedule': 'Jadval',
      'profession': 'Kasb',
      'selectDateHint': 'Tug\'ilgan sanani tanlash',
      'ibanAccountNumber': 'IBAN / Hisob raqami',
      'taxIdVatId': 'Soliq ID / QQS ID',
      'certificateUploaded': 'Sertifikat yuklandi',
      'minimum6Characters': 'Kamida 6 belgi',
      'doctor': 'Shifokor',
      'noMessages': 'Xabarlar yo\'q',
      'photoUpdated': 'Rasm yangilandi',
      'extendedProfileSaved': 'Kengaytirilgan profil saqlandi',
      'socialMedia': 'Ijtimoiy tarmoqlar',
      'socialMediaHint': '@username yoki URL',
      'patientWithChronicDisease': 'Surunkali kasallikka chalingan bemor',
      'dealingWithChronicDiseasePatient':
          'Siz surunkali kasallikka chalingan bemor bilan ishlayapsiz:',
      'takeExtraCareForChronicDiseasePatient':
          'Iltimos, ehtiyot bo\'ling va bu bemor uchun siz rejalashtirayotgan protseduralar, dorilar va davolanishni ikki martta tekshiring.',
      'iUnderstand': 'Tushundim',
      'patientAppAccess': 'Bemor ilovasiga kirish',
      'createPatientAccount': 'Bemor hisobini yaratish',
      'accountCreated': 'Hisob yaratildi',
      'accountAlreadyAvailable': 'Hisob allaqachon mavjud',
      'noAccountYet': 'Hisob hali mavjud emas',
      'shareCredentialsWithPatient':
          'Iltimos, bu ma\'lumotlarni bemor bilan baham ko\'ring. Birinchi marta kirishda parolni o\'zgartirish talab qilinadi.',
      'username': 'Foydalanuvchi nomi',
      'oneTimePassword': 'Bir martalik parol',
      'forSecurityPasswordShownOnce':
          '⚠️ Xavfsizlik uchun bu parol faqat bir marta ko\'rsatiladi.',
      'errorCreatingAccount': 'Hisob yaratishda xatolik',
      'copiedToClipboard': 'xotiraga nusxalandi',
      'copy': 'Nusxalash',
      'patientIdNotAvailable': 'Bemor ID mavjud emas',
      'cannotSaveNotes': 'Izohlarni saqlab bo\'lmaydi.',
      'noItemsToSave': 'Saqlash uchun elementlar yo\'q. Uchrashuv yakunlandi.',
      'appointmentEndedDocumentationSaved':
          'Uchrashuv yakunlandi. Hujjatlar saqlandi.',
      'errorSavingDocumentation': 'Hujjatlarni saqlashda xatolik',
      'errorPickingImage': 'Rasmni tanlashda xatolik',
      'errorLoadingPatientId': 'Bemor ID ni yuklashda xatolik',
      'useEndAppointmentToSave':
          'Barcha hujjatlarni saqlash uchun "Uchrashuvni tugatish" tugmasidan foydalaning',
      'documentsFinalizeHint':
          'Yakuniy izohlar va PDF fayllar uchrashuvni tugatganingizda saqlanadi. Yangi yuklamalar darhol shu yerda ko\'rinadi.',
      'documentsEmptyHint':
          'Yuklangan fayllar va skanlar qabul vaqtida ochish uchun shu yerda paydo bo\'ladi.',
      'consultationScheduleLine': '{start} – {end} • Konsultatsiya',
      'patientIdLabel': 'ID #{id}',
      'patientAgeYears': '{age} yosh',
      'appointmentStatusRequested': 'So\'ralgan',
      'appointmentStatusConfirmed': 'Tasdiqlangan',
      'appointmentStatusCancelled': 'Bekor qilingan',
      'appointmentStatusCompleted': 'Yakunlangan',
      'appointmentStatusInProgress': 'Jarayonda',
      'docSectionLaboratory': 'Laboratoriya',
      'docSectionImaging': 'Tasvirlash',
      'docSectionClinical': 'Klinik va retseptlar',
      'docSectionForms': 'Formalari va qaydlar',
      'docSectionPrivate': 'Shaxsiy',
      'docSectionOther': 'Boshqa',
      'docSectionUncategorized': 'Toifalanmagan',
      'consultationDocumentsDropHint':
          'Fayllarni shu yerga torting yoki yuklash uchun bosing (bemorning tibbiy hujjati sifatida saqlanadi)',
      'consultationDocumentsUploading': 'Yuklanmoqda…',
      'consultationUploadNoBytes':
          'Fayl o\'qilmadi. Kichikroq fayl yoki boshqa formatdan foydalaning.',
      'consultationUploadFailed': 'Yuklash amalga oshmadi',
      // Success snack uses [consultationUploadSuccess] method (full wording).
      'consultationUploadSuccess': '{count} ta fayl muvaffaqiyatli yuklandi.',
      'soapNotesSectionTitle': 'Tuzilgan izohlar (SOAP)',
      'soapNotesSectionSubtitle':
          'Ixtiyoriy maydonlar — yakuniy konsultatsiya PDF fayliga qo\'shiladi.',
      'soapSubjective': 'Subyektiv',
      'soapObjective': 'Obyektiv',
      'soapAssessment': 'Baholash',
      'soapPlan': 'Reja',
      'consultationFocusModeTooltip': 'Katta izoh rejimi',
      'consultationFocusModeTitle': 'Konsultatsiya izohlari',
      'typeANote': 'Izoh yozing',
      'appointmentDocumentation': 'Uchrashuv hujjatlari',
      'docModeGeneral': 'Umumiy uchrashuv hujjati',
      'docMode0252': '025-2 Stomatologik karta',
      'docModeDental': 'Stomatologik qabul yozuvi',
      'dentalDocIntro':
          'Tishni bosing (FDI), xizmatlarni qo\'shing, kerak bo\'lsa chegirma kiriting. Jami bir xil valyutadagi narx qatorlari yig\'indisidan hisoblanadi.',
      'dentalUpperJaw': 'Yuqori jag\'',
      'dentalLowerJaw': 'Pastki jag\'',
      'dentalDiscountPercent': 'Chegirma',
      'dentalClinicalNotes': 'Klinik izohlar',
      'dentalSubtotal': 'Oraliq jami',
      'dentalDiscount': 'Chegirma',
      'dentalTotal': 'To\'lov',
      'dentalLineItems': 'qatorma-qator',
      'dentalToothServices': 'Tish uchun xizmatlar',
      'dentalAddService': 'Xizmat qo\'shish',
      'dentalNoServices': 'Avvalo «Xizmatlar va narxlar»da xizmatlarni kiriting.',
      'dentalDocSaved': 'Stomatologik hujjat saqlandi',
      'dentalDocSaveFailed': 'Stomatologik hujjatni saqlab bo\'lmadi',
      'dentalPdfHeader': 'STOMATOLOGIK QABUL — tishlar bo\'yicha',
      'unsavedChangesSwitch':
          'Saqlanmagan o\'zgarishlar bor. Almashishdan oldin saqlaysizmi?',
      'saveAndSwitch': 'Saqlash va almashtirish',
      'discardAndSwitch': 'Tashlab, almashtirish',
      'openForm0252': '025-2 shaklini to\'ldirish',
      'errorOpeningDocument': 'Hujjatni ochishda xatolik',
      'cannotOpenDocumentUrl': 'Hujjat URL ni ochib bo\'lmaydi',
      'errorSaving': 'Saqlashda xatolik',
      'openRoom': 'Xonani ochish',
      'chronicDiseaseUpdated': 'Surunkali kasallik yangilandi',
      'chronicDisease_none': 'Yo\'q',
      'chronicDisease_diabetesType1': 'Qandli diabet (1-tip)',
      'chronicDisease_diabetesType2': 'Qandli diabet (2-tip)',
      'chronicDisease_hivAids': 'HIV/OIV',
      'chronicDisease_hypertension': 'Gipertoniya',
      'chronicDisease_heartDisease': 'Yurak kasalligi',
      'chronicDisease_chronicKidneyDisease': 'Surunkali buyrak kasalligi',
      'chronicDisease_chronicLiverDisease': 'Surunkali jigar kasalligi',
      'chronicDisease_asthma': 'Bronxial astma',
      'chronicDisease_copd': 'O\'pka obstruktiv kasalligi',
      'chronicDisease_cancer': 'Saraton',
      'chronicDisease_epilepsy': 'Epilepsiya',
      'chronicDisease_multipleSclerosis': 'Ko\'p skleroz',
      'chronicDisease_parkinsonsDisease': 'Parkinson kasalligi',
      'chronicDisease_rheumatoidArthritis': 'Revmatoid artrit',
      'chronicDisease_lupus': 'Lupus',
      'chronicDisease_crohnsDisease': 'Kron kasalligi',
      'chronicDisease_ulcerativeColitis': 'Yaraqli kolit',
      'chronicDisease_hemophilia': 'Gemofiliya',
      'chronicDisease_sickleCellDisease': 'O\'roq hujayrali anemiya',
      'chronicDisease_thalassemia': 'Talassemiya',
      'chronicDisease_other': 'Boshqa',
      'failedToUpdate': 'Yangilashda xatolik',
      'errorLoadingSlots': 'Vaqtlarni yuklashda xatolik',
      'id': 'ID',
      'waitingRoom': 'Kutish xonasi',

      // Form 025-2 translations
      'form0252': 'Form 025-2',
      'form0252MedicalDocument': '025-2 raqamli tibbiy hujjat',
      'patientId': 'Bemorning shaxsiy raqami',
      'job': 'Kasbi',
      'diagnosis': 'Tashxis',
      'complaints': 'Shikoyati',
      'otherIllnessesAndComplications':
          'Boshidan o\'tkazgan va yo\'ldosh kasalliklar',
      'moreDetailsOnAbove': 'Aynan shu kasalliklarning rivojlanishi',
      'visualCheckup': 'Obyektiv tekshiruv, tashqi ko\'rinishi',
      'occlusionBiteType': 'Tish jipslanishi (pripkus)',
      'oralCavityCondition':
          'Og\'iz bo\'shlig\'i, milk, alveola, tanglay shilliq qavatlari holati',
      'xrayLabExaminationData': 'Rentgen labarator tekshiruv ma\'lumotlari',
      'treatment': 'Davolanish',
      'treatmentResultProgress': 'Davolanish natijasi (epikriz)',
      'recommendationsInstructions': 'Ko\'rsatmalar',
      'returnVisits': 'Qayta tashriflar',
      'clinicalFindingsConclusion':
          'Qayta kasallanib murojaat qilgan bemor anamnezi, statusi, tashxisi va davolanish kundaligi',
      'doctorsSurname': 'Davolovchi shifokor familiyasi',
      'noReturnVisitsAddedYet': 'Qayta tashrif mavjud emas',
      'addReturnVisit': 'Tashrif qo\'shish',
      'saveForm': 'Saqlash',
      'patientFormSignatureSectionTitle': 'Bemor imzosi (025-2 shakl)',
      'requestPatientFormSignature': 'Bemordan imzo so\'rash',
      'patientSignaturePending':
          'Imzo so\'rovi yuborildi. Bemorga mobil ilovada bildirishnoma boradi.',
      'patientSignatureRequestSent':
          'Bemor formani ko\'rib chiqish va imzolash uchun xabardor qilindi.',
      'patientFormSignatureReceived': 'Bemor imzosi qabul qilindi.',
      'patientFormSignedAtPrefix': 'Imzolangan sana',
      'patientFormSaveAgainToRefreshPdf':
          'PDF faylida imzo bo\'lishi uchun formani qayta saqlang.',
      'dentalChart': 'Tish diagrammasi',
      'icd10SearchHint': 'ICD-10 kodi yoki nomi bo\'yicha qidiring',
      'speakToType': 'Gapirish orqali yozish',
      'speechToTextRequiresPro':
          'Ovozdan matnga PRO obuna talab qilinadi.',
      'transcribing': 'Matnga o\'girilmoqda…',
      'transcriptionAdded': 'Matn qo\'shildi',
      'noSpeechDetected': 'Ovoz aniqlanmadi',
      'dentalLegend':
          'Belgilanishlar: K=kariyes, P=plomba, pulpit, periodontit, koronka, shtift, yetishmaydigan, protez.',
      'toothMap': 'Tishlar holati',
      'toothMapState': 'Tishlar holati',
      'willBeSetAutomaticallyOnSave': 'Avtomatik tarzda to\'ldiriladi',
      'isWaiting': 'kutmoqda',
      'openRoomWhenReady':
          'Qo\'ng\'iroqni boshlashga tayyor bo\'lganda xonani oching',
      'selectDatesToSeeSchedule': 'Jadvalni ko\'rish uchun sanalarni tanlang',
      'noItemsForThisDay': 'Ushbu kun uchun elementlar yo\'q',
      'updateScheduleMessage':
          'Jadvalni yangilang\nSizning taqvimingiz bu qadar oldinga bron qilish vaqtlarini taqdim etmaydi. Iltimos, jadvalingizni yangilang.',
      'goToSchedule': 'Jadvalga o\'tish',
      'notSelected': 'Tanlanmagan',
      'pleaseSelectDateFirst': 'Iltimos, avval sanani tanlang',
      'failedToChangeSlot': 'Vaqtni o\'zgartirishda xatolik',
      'patientAssigned': 'Bemor tayinlandi',
      'failedToAssign': 'Tayinlashda xatolik',
      'slotDetails': 'Vaqt tafsilotlari',
      'reason': 'Tashrif sababi',
      'assignPatient': 'Bemorni tayinlash',
      'selected': 'Tanlangan',
      'noPatientsAvailable': 'Mavjud bemorlar yo\'q',
      'failedToLoadPatients':
          'Bemorlar ro\'yxati yuklanmadi. Qayta urinib ko\'ring.',
      'choosePlace': 'Joyni tanlash',
      'willBeBookedAsVideoCall':
          'Bu video qo\'ng\'iroq sifatida bron qilinadi.',
      'willBeBookedAtClinic': 'Bu klinika manzilida bron qilinadi.',
      'dateAndTime': 'Sana va vaqt',
      'saved': 'Saqlandi',
      'clinicAddress': 'Klinika manzili',
      'showAppointments': 'Uchrashuvlarni ko\'rsatish',
      'showFreeSlots': 'Bo\'sh vaqtlarni ko\'rsatish',
      'dismiss': 'Yopish',
      'setPracticeTimezoneHint':
          'Uchrashuv vaqtlari to\'g\'ri bo\'lishi uchun Profilda amaliyot vaqt zonangizni (masalan, Europe/Berlin) o\'rnating.',
      'start': 'Boshlash',
      'today': 'Bugun',
      'noAppointmentsToday': 'Bugun uchrashuvlar yo\'q',
      'scheduleIsClear': 'Jadvalingiz bo\'sh',
      'allPatients': 'Barcha bemorlar',
      'askShifaAi': 'Shifa AI dan so\'rang',
      'fromShifaAi': 'Shifa AI dan',
      'previous': 'Oldingi',
      'next': 'Keyingi',
      'addToNotes': 'Eslatmalarga qo\'shish',
      'addedToNotes': 'Eslatmalarga qo\'shildi',
      'fromLast0252Form': 'Oxirgi 025-2 shaklidan',
      'loadingPatients': 'Bemorlar yuklanmoqda…',
      'noPatientSelected': 'Bemor tanlanmagan',
      'aiWillRespondHere': 'AI bu yerda javob beradi…',
      'failedToStartConversation': 'Suhbatni boshlashda xatolik',
      'failedToSendMessage': 'Xabarni yuborishda xatolik',
      'fileAttachmentComingSoon': 'Fayl biriktirish - tez orada',
      'searchDoctorsAndPatients': 'Shifokorlar va bemorlarni qidirish',
      'selectConversation': 'Suhbatni tanlash',
      'noUsersFound': 'Foydalanuvchilar topilmadi',
      'attachFile': 'Fayl biriktirish',
      'couldNotGetAddressDetails':
          'Manzil tafsilotlarini olish mumkin emas. Iltimos, boshqa joylashuvni tanlang.',
      'errorGettingCurrentLocation': 'Joriy joylashuvni olishda xatolik',
      'passwordUpdated': 'Parol yangilandi',
      'germany': 'Germaniya',
      'uzbekistan': 'O\'zbekiston',
      'usa': 'AQSH',
      'otherCountry': 'Boshqa',
      'russian': 'Rus',
      'german': 'Nemis',
      'selectProfession': 'Kasbni tanlash',
      'searchProfession': 'Kasbni qidirish...',
      'noProfessionsFound': 'Kasblar topilmadi',
      'personalInformation': 'Shaxsiy ma\'lumotlar',
      'workplaceInformation': 'Ish joyi ma\'lumotlari',
      'clinicOrWorkplaceName': 'Klinika / Ish joyi nomi',
      'enterClinicOrWorkplaceName': 'Klinika yoki ish joyi nomini kiriting',
      'enterStreetAddress':
          'Ko\'cha manzili, bino nomi, qavat va boshqalarni kiriting',
      'streetAddressHelper':
          'Siz bu maydonni bino tafsilotlari, qavat, xona raqami va boshqalarni qo\'shish uchun tahrirlashingiz mumkin.',

      // Schedule
      'setupYourSchedule': 'Jadvalni sozlash',
      'selectWorkingDaysAndDefineSlots':
          'Ish kunlarini tanlang va bemorlar uchun bron qilish vaqtlarini belgilang.',
      'scheduleValidFrom': 'Jadval dan amal qiladi:',
      'scheduleValidUntil': 'Jadval tugaguncha amal qiladi:',
      'existingCalendarPeriods': 'Mavjud kalendar davrlari',
      'newPeriodMustNotOverlap':
          'Yangi davr mavjud davrlardan hech biriga to\'g\'ri kelmasligi kerak.',
      'scheduleValidFromNew': 'Yangi davr boshlanishi',
      'selectScheduleStartDate': 'Jadval boshlanish sanasini tanlang',
      'selectScheduleEndDate': 'Jadval tugash sanasini tanlang',
      'scheduleSaved': 'Jadval saqlandi!',
      'errorWhileSaving': 'Saqlashda xatolik',
      'scheduleOverlapsExisting': 'Jadval mavjud jadval bilan mos keladi',
      'existingSchedule': 'Mavjud jadval',
      'newScheduleMustStartAfter':
          'Yangi jadval quyidagi sanadan boshlanishi kerak',
      'newScheduleMustBeBeforeOrAfter':
          'Yangi jadval to\'liq oldin (tugashi) yoki to\'liq keyin (boshlanishi) bo\'lishi kerak',
      'before': 'oldin',
      'orAfter': 'yoki keyin',
      'failedToLoadSchedule': 'Jadvalni yuklashda xatolik',
      'failedToLoadRules': 'Qoidalarni yuklashda xatolik',
      'unauthorizedPleaseLoginAgain': 'Ruxsatsiz. Iltimos, qayta kiring.',
      'endTimeMustBeAfterStartTime':
          'Tugash vaqti boshlanish vaqtidan keyin bo\'lishi kerak.',
      'thisTimeOverlapsExistingSlot': 'Bu vaqt mavjud vaqt bilan mos keladi.',
      'monday': 'Dushanba',
      'tuesday': 'Seshanba',
      'wednesday': 'Chorshanba',
      'thursday': 'Payshanba',
      'friday': 'Juma',
      'saturday': 'Shanba',
      'sunday': 'Yakshanba',
      'daySlots': 'vaqtlar',
      'noSlotsYet': 'Hali vaqtlar yo\'q',
      'timePeriod': 'Vaqt oralig\'i',
      'slotTimeframe': 'Vaqt oralig\'i',
      'minutes': 'daqiqa',
      'expandScheduleForDates': 'Ma\'lum sanalar uchun jadvalni kengaytirish',
      'expandScheduleHint':
          'Mavjud jadvaldan keyin qo\'shimcha soatlar qo\'shing (masalan 17:00–23:00). Belgilangan vaqtlarni o\'zgartira olmaysiz.',
      'fromDate': 'Boshlanish sanasi',
      'toDate': 'Tugash sanasi',
      'addExpansion': 'Kengaytirish qo\'shish',
      'noDateSpecificRules': 'Sana bo\'yicha kengaytirishlar hali yo\'q.',
      'expansionAdded': 'Jadval kengaytmasi qo\'shildi.',
      'expandOnlyAfterExisting':
          'Boshlanish vaqti mavjud jadval tugash vaqtiga to\'g\'ri kelishi kerak. Faqat shundan keyin vaqt qo\'shish mumkin.',
    },
    'ru': {
      // Common
      'appName': 'Shifa Doctor',
      'loading': 'Загрузка...',
      'error': 'Ошибка',
      'retry': 'Повторить',
      'unauthorized': 'Не авторизован. Пожалуйста, войдите снова.',
      'networkError': 'Ошибка сети. Проверьте подключение.',
      'requestTimeout': 'Время ожидания истекло. Попробуйте снова.',
      'accessDenied': 'Доступ запрещен',
      'notFound': 'Ресурс не найден',
      'serverError': 'Ошибка сервера. Попробуйте позже.',
      'somethingWentWrong': 'Что-то пошло не так',
      'cancel': 'Отмена',
      'save': 'Сохранить',
      'delete': 'Удалить',
      'edit': 'Редактировать',
      'back': 'Назад',
      'next': 'Далее',
      'complete': 'Завершить',
      'submit': 'Отправить',
      'close': 'Закрыть',
      'yes': 'Да',
      'no': 'Нет',
      'ok': 'ОК',
      'confirm': 'Подтвердить',
      'discard': 'Отменить',
      'search': 'Поиск',
      'filter': 'Фильтр',
      'apply': 'Применить',
      'saveDraftNote': 'Сохранить как Черновик',
      'newSession': 'Новая сессия',
      'draftActions': 'Действия с черновиком',
      'draftSavedAsConsultationNote':
          'Черновик сохранен как заметка консультации',
      'failedToSaveDraft': 'Не удалось сохранить черновик',
      'refresh': 'Обновить',
      'noData': 'Нет данных',
      'required': 'Обязательно',
      'doctor': 'Врач',
      'patient': 'Пациент',
      'admin': 'Администратор',
      'paymentsOpsTitle': 'Платёжные операции',
      'failedToLoadFailedWebhooks':
          'Не удалось загрузить неуспешные webhook: {{error}}',
      'noFailedOrUnprocessedStripeWebhooks':
          'Нет неуспешных или необработанных Stripe webhook событий.',
      'selectedCount': 'Выбрано: {{count}}',
      'retrySelected': 'Повторить выбранные',
      'retrying': 'Повтор...',
      'statusFailed': 'ОШИБКА',
      'statusUnprocessed': 'НЕ ОБРАБОТАНО',
      'eventIdLabel': 'eventId: {{eventId}}',
      'createdLabel': 'создано: {{created}}',
      'retryMetaLine':
          'retryCount: {{retryCount}} · lastRetryAt: {{lastRetryAt}} · retriedByAdminUserId: {{retriedByAdminUserId}}',
      'notAvailableShort': 'Н/Д',
      'retryWebhookEventTitle': 'Повторить webhook событие?',
      'retryWebhookEventBody':
          'Это повторно обработает сохранённый Stripe webhook payload.\n\n'
              'eventType: {{eventType}}\n'
              'eventId: {{eventId}}',
      'retrySelectedWebhookEventsTitle':
          'Повторить выбранные webhook события?',
      'retrySelectedWebhookEventsBody':
          'Вы собираетесь повторить {{count}} webhook событие(й). Каждое выбранное событие будет переиграно из сохранённого payload.',
      'webhookRetriedSuccessfully': 'Webhook успешно повторно обработан.',
      'retryStillFailing':
          'Повтор выполнен, но событие всё ещё завершается ошибкой.',
      'bulkRetryComplete':
          'Массовый повтор завершён: успешно {{successCount}}, ошибок {{failCount}}.',
      'paymentLabel': 'ОПЛАТА: {{status}}',
      'paymentUnknown': 'Неизвестно',
      'paymentStateRaw': '{{state}}',
      'paymentPaid': 'Оплачено',
      'paymentPending': 'Ожидает оплату',
      'paymentFailed': 'Ошибка',
      'paymentNotRequired': 'Не требуется',
      'appointmentPlaceLockedHint':
          'Место записи отображается только для информации и недоступно для изменения здесь.',
      'encouragePayment': 'Напомнить об оплате',
      'paymentReminderSent': 'Напоминание отправлено пациенту',
      'paymentReminderFailed': 'Не удалось отправить напоминание: {{error}}',

      // Navigation
      'chat': 'Чат',
      'home': 'Главная',
      'calendar': 'Календарь',
      'patients': 'Пациенты',
      'tasks': 'Задачи',
      'profile': 'Профиль',
      'signOut': 'Выйти',
      'signOutConfirm': 'Вы уверены, что хотите выйти?',
      'logout': 'Выйти',
      'confirmLogout': 'Вы уверены, что хотите выйти?',

      // Auth
      'login': 'Войти',
      'signIn': 'Войти',
      'phoneOrEmail': 'Номер телефона или Email',
      'emailOrPhone': 'Email или номер телефона',
      'password': 'Пароль',
      'forgotPassword': 'Забыли пароль?',
      'createAccount': 'Создать аккаунт',
      'adminPanel': 'Панель администратора',
      'createAdminUser': 'Создать администратора',
      'createAdminUserDescription':
          'Создайте нового администратора. Он сможет войти в панель администратора.',
      'adminUserCreated': 'Администратор успешно создан',
      'enterValidEmail': 'Введите действительный email',
      'passwordMinLength': 'Пароль должен содержать не менее 8 символов',
      'enterEmailOrPhone': 'Введите email или телефон',
      'verify': 'Подтвердить',
      'oneTimeKey': 'Одноразовый ключ',
      'pleaseEnterOneTimeKey': 'Введите одноразовый ключ.',
      'keyVerified': 'Ключ подтверждён',
      'firstName': 'Имя',
      'lastName': 'Фамилия',
      'emailOptional': 'Email (необязательно)',
      'confirmPassword': 'Подтвердите пароль',
      'enterFirstName': 'Введите имя',
      'enterLastName': 'Введите фамилию',
      'enterPhoneNumber': 'Введите номер телефона',
      'optional': 'Необязательно',
      'pleaseVerifyInvitationKeyFirst': 'Сначала подтвердите ключ приглашения.',
      'accountInformation': 'Данные аккаунта',
      'dateOfBirth': 'Дата рождения',
      'clinic': 'Клиника',
      'profession': 'Специальность',
      'generalPractitioner': 'Врач общей практики',
      'cardiologist': 'Кардиолог',
      'dermatologist': 'Дерматолог',
      'pediatrician': 'Педиатр',
      'accountCreatedPleaseSignIn': 'Аккаунт создан! Войдите в систему.',
      'existingPatientCreatingDoctorAccount':
          'С этими данными уже зарегистрирован пациент. Мы создаём врачебный аккаунт для этого пациента.',
      'confirmRegistration': 'Подтвердить регистрацию',
      'signInToManageSystem': 'Войдите для управления системой',
      'goToDoctorLogin': 'Вход для врача',
      'adminEmailVerificationSent':
          'На {hint} отправлен 6-значный код подтверждения. Введите его ниже, чтобы завершить вход.',
      'adminEnterVerificationCode': 'Код подтверждения',
      'adminVerifyAndSignIn': 'Подтвердить и войти',
      'adminResendVerificationCode': 'Отправить код снова',
      'adminChangeAccount': 'Другой аккаунт',
      // Phone OTP & Forgot password
      'signInWithPhone': 'Войти по номеру телефона',
      'signInWithEmail': 'Войти по электронной почте',
      'enterEmailForOtp': 'Введите ваш email для получения кода подтверждения.',
      'enterEmail': 'Введите email',
      'otpSentToEmail': '6-значный код отправлен на {email}. Проверьте почту.',
      'continue': 'Продолжить',
      'enterOtp': 'Введите код',
      'resendCode': 'Отправить код снова',
      'resendCodeIn': 'Отправить код снова через {{time}}',
      'tooManyRequests': 'Слишком много запросов. Попробуйте позже.',
      'invalidOtp': 'Неверный или истекший код.',
      'resetPassword': 'Сброс пароля',
      'passwordMismatch': 'Пароли не совпадают.',
      'passwordTooWeak':
          'Пароль: минимум 8 символов, 1 заглавная буква и 1 цифра.',
      'accessRestricted': 'Доступ только для врачей.',
      'accountPending': 'Ваш аккаунт на модерации.',
      'accountBlocked': 'Ваш аккаунт заблокирован.',
      'otpSent': 'Код подтверждения отправлен.',
      'otpResent': 'Код отправлен снова.',
      'otpResendHint':
          'Чтобы получить новый код, вернитесь назад и нажмите «Продолжить».',
      'detecting': 'Определение…',
      'practiceTimezonePlaceholder':
          'Часовой пояс практики (например, Europe/Berlin)',

      // Profile
      'editProfile': 'Редактировать профиль',
      'language': 'Язык',
      'settings': 'Настройки',
      'english': 'Английский',
      'uzbek': 'Узбекский',
      'uzbekCyrillicMenu': 'Узбекский (кириллица)',
      'russian': 'Русский',
      'german': 'Немецкий',
      'selectLanguage': 'Выбрать язык',
      'languageChanged': 'Язык успешно изменен',
      'biography': 'Биография',
      'services': 'Услуги',
      'certificates': 'Сертификаты',
      'telegram': 'Telegram',
      'instagram': 'Instagram',
      'uploadCertificate': 'Загрузить сертификат',
      'addService': 'Добавить услугу',
      'removeService': 'Удалить услугу',
      'openServicesPricingToManageEntries':
          'Откройте «Услуги и цены», чтобы управлять записями',
      'enterService': 'Введите название услуги',
      'servicesPricing': 'Услуги и цены',
      'servicesPricingSubtitle':
          'Управляйте названиями услуг, ценами, валютами и описаниями',
      'servicesPricingPanelDesc':
          'Определите оплачиваемые услуги с описаниями и ценами в нескольких валютах.',
      'openServicesPricing': 'Открыть «Услуги и цены»',
      'newService': 'Новая услуга',
      'editService': 'Изменить услугу',
      'serviceTitleLabel': 'Название',
      'serviceDescriptionLabel': 'Описание',
      'servicePriceLabel': 'Стоимость (например, 25.00)',
      'serviceCurrencyLabel': 'Валюта (EUR/UZS/USD)',
      'serviceFreeConsultation': 'Бесплатная консультация (видео)',
      'serviceFreeConsultationHint':
          'При выборе этой услуги для видеоприёма запись сразу подтверждается без оплаты.',
      'serviceGroupsTitle': 'Группы услуг',
      'serviceGroupsHint':
          'Группы помогают упорядочить услуги в профиле. Меньший порядок сортировки отображается раньше.',
      'serviceGroupLabel': 'Группа',
      'serviceGroupNone': 'Без группы',
      'servicePricesSection':
          'Цены: добавьте строку по умолчанию для всех локаций или отдельные строки для конкретных клиник.',
      'priceScopeLabel': 'Действует для',
      'priceScopeAllLocations': 'Все локации (по умолчанию)',
      'addPriceRow': 'Добавить цену',
      'editGroup': 'Изменить группу',
      'groupName': 'Название группы',
      'sortOrder': 'Порядок сортировки',
      'newGroup': 'Новая группа',
      'addGroup': 'Добавить группу',
      'profileInformation': 'Личная информация',
      'contactDetails': 'Контактные данные',
      'paymentAndInvoicing': 'Оплата и выставление счетов',
      'profileInformationSaved': 'Информация профиля сохранена',
      'contactDetailsSaved': 'Контактные данные сохранены',
      'paymentAndInvoicingSaved': 'Оплата и выставление счетов сохранены',
      'settingsSaved': 'Настройки сохранены',
      'passwordUpdatedSuccessfully': 'Пароль успешно обновлен',
      'newPasswordConfirmationMismatchError':
          'Новый пароль и подтверждение не совпадают',
      'currentPasswordIsRequired': 'Текущий пароль обязателен',
      'pleaseConfirmNewPasswordError': 'Пожалуйста, подтвердите новый пароль',
      'currentPassword': 'Текущий пароль',
      'newPassword': 'Новый пароль',
      'confirmNewPassword': 'Подтвердите новый пароль',
      'billingName': 'Имя для выставления счета',
      'billingEmail': 'Email для выставления счета',
      'on': 'Вкл',
      'off': 'Выкл',
      'fullName': 'Полное имя',
      'country': 'Страна',
      'twoFactorAuthentication': 'Двухфакторная аутентификация',
      'encryptedDocuments': 'Зашифрованные документы',
      'updateOrChangeSchedule': 'Обновить или изменить расписание',
      'changeOrResetPassword': 'Изменить или сбросить пароль здесь',
      'settingsSubtitle': 'Страна, Язык, 2FA, Зашифрованные документы',
      'extendedProfileSubtitle':
          'Биография, Услуги, Сертификаты, Социальные сети',
      'phone': 'Телефон',
      'yourName': 'Ваше имя',

      // Home
      'dashboard': 'Панель управления',
      'todayAppointments': 'Сегодняшние записи',
      'upcomingAppointments': 'Предстоящие записи',
      'recentPatients': 'Последние пациенты',
      'analytics': 'Аналитика',

      // Calendar
      'appointments': 'Записи',
      'freeSlots': 'Свободные слоты',
      'date': 'Дата',
      'time': 'Время',
      'duration': 'Продолжительность',
      'place': 'Место',
      'changeSlot': 'Изменить слот',
      'cancelAppointment': 'Отменить запись',
      'cancelConfirm': 'Вы уверены, что хотите отменить эту запись?',
      'appointmentCancelled': 'Запись успешно отменена',
      'pastAppointmentNoChange':
          'Прошедшие записи нельзя изменить или отменить.',
      'pastSlotCannotAssign': 'Этот слот в прошлом. Назначить пациента нельзя.',
      'slotChanged': 'Слот успешно изменен',
      'makeAppointment': 'Записать на приём',
      'selectDate': 'Выбрать дату',
      'selectTime': 'Выбрать время',
      'availableSlots': 'Доступные слоты',
      'appointmentType': 'Тип приёма',
      'noSlotsAvailable': 'Нет доступных слотов',
      'noAppointments': 'Нет записей',
      'noFreeSlots': 'Нет свободных слотов',
      'monthJanuary': 'Январь',
      'monthFebruary': 'Февраль',
      'monthMarch': 'Март',
      'monthApril': 'Апрель',
      'monthMay': 'Май',
      'monthJune': 'Июнь',
      'monthJuly': 'Июль',
      'monthAugust': 'Август',
      'monthSeptember': 'Сентябрь',
      'monthOctober': 'Октябрь',
      'monthNovember': 'Ноябрь',
      'monthDecember': 'Декабрь',

      // Patients
      'patient': 'Пациент',
      'patientList': 'Список пациентов',
      'searchPatients': 'Поиск пациентов...',
      'noPatientsFound': 'Пациенты не найдены',
      'patientDetails': 'Детали пациента',
      'generalInformation': 'Общая информация',
      'documents': 'Документы',
      'chronicDisease': 'Хроническое заболевание',
      'selectChronicDisease': 'Выбрать хроническое заболевание',
      'noChronicDisease': 'Нет хронических заболеваний',
      'chronicDiseaseWarning':
          'Внимание: У этого пациента серьезное/хроническое заболевание. Пожалуйста, будьте особенно осторожны.',
      'createTask': 'Создать задачу',
      'assignResult': 'Назначить результат',
      'startAppointment': 'Начать прием',
      'phoneNumber': 'Номер телефона',
      'phoneNumberRequired': 'Номер телефона обязателен',
      'email': 'Email',
      'address': 'Адрес',
      'location': 'Местоположение',
      'latitude': 'Широта',
      'longitude': 'Долгота',
      'getCurrentLocation': 'Получить текущее местоположение',
      'saveLocation': 'Сохранить местоположение',
      'locationSaved': 'Местоположение сохранено',
      'invalidCoordinates': 'Пожалуйста, введите действительные координаты',
      'locationFeatureComingSoon':
          'Функция определения местоположения скоро появится. Пожалуйста, введите координаты вручную.',
      'selectLocationOnMap': 'Выбрать местоположение на карте',
      'currentLocation': 'Текущее местоположение',
      'addressFromCoordinates': 'Адрес по координатам',
      'coordinatesFromAddress': 'Координаты по адресу',
      'enterAddressToFindCoordinates': 'Введите адрес для поиска координат',
      'addressFound': 'Адрес найден',
      'addressNotFound': 'Адрес не найден',
      'pleaseEnterAddress': 'Пожалуйста, введите адрес',
      'locationServicesDisabled':
          'Службы определения местоположения отключены. Пожалуйста, включите их.',
      'locationPermissionDenied':
          'Разрешения на определение местоположения отклонены.',
      'locationPermissionDeniedForever':
          'Разрешения на определение местоположения отклонены навсегда. Пожалуйста, включите их в настройках.',
      'selectedLocation': 'Выбранное местоположение',
      'selectLocation': 'Выберите местоположение',
      'primary': 'Основное',
      'manage': 'Управлять',
      'label': 'Название',
      'manageLocations': 'Управление местоположениями',
      'addLocation': 'Добавить местоположение',
      'editLocation': 'Редактировать местоположение',
      'deleteLocation': 'Удалить местоположение',
      'deleteLocationConfirm':
          'Удалить "{label}"? Правила расписания и записи в этом местоположении сначала нужно удалить.',
      'noLocationsYet':
          'Местоположений пока нет. Нажмите "Добавить местоположение", чтобы создать первое.',
      'labelRequired': 'Название обязательно',
      'exampleMainClinic': 'например: Основная клиника',
      'setAsPrimary': 'Сделать основным',
      'addFirstLocationHint':
          'Добавьте как минимум одно место приема, чтобы организовать расписание.',
      'copyFromPreviousDay': 'Копировать с предыдущего дня',
      'copyFromAnotherDay': 'Копировать с другого дня',
      'copyScheduleFromDay': 'С какого дня копировать расписание?',
      'noPreviousDayScheduleToCopy':
          'На предыдущий день нет расписания для копирования.',
      'scheduleCopiedFromPreviousDay':
          'Расписание скопировано с предыдущего дня.',
      'noSourceDaysToCopyFrom':
          'Нет других дней с расписанием для копирования.',
      'failedToCopySchedule':
          'Не удалось скопировать расписание с выбранного дня.',
      'scheduleCopiedFromDay': 'Расписание скопировано с {day}.',
      'current': 'Текущее',
      'birthDate': 'Дата рождения',
      'gender': 'Пол',
      'male': 'Мужской',
      'female': 'Женский',
      'other': 'Другой',

      // Tasks
      'remoteCareTasks': 'Задачи удаленного ухода',
      'remoteCareTasksSubtitle': 'Контроль и управление наблюдением пациентов',
      'createRemoteCareTask': 'Создать задачу удаленного ухода',
      'taskTemplates': 'Шаблоны задач',
      'useTemplate': 'Использовать шаблоны',
      'activeTasks': 'Активные задачи',
      'completedTasks': 'Выполненные задачи',
      'overdueTasks': 'Просроченные задачи',
      'searchTasksOrPatients': 'Поиск задач или пациентов',
      'createFirstRemoteTask': 'Создайте первую задачу удаленного ухода',
      'taskProgress': 'Прогресс задачи',
      'perDay': 'в день',
      'taskName': 'Название задачи',
      'description': 'Описание',
      'category': 'Категория',
      'vital': 'Жизненно важные показатели',
      'exercise': 'Упражнение',
      'medication': 'Лекарство',
      'taskOther': 'Другое',
      'timesPerDay': 'Раз в день',
      'morningTime': 'Утреннее время',
      'afternoonTime': 'Дневное время',
      'eveningTime': 'Вечернее время',
      'startDate': 'Дата начала',
      'startTime': 'Время начала',
      'intervalBetweenTasks': 'Интервал между заданиями',
      'everyNHours': 'Каждые %d ч.',
      'every1Hour': 'Каждый час',
      'slotsPreviewLabel': 'Времена в день',
      'slotsPreviewClipped':
          '%d слот(ов) не поместятся до полуночи. Выберите меньший интервал или более раннее время начала.',
      'scheduleMode': 'Расписание',
      'scheduleModeEvenSpacing': 'Равные интервалы',
      'scheduleModeCustomTimes': 'Свои времена',
      'customTimesLabel': 'Времена в день',
      'customTimesHint':
          'Задайте каждое время вручную для поддержки нерегулярных расписаний.',
      'customTimesEmpty': 'Времена ещё не добавлены — добавьте ниже.',
      'customTimesAddSlot': 'Добавить время',
      'customTimesAddAtLeastOne': 'Добавьте хотя бы одно время',
      'customTimesCount': '%d слот(ов) в день',
      'edit': 'Изменить',
      'remove': 'Удалить',
      'endDate': 'Дата окончания',
      'durationDays': 'Продолжительность (дни)',
      'useEndDate': 'Использовать дату окончания (иначе продолжительность)',
      'inputType': 'Тип ввода',
      'numeric': 'Числовой',
      'text': 'Текст',
      'boolean': 'Да/Нет',
      'inputLabel': 'Метка ввода',
      'notesRequired': 'Примечания обязательны',
      'notesLabel': 'Метка примечаний',
      'taskCreated': 'Задача успешно создана',
      'taskUpdated': 'Задача успешно обновлена',
      'taskCancelled': 'Задача успешно отменена',
      'failedToCreateTask': 'Не удалось создать задачу',
      'failedToUpdateTask': 'Не удалось обновить задачу',
      'selectPatient': 'Выбрать пациента',
      'tapToSearch': 'Нажмите для поиска и выбора пациента',
      'searchByNameOrId': 'Поиск по имени или ID',
      'taskDetails': 'Детали задачи',
      'progress': 'Прогресс',
      'checkInCompleted': 'Выполнено',
      'pending': 'В ожидании',
      'missed': 'Пропущено',
      'checkIns': 'Отметки',
      'checkInDetails': 'Детали отметки',
      'scheduled': 'Запланировано',
      'submittedAt': 'Отправлено',
      'awaitingSubmission': 'Ожидается отправка',
      'noSubmissionReceived': 'Отправка не получена',
      'status': 'Статус',
      'active': 'Активно',
      'taskCompleted': 'Выполнено',
      'expired': 'Истек срок',
      'taskStatusCancelled': 'Отменено',
      'draft': 'Черновик',
      'all': 'Все',
      'noTasksFound': 'Задачи не найдены',
      'taskDescription': 'Описание задачи, показанное пациенту',
      'enterTaskName': 'Введите название задачи',
      'enterInputLabel': 'Например: Артериальное давление, Вес (кг)',
      'enterNotesLabel': 'Например: Дополнительные примечания',
      'notSet': 'Не установлено',

      // Documents
      'uploadDocument': 'Загрузить документ',
      'documentTitle': 'Название документа',
      'enterDocumentTitle': 'Введите название документа',
      'selectFile': 'Выбрать файл',
      'documentUploaded': 'Документ успешно загружен',
      'noDocuments': 'Нет доступных документов',
      // Document categories / visibility
      'documentCategoryLabel': 'Тип документа',
      'documentCategorySelect': 'Выберите тип',
      'documentCategoryHint':
          'Выберите тип медицинского результата, чтобы документ был виден всем врачам этого пациента. Внутренние/частные типы остаются видимы только вам.',
      'documentCategoryGroupMedical':
          'Медицинские результаты (видны всем врачам)',
      'documentCategoryGroupPrivate': 'Частные (видны только вам)',
      'sharedWithTeamTooltip': 'Виден всем врачам этого пациента',
      'documentCategory_BLOOD_TEST': 'Анализ крови',
      'documentCategory_URINE_TEST': 'Анализ мочи',
      'documentCategory_STOOL_TEST': 'Анализ кала',
      'documentCategory_LAB_RESULT': 'Лабораторный анализ',
      'documentCategory_MRI': 'МРТ',
      'documentCategory_CT_SCAN': 'КТ',
      'documentCategory_XRAY': 'Рентген',
      'documentCategory_ULTRASOUND': 'УЗИ',
      'documentCategory_MAMMOGRAPHY': 'Маммография',
      'documentCategory_ECG': 'ЭКГ',
      'documentCategory_EEG': 'ЭЭГ',
      'documentCategory_ENDOSCOPY': 'Эндоскопия',
      'documentCategory_BIOPSY': 'Биопсия',
      'documentCategory_PATHOLOGY': 'Патология',
      'documentCategory_IMAGING_OTHER': 'Другая визуализация',
      'documentCategory_PRESCRIPTION': 'Рецепт',
      'documentCategory_VACCINATION_RECORD': 'Запись о вакцинации',
      'documentCategory_DISCHARGE_SUMMARY': 'Выписной эпикриз',
      'documentCategory_REFERRAL': 'Направление',
      'documentCategory_HOSPITAL_REPORT': 'Заключение из больницы',
      'documentCategory_ALLERGY_REPORT': 'Аллергологический отчёт',
      'documentCategory_OTHER_MEDICAL': 'Другой медицинский результат',
      'documentCategory_APPOINTMENT_NOTE': 'Заметка к приёму',
      'documentCategory_REMOTE_TASK_DOCUMENT':
          'Документ к удалённой задаче',
      'documentCategory_FORM_025_2': 'Форма 025-2',
      'documentCategory_INTERNAL_NOTE': 'Внутренняя заметка',
      'documentCategory_OTHER_PRIVATE': 'Другой частный документ',
      'couldNotLoadDocument': 'Не удалось загрузить документ',
      'openDocument': 'Открыть документ',
      'requestAccess': 'Запросить доступ',
      'documentLocked': 'Заблокировано',
      'uploadedBy': 'Загружено',
      'anotherUser': 'Другой пользователь',
      'listRefreshed': 'Список пациентов обновлён',
      // Appointments
      'appointmentDetails': 'Детали записи',
      'patientName': 'Имя пациента',
      'appointmentDate': 'Дата записи',
      'appointmentTime': 'Время записи',
      'appointmentPlace': 'Место',
      'videoCall': 'Видеозвонок',
      'inClinic': 'В клинике',
      'notes': 'Примечания',
      'enterNotes': 'Введите примечания к записи...',
      'beforeTreatment': 'До лечения',
      'afterTreatment': 'После лечения',
      'startAiNotes': 'Начать AI заметки',
      'recordingForAiNotes': 'Запись для AI заметок',
      'processRecording': 'Обработать',
      'aiNotesUploaded':
          'Запись загружена. Заметки будут готовы через несколько минут.',
      'aiNotesNotReadyTryLater':
          'Заметки AI ещё не готовы. Попробуйте снова через 30 секунд.',
      'uploadPhoto': 'Загрузить фото',
      'endAppointment': 'Завершить прием',
      'appointmentEnded': 'Прием успешно завершен',
      'documentGenerated': 'Документ успешно сгенерирован',
      'viewDocument': 'Просмотреть документ',
      'requestSignature': 'Запросить подпись',
      'waitingForPatientSignature': 'Ожидание подписи пациента...',
      'patientSigned': 'Пациент подписал ✓',
      'signatureRequestSent': 'Запрос подписи отправлен пациенту',

      // Chat
      'messages': 'Сообщения',
      'typeMessage': 'Введите сообщение...',
      'send': 'Отправить',
      'noConversations': 'Пока нет бесед',
      'newestFirst': 'Сначала Новые',
      'oldestFirst': 'Сначала Старые',
      'unreadOnlyNewest': 'Только Непрочитанные (Сначала Новые)',
      'unreadOnlyOldest': 'Только Непрочитанные (Сначала Старые)',
      'noUnreadConversations': 'Нет непрочитанных бесед',
      'justNow': 'Только что',
      'minuteAgo': '1 минуту назад',
      'minutesAgo': '%s минут назад',
      'hourAgo': '1 час назад',
      'hoursAgo': '%s часов назад',
      'yesterday': 'Вчера',
      'isTyping': 'печатает',
      'selectConversation': 'Выберите беседу',
      'attachFile': 'Прикрепить файл',
      'selectImage': 'Выбрать изображение',
      'takePhoto': 'Сделать фото',
      'chooseFromGallery': 'Выбрать из галереи',
      'recordVoice': 'Записать голосовое сообщение',
      'voiceMessage': 'Голосовое сообщение',
      'voiceRecordingFinishHint':
          'Нажмите «Отправить», когда закончите — паузы и слова-паразиты допустимы.',
      'cancel': 'Отмена',
      'sendVoice': 'Отправить голосовое',
      'compressingImage': 'Сжатие изображения...',
      'uploadingFile': 'Загрузка файла...',
      'errorUploadingFile': 'Ошибка загрузки файла',
      'errorRecordingVoice': 'Ошибка записи голоса',
      'selectDocument': 'Выбрать документ',
      'voiceRecordingNotSupportedOnWeb':
          'Запись голоса не поддерживается в веб-версии. Пожалуйста, используйте мобильное приложение.',
      'microphonePermissionDenied': 'Доступ к микрофону запрещен',
      'failedToStartConversation': 'Не удалось начать разговор',
      'failedToSendMessage': 'Не удалось отправить сообщение',
      'failedToStartVideoCall': 'Не удалось начать видеозвонок',
      'videoCallAvailableFiveMinBefore':
          'Вы можете начать за 5 минут до приёма.',
      'videoCallTooLateAfterOneHour':
          'Видеоконсультация завершилась более часа назад. Открывайте только если приём уже идёт.',
      'videoCallEnded': 'Видеозвонок завершен',
      'callErrorOccurred': 'Произошла ошибка звонка',
      'waitingForParticipants': 'Ожидание участников...',
      'joinVideoCall': 'Присоединиться к видеозвонку',
      'videoCallReady': 'Видеозвонок готов',
      'videoCallErrorTitle': 'Ошибка видеозвонка',
      'videoCallConnecting': 'Подключение к видеозвонку...',
      'videoCallNotAvailableShort': 'Видеозвонок недоступен',
      'videoCallJoinWindowClosedMessage':
          'Видеозвонок завершён. Окно подключения закрывается через 15 минут после окончания приёма.',
      'videoCallNotYetAvailableMessage':
          'Видеозвонок пока недоступен. Подключиться можно за 5 минут до начала приёма.',
      'videoCallPaymentRequiredMessage':
          'Перед подключением к видеоконсультации необходимо произвести оплату.',
      'clickBelowToJoinCall': 'Нажмите ниже, чтобы присоединиться к звонку',
      'tokenRevoked': 'Токен отозван',
      'tokenRegenerated': 'Токен перегенерирован',
      'configUpdated': 'Настройки обновлены',
      'noContact': 'Нет контакта',
      'noEmail': 'Нет email',
      'noPhone': 'Нет телефона',
      'addRow': 'Добавить строку',
      'removeRow': 'Удалить строку',
      'editRow': 'Редактировать строку',
      'collapse': 'Свернуть',
      'expand': 'Развернуть',
      'formSavedSuccessfully': 'Форма успешно сохранена',
      'errorSavingForm': 'Ошибка сохранения формы',
      'pdfGeneratedPrintingNotImplemented':
          'PDF создан. Печать пока не реализована.',

      // Errors & Validation
      'invalidEmail':
          'Пожалуйста, введите действительный адрес электронной почты',
      'invalidPhone': 'Пожалуйста, введите действительный номер телефона',
      'passwordTooShort': 'Пароль должен содержать не менее 8 символов',
      'passwordTooLong': 'Пароль должен содержать не более 128 символов',
      'passwordsDoNotMatch': 'Пароли не совпадают',
      'passwordRequired': 'Пароль обязателен',
      'pleaseConfirmPassword': 'Пожалуйста, подтвердите пароль',
      'passwordRequirementMinLength': 'Не менее 8 символов',
      'passwordRequirementMaxLength': 'Не более 128 символов',
      'passwordRequirementUppercase': 'Одна заглавная буква',
      'passwordRequirementLowercase': 'Одна строчная буква',
      'passwordRequirementDigit': 'Одна цифра',
      'passwordRequirementSpecialChar': 'Один спецсимвол (!@#\$%^&* и т.д.)',
      'fieldRequired': 'Это поле обязательно',
      'pleaseSelectPatient': 'Пожалуйста, выберите пациента',
      'pleaseEnterTaskName': 'Название задачи обязательно',
      'unauthorized': 'Не авторизован. Пожалуйста, войдите снова.',
      'networkError': 'Ошибка сети. Пожалуйста, проверьте ваше соединение.',
      'unknownError': 'Произошла неизвестная ошибка',

      // Notifications
      'notifications': 'Уведомления',
      'noNotifications': 'Нет уведомлений',
      'markAllAsRead': 'Отметить все как прочитанные',
      'approve': 'Разрешить',
      'reject': 'Отклонить',
      'approved': 'Разрешено',
      'rejected': 'Отклонено',
      'documentAccessApproved': 'Доступ предоставлен',
      'documentAccessRejected': 'Запрос на доступ отклонён',
      'documentAccessRequest': 'Запрос доступа к документу',
      'documentAccessRequestDetail':
          '{doctorName} запросил доступ к документу «{documentTitle}» для пациента {patientName}.',
      'documentAccessApprovedDetail':
          'Ваш запрос доступа к документу «{documentTitle}» для пациента {patientName} одобрен.',
      'documentAccessRejectedDetail':
          'Ваш запрос доступа к документу «{documentTitle}» для пациента {patientName} отклонён.',
      'notificationGeneric': 'Уведомление',
      'notificationTypeAiScribeReady': 'Готово резюме AI-секретаря',
      'notificationMessagePatientBookedAppointment':
          'Пациент {name} записался на приём.',
      'notificationMessageAppointmentReminder':
          'Ваш приём примерно через 1 час. Пожалуйста, будьте готовы.',
      'patientBriefingTitle': 'Брифинг пациента',
      'patientBriefingError': 'Не удалось создать брифинг.',
      'patientBriefingSources': 'На основе {n} документ(ов).',
      'patientBriefingSourcesWithAppointments':
          'На основе {docs} документ(ов) и {appts} приём(ов).',
      'patientBriefingSourcesAppointmentsOnly': 'На основе {n} приём(ов).',
      'patientBriefingCopied': 'Брифинг скопирован.',
      'patientBriefingCopy': 'Копировать',
      'generateBriefing': 'Создать брифинг',
      'patientBriefingGenerating': 'Читаем документы и создаём брифинг...',
      'aiFollowupRefineDiagnosis': 'Уточнить диагноз',
      'aiFollowupTreatmentOptions': 'Варианты лечения',
      'aiFollowupWhenToWorry': 'Когда стоит тревожиться',
      'aiFollowupPromptRefineDiagnosis':
          'На основе вашего предыдущего ответа уточните диагноз и расставьте дифференциальные диагнозы по приоритету.',
      'aiFollowupPromptTreatmentOptions':
          'На основе предыдущей оценки предложите варианты лечения и меры первой линии.',
      'aiFollowupPromptWhenToWorry':
          'Расширьте раздел тревожных признаков и четко укажите, когда нужна срочная или экстренная помощь.',
      'showAiAndFormNotes': 'Показать',
      'hideAiAndFormNotes': 'Скрыть',
      'notesSectionsHidden': 'Записи AI и формы скрыты. Нажмите ⋮ → Показать.',
      'somethingWentWrong': 'Что-то пошло не так',
      'imageNotAvailable': 'Изображение недоступно',
      'failedToLoadImage': 'Не удалось загрузить изображение',
      'requestAccessSent': 'Запрос отправлен',

      // Chronic Diseases
      'aids': 'СПИД',
      'diabetes': 'Сахарный диабет',
      'hypertension': 'Гипертония',
      'heartDisease': 'Болезнь сердца',
      'cancer': 'Рак',
      'kidneyDisease': 'Заболевание почек',
      'liverDisease': 'Заболевание печени',
      'asthma': 'Астма',
      'copd': 'ХОБЛ',
      'epilepsy': 'Эпилепсия',

      // Additional
      'appointmentsLast7Days': 'Записи (последние 7 дней)',
      'visitTypeDistribution': 'Распределение типов посещений',
      'inPerson': 'Очно',
      'appointmentsToday': 'Записи сегодня',
      'completedToday': 'Завершено сегодня',
      'cancelledToday': 'Отменено / неявка',
      'newPatientsToday': 'Новые пациенты сегодня',
      'activePatients': 'Активные пациенты (30 дн.)',
      'documentsReceived': 'Документы получены (30 дн.)',
      'engagementSummary': 'Активность пациентов',
      'analyticsNoData': 'Нет данных',
      'extendedProfile': 'Расширенный профиль',
      'schedule': 'Расписание',
      'profession': 'Профессия',
      'selectDateHint': 'Выберите дату рождения',
      'ibanAccountNumber': 'IBAN / Номер счета',
      'taxIdVatId': 'ИНН / ИНН НДС',
      'certificateUploaded': 'Сертификат загружен',
      'uploading': 'Загрузка...',
      'minimum6Characters': 'Минимум 6 символов',
      'doctor': 'Доктор',
      'noMessages': 'Нет сообщений',

      // Account creation
      'patientAppAccess': 'Доступ к приложению для пациентов',
      'createPatientAccount': 'Создать учетную запись пациента',
      'shareCredentialsWithPatient':
          'Пожалуйста, поделитесь этими учетными данными с пациентом. Им потребуется изменить пароль при первом входе.',
      'username': 'Имя пользователя',
      'oneTimePassword': 'Одноразовый пароль',
      'forSecurityPasswordShownOnce':
          '⚠️ В целях безопасности этот пароль показывается только один раз.',
      'errorCreatingAccount': 'Ошибка при создании учетной записи',
      'copiedToClipboard': 'скопировано в буфер обмена',
      'copy': 'Копировать',
      'takePhoto': 'Сделать фото',
      'chooseFromGallery': 'Выбрать из галереи',

      // Appointments
      'patientIdNotAvailable': 'ID пациента недоступен',
      'cannotSaveNotes': 'Невозможно сохранить примечания.',
      'noItemsToSave': 'Нет элементов для сохранения. Прием завершен.',
      'appointmentEndedDocumentationSaved':
          'Прием завершен. Документация сохранена.',
      'errorSavingDocumentation': 'Ошибка при сохранении документации',
      'errorPickingImage': 'Ошибка при выборе изображения',
      'errorLoadingPatientId': 'Ошибка при загрузке ID пациента',
      'useEndAppointmentToSave':
          'Используйте "Завершить прием", чтобы сохранить всю документацию',
      'documentsFinalizeHint':
          'Итоговые записи и PDF сохраняются при завершении приёма. Новые загрузки сразу отображаются здесь.',
      'documentsEmptyHint':
          'Загруженные файлы и сканы появятся здесь для быстрого просмотра во время приёма.',
      'consultationScheduleLine': '{start} – {end} • Консультация',
      'patientIdLabel': 'ID №{id}',
      'patientAgeYears': '{age} лет',
      'appointmentStatusRequested': 'Запрошен',
      'appointmentStatusConfirmed': 'Подтверждён',
      'appointmentStatusCancelled': 'Отменён',
      'appointmentStatusCompleted': 'Завершён',
      'appointmentStatusInProgress': 'Идёт приём',
      'docSectionLaboratory': 'Лаборатория',
      'docSectionImaging': 'Визуализация',
      'docSectionClinical': 'Клиника и назначения',
      'docSectionForms': 'Формы и записи',
      'docSectionPrivate': 'Частные',
      'docSectionOther': 'Прочее',
      'docSectionUncategorized': 'Без категории',
      'consultationDocumentsDropHint':
          'Перетащите файлы сюда или нажмите для загрузки (сохраняются как медицинский документ)',
      'consultationDocumentsUploading': 'Загрузка…',
      'consultationUploadNoBytes':
          'Не удалось прочитать файл. Попробуйте меньший файл или другой формат.',
      'consultationUploadFailed': 'Ошибка загрузки',
      // Success snack uses [consultationUploadSuccess] (Russian plurals).
      'consultationUploadSuccess': 'Загружено файлов: {count}.',
      'soapNotesSectionTitle': 'Структурированные записи (SOAP)',
      'soapNotesSectionSubtitle':
          'Необязательные поля — включаются в сохранённый PDF приёма.',
      'soapSubjective': 'Субъективно',
      'soapObjective': 'Объективно',
      'soapAssessment': 'Оценка',
      'soapPlan': 'План',
      'consultationFocusModeTooltip': 'Режим крупных записей',
      'consultationFocusModeTitle': 'Заметки приёма',
      'typeANote': 'Введите примечание',
      'appointmentDocumentation': 'Документация приема',
      'docModeGeneral': 'Общий документ приема',
      'docMode0252': '025-2 Стоматологическая карта',
      'docModeDental': 'Запись стоматологического приёма',
      'dentalDocIntro':
          'Нажмите зуб (код FDI), добавьте услуги из каталога, при необходимости укажите скидку. Итог считается по строкам в одной валюте.',
      'dentalUpperJaw': 'Верхняя челюсть',
      'dentalLowerJaw': 'Нижняя челюсть',
      'dentalDiscountPercent': 'Скидка',
      'dentalClinicalNotes': 'Клинические заметки',
      'dentalSubtotal': 'Промежуточный итог',
      'dentalDiscount': 'Скидка',
      'dentalTotal': 'К оплате',
      'dentalLineItems': 'поз.',
      'dentalToothServices': 'Услуги для зуба',
      'dentalAddService': 'Добавить услугу',
      'dentalNoServices': 'Сначала задайте услуги в разделе «Услуги и цены».',
      'dentalDocSaved': 'Стоматологическая документация сохранена',
      'dentalDocSaveFailed': 'Не удалось сохранить стоматологическую документацию',
      'dentalPdfHeader': 'СТОМАТОЛОГИЧЕСКИЙ ПРИЁМ — по зубам',
      'unsavedChangesSwitch':
          'У вас есть несохраненные изменения. Сохранить перед переключением?',
      'saveAndSwitch': 'Сохранить и переключить',
      'discard': 'Отменить',
      'discardAndSwitch': 'Отменить изменения и переключить',
      'openForm0252': 'Открыть форму 025-2',
      'errorOpeningDocument': 'Ошибка при открытии документа',
      'cannotOpenDocumentUrl': 'Невозможно открыть URL документа',
      'errorSaving': 'Ошибка сохранения',
      'openRoom': 'Открыть комнату',

      // Account creation
      'accountCreated': 'Аккаунт создан',
      'accountAlreadyAvailable': 'Аккаунт уже доступен',
      'noAccountYet': 'Нет аккаунта',

      // Patients
      'chronicDiseaseUpdated': 'Хроническое заболевание обновлено',
      'chronicDisease_none': 'Нет',
      'chronicDisease_diabetesType1': 'Диабет (1 тип)',
      'chronicDisease_diabetesType2': 'Диабет (2 тип)',
      'chronicDisease_hivAids': 'ВИЧ/СПИД',
      'chronicDisease_hypertension': 'Гипертония',
      'chronicDisease_heartDisease': 'Болезнь сердца',
      'chronicDisease_chronicKidneyDisease': 'Хроническая болезнь почек',
      'chronicDisease_chronicLiverDisease': 'Хроническая болезнь печени',
      'chronicDisease_asthma': 'Астма',
      'chronicDisease_copd': 'ХОБЛ',
      'chronicDisease_cancer': 'Рак',
      'chronicDisease_epilepsy': 'Эпилепсия',
      'chronicDisease_multipleSclerosis': 'Рассеянный склероз',
      'chronicDisease_parkinsonsDisease': 'Болезнь Паркинсона',
      'chronicDisease_rheumatoidArthritis': 'Ревматоидный артрит',
      'chronicDisease_lupus': 'Волчанка',
      'chronicDisease_crohnsDisease': 'Болезнь Крона',
      'chronicDisease_ulcerativeColitis': 'Язвенный колит',
      'chronicDisease_hemophilia': 'Гемофилия',
      'chronicDisease_sickleCellDisease': 'Серповидноклеточная анемия',
      'chronicDisease_thalassemia': 'Талассемия',
      'chronicDisease_other': 'Другое',
      'failedToUpdate': 'Не удалось обновить',
      'errorLoadingSlots': 'Ошибка при загрузке слотов',
      'id': 'ID',
      'waitingRoom': 'Комната ожидания',
      'isWaiting': 'ожидает',
      'openRoomWhenReady':
          'Откройте комнату, когда будете готовы начать звонок',

      // Form 025-2 translations
      'form0252': 'Форма 025-2',
      'form0252MedicalDocument': 'Медицинский документ 025-2',
      'patientId': 'ID пациента',
      'job': 'Работа',
      'diagnosis': 'Диагноз',
      'complaints': 'Жалобы',
      'otherIllnessesAndComplications': 'Другие заболевания и осложнения',
      'moreDetailsOnAbove': 'Подробнее о вышеизложенном',
      'visualCheckup': 'Визуальный осмотр',
      'occlusionBiteType': 'Окклюзия / Тип прикуса',
      'oralCavityCondition':
          'Состояние полости рта, десен, альвеол, неба и слизистой оболочки полости рта',
      'xrayLabExaminationData':
          'Данные рентгенологического и лабораторного обследования',
      'treatment': 'Лечение',
      'treatmentResultProgress': 'Результат лечения (прогресс/динамика)',
      'recommendationsInstructions': 'Рекомендации / Инструкции',
      'returnVisits': 'Повторные визиты',
      'clinicalFindingsConclusion': 'Клинические данные и заключение',
      'doctorsSurname': 'Фамилия врача',
      'noReturnVisitsAddedYet': 'Пока нет повторных визитов',
      'addReturnVisit': 'Добавить повторный визит',
      'saveForm': 'Сохранить форму',
      'patientFormSignatureSectionTitle': 'Подпись пациента (форма 025-2)',
      'requestPatientFormSignature': 'Запросить подпись пациента',
      'patientSignaturePending':
          'Запрос подписи отправлен. Пациент получит уведомление в приложении.',
      'patientSignatureRequestSent':
          'Пациент уведомлён: нужно просмотреть и подписать форму.',
      'patientFormSignatureReceived': 'Подпись пациента получена.',
      'patientFormSignedAtPrefix': 'Дата подписи',
      'patientFormSaveAgainToRefreshPdf':
          'Сохраните форму ещё раз, чтобы обновить PDF с подписью пациента.',
      'dentalChart': 'Зубная карта',
      'icd10SearchHint': 'Поиск по коду или названию ICD-10',
      'speakToType': 'Говорить для ввода текста',
      'speechToTextRequiresPro':
          'Преобразование речи в текст доступно с подпиской Pro.',
      'transcribing': 'Распознавание…',
      'transcriptionAdded': 'Текст добавлен',
      'noSpeechDetected': 'Речь не распознана',
      'dentalLegend':
          'Легенда: K=кариес, P=пломба, пульпит, периодонтит, коронка, штифт, отсутствует, протез.',
      'toothMap': 'Карта зубов',
      'toothMapState': 'Состояние зубов',
      'willBeSetAutomaticallyOnSave':
          'Будет установлено автоматически при сохранении',
      'couldNotGetAddressDetails':
          'Не удалось получить данные адреса. Пожалуйста, попробуйте выбрать другое местоположение.',
      'errorGettingCurrentLocation':
          'Ошибка при получении текущего местоположения',
      'passwordUpdated': 'Пароль обновлен',
      'photoUpdated': 'Фото обновлено',
      'socialMediaHint': '@username или URL',
      'uploadFailed': 'Загрузка не удалась',
      'germany': 'Германия',
      'uzbekistan': 'Узбекистан',
      'usa': 'США',
      'otherCountry': 'Другое',
      'selectProfession': 'Выбрать профессию',
      'searchProfession': 'Поиск профессии...',
      'noProfessionsFound': 'Профессии не найдены',
      'region': 'Регион',
      'district': 'Район',
      'postalCode': 'Почтовый индекс',
      'streetAddress': 'Адрес улицы',
      'personalInformation': 'Личная информация',
      'workplaceInformation': 'Информация о рабочем месте',
      'clinicOrWorkplaceName': 'Название клиники / рабочего места',
      'enterClinicOrWorkplaceName':
          'Введите название вашей клиники или рабочего места',
      'enterStreetAddress': 'Введите адрес улицы, название здания, этаж и т.д.',
      'streetAddressHelper':
          'Вы можете отредактировать это поле, чтобы добавить детали здания, этаж, номер комнаты и т.д.',

      // Schedule
      'setupYourSchedule': 'Настройка расписания',
      'selectWorkingDaysAndDefineSlots':
          'Выберите рабочие дни и определите доступные временные слоты для ваших пациентов.',
      'scheduleValidFrom': 'Расписание действительно с:',
      'scheduleValidUntil': 'Расписание действительно до:',
      'existingCalendarPeriods': 'Существующие периоды календаря',
      'newPeriodMustNotOverlap':
          'Новый период не должен пересекаться ни с одним существующим.',
      'scheduleValidFromNew': 'Новый период с',
      'selectScheduleStartDate': 'Выберите дату начала расписания',
      'selectScheduleEndDate': 'Выберите дату окончания расписания',
      'scheduleSaved': 'Расписание сохранено!',
      'errorWhileSaving': 'Ошибка при сохранении',
      'scheduleOverlapsExisting':
          'Расписание пересекается с существующим расписанием',
      'existingSchedule': 'Существующее расписание',
      'newScheduleMustStartAfter': 'Новое расписание должно начинаться после',
      'newScheduleMustBeBeforeOrAfter':
          'Новое расписание должно быть полностью до (окончание) или полностью после (начало)',
      'before': 'до',
      'orAfter': 'или после',
      'failedToLoadSchedule': 'Не удалось загрузить расписание',
      'failedToLoadRules': 'Не удалось загрузить правила',
      'unauthorizedPleaseLoginAgain':
          'Не авторизован. Пожалуйста, войдите снова.',
      'endTimeMustBeAfterStartTime':
          'Время окончания должно быть после времени начала.',
      'thisTimeOverlapsExistingSlot':
          'Это время пересекается с существующим слотом.',
      'monday': 'Понедельник',
      'tuesday': 'Вторник',
      'wednesday': 'Среда',
      'thursday': 'Четверг',
      'friday': 'Пятница',
      'saturday': 'Суббота',
      'sunday': 'Воскресенье',
      'daySlots': 'слоты',
      'noSlotsYet': 'Пока нет слотов',
      'timePeriod': 'Временной период',
      'slotTimeframe': 'Временной интервал слота',
      'minutes': 'минут',
      'expandScheduleForDates': 'Расширить расписание на конкретные даты',
      'expandScheduleHint':
          'Добавьте часы после текущего расписания (напр. 17:00–23:00). Нельзя перекрывать уже заданные слоты.',
      'fromDate': 'Дата начала',
      'toDate': 'Дата окончания',
      'addExpansion': 'Добавить расширение',
      'noDateSpecificRules': 'Пока нет расширений по датам.',
      'expansionAdded': 'Расширение расписания добавлено.',
      'expandOnlyAfterExisting':
          'Время начала должно совпадать с окончанием текущего расписания. Можно добавлять только слоты после него.',
      'selectDatesToSeeSchedule': 'Выберите даты для просмотра расписания',
      'noItemsForThisDay': 'Нет элементов на этот день',
      'updateScheduleMessage':
          'Обновите расписание\nВаш календарь не предоставляет время для бронирования так далеко вперед. Пожалуйста, обновите свое расписание.',
      'goToSchedule': 'Перейти к расписанию',
      'notSelected': 'Не выбрано',
      'pleaseSelectDateFirst': 'Пожалуйста, сначала выберите дату',
      'failedToChangeSlot': 'Не удалось изменить слот',
      'patientAssigned': 'Пациент назначен',
      'failedToAssign': 'Не удалось назначить',
      'slotDetails': 'Детали слота',
      'reason': 'Причина визита',
      'assignPatient': 'Назначить пациента',
      'selected': 'Выбрано',
      'noPatientsAvailable': 'Нет доступных пациентов',
      'failedToLoadPatients':
          'Не удалось загрузить список пациентов. Попробуйте снова.',
      'choosePlace': 'Выбрать место',
      'willBeBookedAsVideoCall': 'Это будет забронировано как видеозвонок.',
      'willBeBookedAtClinic': 'Это будет забронировано по адресу клиники.',
      'dateAndTime': 'Дата и время',
      'saved': 'Сохранено',
      'clinicAddress': 'Адрес клиники',
      'showAppointments': 'Показать записи',
      'showFreeSlots': 'Показать свободные слоты',
      'dismiss': 'Закрыть',
      'setPracticeTimezoneHint':
          'Укажите часовой пояс практики в Профиле (например, Europe/Berlin), чтобы время приёмов отображалось верно.',
      'start': 'Начать',
      'today': 'Сегодня',
      'noAppointmentsToday': 'Нет записей на сегодня',
      'scheduleIsClear': 'Ваше расписание свободно',
      'allPatients': 'Все пациенты',
      'askShifaAi': 'Спросить Shifa AI',
      'fromShifaAi': 'Из Shifa AI',
      'previous': 'Назад',
      'next': 'Далее',
      'addToNotes': 'Добавить в заметки',
      'addedToNotes': 'Добавлено в заметки',
      'fromLast0252Form': 'Из последней формы 025-2',
      'loadingPatients': 'Загрузка пациентов…',
      'noPatientSelected': 'Пациент не выбран',
      'aiWillRespondHere': 'AI ответит здесь…',
      'failedToStartConversation': 'Не удалось начать беседу',
      'failedToSendMessage': 'Не удалось отправить сообщение',
      'fileAttachmentComingSoon': 'Прикрепление файлов - скоро',
      'searchDoctorsAndPatients': 'Поиск врачей и пациентов',
      'selectConversation': 'Выбрать беседу',
      'noUsersFound': 'Пользователи не найдены',
      'searchUsersPlaceholder': 'Поиск по имени, номеру, роли',
      'attachFile': 'Прикрепить файл',
      'failedToLoad': 'Не удалось загрузить',
      'createFailed': 'Не удалось создать',
      'document': 'Документ',
      'uploaded': 'загружено',
      'uploadError': 'Ошибка загрузки',
      'addAnotherPage': 'Добавить еще страницу?',
      'pagesScanned': 'Страниц отсканировано',
      'finish': 'Завершить',
      'addPage': 'Добавить страницу',
      'scannedDocument': 'Отсканированный документ',
      'pages': 'страницы',
      'pageDocument': 'страничный документ',
      'scanFailed': 'Сканирование не удалось',
      'yesCancel': 'Да, отменить',
      'failedToCancel': 'Не удалось отменить',
      'days': 'дни',
      'value': 'Значение',
      'newPatient': 'Новый пациент',
      'createNewPatient': 'Создать нового пациента',
      'createPatient': 'Создать пациента',
      'patientCreated': 'Пациент создан',
      'documentHistory': 'История документов',
      'createForm': 'Создать форму',
      'selectFormTemplate': 'Выберите шаблон формы',
      'uploadPdf': 'Загрузить PDF',
      'scanMultiPage': 'Сканировать (многостраничный)',
      'add': 'Добавить',
      'noFileData': 'Нет данных файла для загрузки',
      'cancelTask': 'Отменить задачу',
      'cancelTaskConfirm': 'Вы уверены, что хотите отменить эту задачу?',
      'taskNotFound': 'Задача не найдена',
      'noCheckInsFound': 'Отметки не найдены',
      'patientWithChronicDisease': 'Пациент с хроническим заболеванием',
      'dealingWithChronicDiseasePatient':
          'Вы имеете дело с пациентом с хроническим заболеванием:',
      'takeExtraCareForChronicDiseasePatient':
          'Пожалуйста, будьте осторожны и дважды проверьте все процедуры, лекарства и методы лечения, которые вы планируете для этого пациента.',
      'iUnderstand': 'Я понимаю',
      'city': 'Город',
    },
  };

  String translate(String key) {
    final lc = locale.languageCode;
    if (lc == 'uz' && (locale.scriptCode ?? '') == 'Cyrl') {
      final uzVal = _localizedValues['uz']?[key];
      if (uzVal != null) return transliterateUzbekLatinToCyrillicUi(uzVal);
      return _localizedValues['en']?[key] ?? key;
    }
    return _localizedValues[lc]?[key] ?? _localizedValues['en']?[key] ?? key;
  }

  /// Localized month name (1 = January, 12 = December).
  String monthName(int month) {
    if (month < 1 || month > 12) return '';
    const keys = [
      'monthJanuary',
      'monthFebruary',
      'monthMarch',
      'monthApril',
      'monthMay',
      'monthJune',
      'monthJuly',
      'monthAugust',
      'monthSeptember',
      'monthOctober',
      'monthNovember',
      'monthDecember',
    ];
    return translate(keys[month - 1]);
  }

  /// Maps stored chronic disease value (English) to localization key.
  static const Map<String, String> _chronicDiseaseKeys = {
    'None': 'chronicDisease_none',
    'Diabetes (Type 1)': 'chronicDisease_diabetesType1',
    'Diabetes (Type 2)': 'chronicDisease_diabetesType2',
    'HIV/AIDS': 'chronicDisease_hivAids',
    'Hypertension': 'chronicDisease_hypertension',
    'Heart Disease': 'chronicDisease_heartDisease',
    'Chronic Kidney Disease': 'chronicDisease_chronicKidneyDisease',
    'Chronic Liver Disease': 'chronicDisease_chronicLiverDisease',
    'Asthma': 'chronicDisease_asthma',
    'COPD': 'chronicDisease_copd',
    'Cancer': 'chronicDisease_cancer',
    'Epilepsy': 'chronicDisease_epilepsy',
    'Multiple Sclerosis': 'chronicDisease_multipleSclerosis',
    "Parkinson's Disease": 'chronicDisease_parkinsonsDisease',
    'Rheumatoid Arthritis': 'chronicDisease_rheumatoidArthritis',
    'Lupus': 'chronicDisease_lupus',
    "Crohn's Disease": 'chronicDisease_crohnsDisease',
    'Ulcerative Colitis': 'chronicDisease_ulcerativeColitis',
    'Hemophilia': 'chronicDisease_hemophilia',
    'Sickle Cell Disease': 'chronicDisease_sickleCellDisease',
    'Thalassemia': 'chronicDisease_thalassemia',
    'Other': 'chronicDisease_other',
  };

  /// Returns localized label for a chronic disease (stored value is English).
  String translateChronicDisease(String? value) {
    if (value == null || value.isEmpty) return translate('chronicDisease_none');
    final key = _chronicDiseaseKeys[value] ?? 'chronicDisease_other';
    return translate(key);
  }

  // Convenience getters for common strings
  String get appName => translate('appName');
  String get loading => translate('loading');
  String get error => translate('error');
  String get retry => translate('retry');
  String get cancel => translate('cancel');
  String get save => translate('save');
  String get delete => translate('delete');
  String get edit => translate('edit');
  String get back => translate('back');
  String get next => translate('next');
  String get complete => translate('complete');
  String get submit => translate('submit');
  String get close => translate('close');
  String get yes => translate('yes');
  String get no => translate('no');
  String get ok => translate('ok');
  String get confirm => translate('confirm');
  String get discard => translate('discard');
  String get search => translate('search');
  String get filter => translate('filter');
  String get refresh => translate('refresh');
  String get noData => translate('noData');
  String get required => translate('required');

  // Navigation
  String get chat => translate('chat');
  String get home => translate('home');
  String get calendar => translate('calendar');
  String get patients => translate('patients');
  String get tasks => translate('tasks');
  String get profile => translate('profile');
  String get signOut => translate('signOut');
  String get signOutConfirm => translate('signOutConfirm');

  // Auth
  String get login => translate('login');
  String get signIn => translate('signIn');
  String get phoneOrEmail => translate('phoneOrEmail');
  String get emailOrPhone => translate('emailOrPhone');
  String get password => translate('password');
  String get forgotPassword => translate('forgotPassword');
  String get createAccount => translate('createAccount');
  String get adminPanel => translate('adminPanel');
  String get enterEmailOrPhone => translate('enterEmailOrPhone');
  String get verify => translate('verify');
  String get oneTimeKey => translate('oneTimeKey');
  String get pleaseEnterOneTimeKey => translate('pleaseEnterOneTimeKey');
  String get keyVerified => translate('keyVerified');
  String get firstName => translate('firstName');
  String get lastName => translate('lastName');
  String get emailOptional => translate('emailOptional');
  String get confirmPassword => translate('confirmPassword');
  String get enterFirstName => translate('enterFirstName');
  String get enterLastName => translate('enterLastName');
  String get enterPhoneNumber => translate('enterPhoneNumber');
  String get pleaseVerifyInvitationKeyFirst =>
      translate('pleaseVerifyInvitationKeyFirst');
  String get accountInformation => translate('accountInformation');
  String get dateOfBirth => translate('dateOfBirth');
  String get clinic => translate('clinic');
  String get profession => translate('profession');
  String get generalPractitioner => translate('generalPractitioner');
  String get cardiologist => translate('cardiologist');
  String get dermatologist => translate('dermatologist');
  String get pediatrician => translate('pediatrician');
  String get accountCreatedPleaseSignIn =>
      translate('accountCreatedPleaseSignIn');
  String get existingPatientCreatingDoctorAccount =>
      translate('existingPatientCreatingDoctorAccount');
  String get confirmRegistration => translate('confirmRegistration');
  String get signInToManageSystem => translate('signInToManageSystem');
  String get goToDoctorLogin => translate('goToDoctorLogin');
  String adminEmailVerificationSent(String hint) =>
      translate('adminEmailVerificationSent').replaceAll('{hint}', hint);
  String get adminEnterVerificationCode => translate('adminEnterVerificationCode');
  String get adminVerifyAndSignIn => translate('adminVerifyAndSignIn');
  String get adminResendVerificationCode => translate('adminResendVerificationCode');
  String get adminChangeAccount => translate('adminChangeAccount');
  String get signInWithPhone => translate('signInWithPhone');
  String get signInWithEmail => translate('signInWithEmail');
  String get enterEmailForOtp => translate('enterEmailForOtp');
  String get enterEmail => translate('enterEmail');
  String otpSentToEmail(String email) =>
      translate('otpSentToEmail').replaceAll('{email}', email);
  String get continue_ => translate('continue');
  String get enterOtp => translate('enterOtp');
  String get resendCode => translate('resendCode');
  String resendCodeIn(String time) =>
      translate('resendCodeIn').replaceAll('{{time}}', time);
  String get tooManyRequests => translate('tooManyRequests');
  String get invalidOtp => translate('invalidOtp');
  String get resetPassword => translate('resetPassword');
  String get passwordMismatch => translate('passwordMismatch');
  String get passwordTooWeak => translate('passwordTooWeak');
  String get accessRestricted => translate('accessRestricted');
  String get accountPending => translate('accountPending');
  String get accountBlocked => translate('accountBlocked');
  String get otpSent => translate('otpSent');
  String get otpResent => translate('otpResent');
  String get otpResendHint => translate('otpResendHint');
  String get detecting => translate('detecting');
  String get practiceTimezonePlaceholder =>
      translate('practiceTimezonePlaceholder');

  // Profile
  String get editProfile => translate('editProfile');
  String get language => translate('language');
  String get settings => translate('settings');
  String get english => translate('english');
  String get uzbek => translate('uzbek');
  String get selectLanguage => translate('selectLanguage');
  String get languageChanged => translate('languageChanged');
  String get biography => translate('biography');
  String get services => translate('services');
  String get certificates => translate('certificates');
  String get telegram => translate('telegram');
  String get instagram => translate('instagram');
  String get uploadCertificate => translate('uploadCertificate');
  String get addService => translate('addService');
  String get removeService => translate('removeService');
  String get openServicesPricingToManageEntries =>
      translate('openServicesPricingToManageEntries');
  String get enterService => translate('enterService');

  // Home
  String get dashboard => translate('dashboard');
  String get today => translate('today');
  String get noAppointmentsToday => translate('noAppointmentsToday');
  String get scheduleIsClear => translate('scheduleIsClear');
  String get allPatients => translate('allPatients');
  String get todayAppointments => translate('todayAppointments');
  String get upcomingAppointments => translate('upcomingAppointments');
  String get recentPatients => translate('recentPatients');
  String get analytics => translate('analytics');

  // Calendar
  String get appointments => translate('appointments');
  String get freeSlots => translate('freeSlots');
  String get date => translate('date');
  String get time => translate('time');
  String get duration => translate('duration');
  String get place => translate('place');
  String get changeSlot => translate('changeSlot');
  String get cancelAppointment => translate('cancelAppointment');
  String get cancelConfirm => translate('cancelConfirm');
  String get appointmentCancelled => translate('appointmentCancelled');
  String get slotChanged => translate('slotChanged');
  String get selectDate => translate('selectDate');
  String get selectTime => translate('selectTime');
  String get availableSlots => translate('availableSlots');
  String get noSlotsAvailable => translate('noSlotsAvailable');
  String get noAppointments => translate('noAppointments');
  String get noFreeSlots => translate('noFreeSlots');

  // Patients
  String get patient => translate('patient');
  String get patientList => translate('patientList');
  String get searchPatients => translate('searchPatients');
  String get noPatientsFound => translate('noPatientsFound');
  String get patientDetails => translate('patientDetails');
  String get generalInformation => translate('generalInformation');
  String get documents => translate('documents');
  String get chronicDisease => translate('chronicDisease');
  String get selectChronicDisease => translate('selectChronicDisease');
  String get noChronicDisease => translate('noChronicDisease');
  String get chronicDiseaseWarning => translate('chronicDiseaseWarning');
  String get createTask => translate('createTask');
  String get assignResult => translate('assignResult');
  String get startAppointment => translate('startAppointment');
  String get start => translate('start');
  String get phoneNumber => translate('phoneNumber');
  String get optional => translate('optional');
  String get email => translate('email');
  String get address => translate('address');
  String get location => translate('location');
  String get latitude => translate('latitude');
  String get longitude => translate('longitude');
  String get getCurrentLocation => translate('getCurrentLocation');
  String get saveLocation => translate('saveLocation');
  String get locationSaved => translate('locationSaved');
  String get invalidCoordinates => translate('invalidCoordinates');
  String get locationFeatureComingSoon =>
      translate('locationFeatureComingSoon');
  String get selectLocationOnMap => translate('selectLocationOnMap');
  String get currentLocation => translate('currentLocation');
  String get addressFromCoordinates => translate('addressFromCoordinates');
  String get coordinatesFromAddress => translate('coordinatesFromAddress');
  String get birthDate => translate('birthDate');
  String get gender => translate('gender');
  String get male => translate('male');
  String get female => translate('female');
  String get other => translate('other');

  // Tasks
  String get remoteCareTasks => translate('remoteCareTasks');
  String get createRemoteCareTask => translate('createRemoteCareTask');
  String get taskName => translate('taskName');
  String get description => translate('description');
  String get category => translate('category');
  String get vital => translate('vital');
  String get exercise => translate('exercise');
  String get medication => translate('medication');
  String get taskOther => translate('taskOther');
  String get timesPerDay => translate('timesPerDay');
  String get morningTime => translate('morningTime');
  String get afternoonTime => translate('afternoonTime');
  String get eveningTime => translate('eveningTime');
  String get startDate => translate('startDate');
  String get startTime => translate('startTime');
  String get endDate => translate('endDate');
  String get durationDays => translate('durationDays');
  String get useEndDate => translate('useEndDate');
  String get inputType => translate('inputType');
  String get numeric => translate('numeric');
  String get text => translate('text');
  String get boolean => translate('boolean');
  String get inputLabel => translate('inputLabel');
  String get notesRequired => translate('notesRequired');
  String get notesLabel => translate('notesLabel');
  String get taskCreated => translate('taskCreated');
  String get taskUpdated => translate('taskUpdated');
  String get taskCancelled => translate('taskCancelled');
  String get failedToCreateTask => translate('failedToCreateTask');
  String get failedToUpdateTask => translate('failedToUpdateTask');
  String get selectPatient => translate('selectPatient');
  String get tapToSearch => translate('tapToSearch');
  String get searchByNameOrId => translate('searchByNameOrId');
  String get taskDetails => translate('taskDetails');
  String get progress => translate('progress');
  String get checkInCompleted => translate('checkInCompleted');
  String get pending => translate('pending');
  String get missed => translate('missed');
  String get checkIns => translate('checkIns');
  String get status => translate('status');
  String get active => translate('active');
  String get taskCompleted => translate('taskCompleted');
  String get expired => translate('expired');
  String get taskStatusCancelled => translate('taskStatusCancelled');
  String get draft => translate('draft');
  String get all => translate('all');
  String get noTasksFound => translate('noTasksFound');
  String get taskDescription => translate('taskDescription');
  String get enterTaskName => translate('enterTaskName');
  String get enterInputLabel => translate('enterInputLabel');
  String get enterNotesLabel => translate('enterNotesLabel');
  String get notSet => translate('notSet');

  // Documents
  String get uploadDocument => translate('uploadDocument');
  String get documentTitle => translate('documentTitle');
  String get enterDocumentTitle => translate('enterDocumentTitle');
  String get selectFile => translate('selectFile');
  String get documentUploaded => translate('documentUploaded');
  String get uploadFailed => translate('uploadFailed');
  String get noDocuments => translate('noDocuments');
  String get openDocument => translate('openDocument');
  String get requestAccess => translate('requestAccess');
  String get documentLocked => translate('documentLocked');
  String get uploadedBy => translate('uploadedBy');

  // Appointments
  String get appointmentDetails => translate('appointmentDetails');
  String get patientName => translate('patientName');
  String get appointmentDate => translate('appointmentDate');
  String get appointmentTime => translate('appointmentTime');
  String get appointmentPlace => translate('appointmentPlace');
  String get videoCall => translate('videoCall');
  String get inClinic => translate('inClinic');
  String get notes => translate('notes');
  String get enterNotes => translate('enterNotes');
  String get beforeTreatment => translate('beforeTreatment');
  String get afterTreatment => translate('afterTreatment');
  String get startAiNotes => translate('startAiNotes');
  String get recordingForAiNotes => translate('recordingForAiNotes');
  String get processRecording => translate('processRecording');
  String get aiNotesUploaded => translate('aiNotesUploaded');
  String get aiNotesNotReadyTryLater => translate('aiNotesNotReadyTryLater');
  String get uploadPhoto => translate('uploadPhoto');
  String get endAppointment => translate('endAppointment');
  String get appointmentEnded => translate('appointmentEnded');
  String get documentGenerated => translate('documentGenerated');
  String get viewDocument => translate('viewDocument');
  String get requestSignature => translate('requestSignature');
  String get waitingForPatientSignature =>
      translate('waitingForPatientSignature');
  String get patientSigned => translate('patientSigned');
  String get signatureRequestSent => translate('signatureRequestSent');

  // Chat
  String get messages => translate('messages');
  String get typeMessage => translate('typeMessage');
  String get send => translate('send');
  String get noConversations => translate('noConversations');
  String get isTyping => translate('isTyping');
  String get selectConversation => translate('selectConversation');
  String get attachFile => translate('attachFile');
  String get selectImage => translate('selectImage');
  String get recordVoice => translate('recordVoice');
  String get voiceMessage => translate('voiceMessage');
  String get sendVoice => translate('sendVoice');
  String get compressingImage => translate('compressingImage');
  String get uploadingFile => translate('uploadingFile');
  String get errorUploadingFile => translate('errorUploadingFile');
  String get errorRecordingVoice => translate('errorRecordingVoice');
  String get selectDocument => translate('selectDocument');
  String get selectFormTemplate => translate('selectFormTemplate');
  String get voiceRecordingNotSupportedOnWeb =>
      translate('voiceRecordingNotSupportedOnWeb');
  String get microphonePermissionDenied =>
      translate('microphonePermissionDenied');

  // Errors & Validation
  String get invalidEmail => translate('invalidEmail');
  String get invalidPhone => translate('invalidPhone');
  String get passwordTooShort => translate('passwordTooShort');
  String get passwordsDoNotMatch => translate('passwordsDoNotMatch');
  String get fieldRequired => translate('fieldRequired');
  String get pleaseSelectPatient => translate('pleaseSelectPatient');
  String get pleaseEnterTaskName => translate('pleaseEnterTaskName');
  String get unauthorized => translate('unauthorized');
  String get networkError => translate('networkError');
  String get unknownError => translate('unknownError');

  // Notifications
  String get notifications => translate('notifications');
  String get noNotifications => translate('noNotifications');
  String get markAllAsRead => translate('markAllAsRead');
  String get approve => translate('approve');
  String get reject => translate('reject');
  String get approved => translate('approved');
  String get rejected => translate('rejected');
  String get document => translate('document');
  String get documentAccessApproved => translate('documentAccessApproved');
  String get documentAccessRejected => translate('documentAccessRejected');
  String get documentAccessRequest => translate('documentAccessRequest');
  String get documentAccessRequestDetail =>
      translate('documentAccessRequestDetail');
  String get documentAccessApprovedDetail =>
      translate('documentAccessApprovedDetail');
  String get documentAccessRejectedDetail =>
      translate('documentAccessRejectedDetail');
  String get notificationGeneric => translate('notificationGeneric');
  String get somethingWentWrong => translate('somethingWentWrong');
  String get imageNotAvailable => translate('imageNotAvailable');
  String get failedToLoadImage => translate('failedToLoadImage');
  String get requestAccessSent => translate('requestAccessSent');
  String get notificationFilterAll => translate('notificationFilterAll');
  String get notificationFilterAppointments =>
      translate('notificationFilterAppointments');
  String get notificationFilterTasks => translate('notificationFilterTasks');
  String get notificationFilterMessages =>
      translate('notificationFilterMessages');
  String get notificationSettings => translate('notificationSettings');
  String get notificationViewResult => translate('notificationViewResult');
  String get notificationViewAppointment =>
      translate('notificationViewAppointment');
  String get notificationOpenCalendar => translate('notificationOpenCalendar');
  String get notificationReschedule => translate('notificationReschedule');
  String get notificationEmptyFilter => translate('notificationEmptyFilter');
  String get notificationEmptyFilterHint =>
      translate('notificationEmptyFilterHint');
  String get notificationEmptyBody => translate('notificationEmptyBody');
  String get timeJustNow => translate('timeJustNow');
  String timeMinAgo(int n) =>
      translate('timeMinAgo').replaceAll('{n}', n.toString());
  String timeYesterday(String time) =>
      translate('timeYesterday').replaceAll('{time}', time);
  String get notificationYesterday => translate('yesterday');
  String monthShort(int month) {
    if (month < 1 || month > 12) return '';
    const keys = [
      'monthJan',
      'monthFeb',
      'monthMar',
      'monthApr',
      'monthMay',
      'monthJun',
      'monthJul',
      'monthAug',
      'monthSep',
      'monthOct',
      'monthNov',
      'monthDec',
    ];
    return translate(keys[month - 1]);
  }

  String get notificationTypeAppointmentBooked =>
      translate('notificationTypeAppointmentBooked');
  String get notificationTypeAppointmentCancelled =>
      translate('notificationTypeAppointmentCancelled');
  String get notificationTypeTaskCompleted =>
      translate('notificationTypeTaskCompleted');
  String get notificationTypeTaskAssigned =>
      translate('notificationTypeTaskAssigned');
  String get notificationTypeDocumentAccessRequest =>
      translate('notificationTypeDocumentAccessRequest');
  String get notificationTypeDocumentAccessApproved =>
      translate('notificationTypeDocumentAccessApproved');
  String get notificationTypeDocumentAccessRejected =>
      translate('notificationTypeDocumentAccessRejected');
  String get notificationTypeAiScribeReady =>
      translate('notificationTypeAiScribeReady');
  String notificationMessagePatientBookedAppointment(String name) => translate(
    'notificationMessagePatientBookedAppointment',
  ).replaceAll('{name}', name);
  String get notificationMessageAppointmentReminder =>
      translate('notificationMessageAppointmentReminder');
  String get patientBriefingTitle => translate('patientBriefingTitle');
  String get patientBriefingError => translate('patientBriefingError');
  String patientBriefingSources(int n) =>
      translate('patientBriefingSources').replaceAll('{n}', n.toString());
  String patientBriefingSourcesWithAppointments(int docs, int appts) =>
      translate('patientBriefingSourcesWithAppointments')
          .replaceAll('{docs}', docs.toString())
          .replaceAll('{appts}', appts.toString());
  String patientBriefingSourcesAppointmentsOnly(int n) => translate(
    'patientBriefingSourcesAppointmentsOnly',
  ).replaceAll('{n}', n.toString());
  String get patientBriefingCopied => translate('patientBriefingCopied');
  String get patientBriefingCopy => translate('patientBriefingCopy');
  String get generateBriefing => translate('generateBriefing');
  String get patientBriefingGenerating =>
      translate('patientBriefingGenerating');
  String get showAiAndFormNotes => translate('showAiAndFormNotes');
  String get hideAiAndFormNotes => translate('hideAiAndFormNotes');
  String get notesSectionsHidden => translate('notesSectionsHidden');

  // Chronic Diseases
  String get aids => translate('aids');
  String get diabetes => translate('diabetes');
  String get hypertension => translate('hypertension');
  String get heartDisease => translate('heartDisease');
  String get cancer => translate('cancer');
  String get kidneyDisease => translate('kidneyDisease');
  String get liverDisease => translate('liverDisease');
  String get asthma => translate('asthma');
  String get copd => translate('copd');
  String get epilepsy => translate('epilepsy');

  // Additional
  String get appointmentsLast7Days => translate('appointmentsLast7Days');
  String get visitTypeDistribution => translate('visitTypeDistribution');
  String get inPerson => translate('inPerson');
  String get appointmentsToday => translate('appointmentsToday');
  String get completedToday => translate('completedToday');
  String get cancelledToday => translate('cancelledToday');
  String get newPatientsToday => translate('newPatientsToday');
  String get activePatients => translate('activePatients');
  String get documentsReceived => translate('documentsReceived');
  String get engagementSummary => translate('engagementSummary');
  String get analyticsNoData => translate('analyticsNoData');
  String get extendedProfile => translate('extendedProfile');
  String get schedule => translate('schedule');
  String get selectDateHint => translate('selectDateHint');
  String get ibanAccountNumber => translate('ibanAccountNumber');
  String get taxIdVatId => translate('taxIdVatId');
  String get certificateUploaded => translate('certificateUploaded');
  String get uploading => translate('uploading');
  String get minimum6Characters => translate('minimum6Characters');
  String get doctor => translate('doctor');
  String get noMessages => translate('noMessages');

  // Account creation
  String get patientAppAccess => translate('patientAppAccess');
  String get createPatientAccount => translate('createPatientAccount');
  String get shareCredentialsWithPatient =>
      translate('shareCredentialsWithPatient');
  String get username => translate('username');
  String get oneTimePassword => translate('oneTimePassword');
  String get forSecurityPasswordShownOnce =>
      translate('forSecurityPasswordShownOnce');
  String get errorCreatingAccount => translate('errorCreatingAccount');
  String get copiedToClipboard => translate('copiedToClipboard');
  String get copy => translate('copy');
  String get takePhoto => translate('takePhoto');
  String get chooseFromGallery => translate('chooseFromGallery');

  // Appointments
  String get patientIdNotAvailable => translate('patientIdNotAvailable');
  String get cannotSaveNotes => translate('cannotSaveNotes');
  String get noItemsToSave => translate('noItemsToSave');
  String get appointmentEndedDocumentationSaved =>
      translate('appointmentEndedDocumentationSaved');
  String get errorSavingDocumentation => translate('errorSavingDocumentation');
  String get errorPickingImage => translate('errorPickingImage');
  String get errorLoadingPatientId => translate('errorLoadingPatientId');
  String get useEndAppointmentToSave => translate('useEndAppointmentToSave');
  String get documentsFinalizeHint => translate('documentsFinalizeHint');
  String get documentsEmptyHint => translate('documentsEmptyHint');

  String consultationScheduleLine(String start, String end) => translate(
        'consultationScheduleLine',
      ).replaceAll('{start}', start).replaceAll('{end}', end);

  String patientIdLabel(String id) =>
      translate('patientIdLabel').replaceAll('{id}', id);

  String patientAgeYears(int age) =>
      translate('patientAgeYears').replaceAll('{age}', age.toString());

  String get appointmentStatusRequested =>
      translate('appointmentStatusRequested');
  String get appointmentStatusConfirmed =>
      translate('appointmentStatusConfirmed');
  String get appointmentStatusCancelled =>
      translate('appointmentStatusCancelled');
  String get appointmentStatusCompleted =>
      translate('appointmentStatusCompleted');
  String get appointmentStatusInProgress =>
      translate('appointmentStatusInProgress');

  String get docSectionLaboratory => translate('docSectionLaboratory');
  String get docSectionImaging => translate('docSectionImaging');
  String get docSectionClinical => translate('docSectionClinical');
  String get docSectionForms => translate('docSectionForms');
  String get docSectionPrivate => translate('docSectionPrivate');
  String get docSectionOther => translate('docSectionOther');
  String get docSectionUncategorized => translate('docSectionUncategorized');

  String get consultationDocumentsDropHint =>
      translate('consultationDocumentsDropHint');
  String get consultationDocumentsUploading =>
      translate('consultationDocumentsUploading');
  String get consultationUploadNoBytes =>
      translate('consultationUploadNoBytes');
  String get consultationUploadFailed => translate('consultationUploadFailed');

  /// Upload confirmation after consultation document upload (idiomatic per locale).
  String consultationUploadSuccess(int count) {
    switch (locale.languageCode) {
      case 'ru':
        return _ruConsultationUploadSuccess(count);
      case 'uz':
        return '$count ta fayl muvaffaqiyatli yuklandi.';
      case 'en':
        final noun = count == 1 ? 'file' : 'files';
        return 'Uploaded $count $noun.';
      default:
        final noun = count == 1 ? 'file' : 'files';
        return 'Uploaded $count $noun.';
    }
  }

  String _ruConsultationUploadSuccess(int n) {
    final mod10 = n % 10;
    final mod100 = n % 100;
    if (mod10 == 1 && mod100 != 11) {
      return 'Загружен $n файл.';
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
      return 'Загружено $n файла.';
    }
    return 'Загружено $n файлов.';
  }

  String get soapNotesSectionTitle => translate('soapNotesSectionTitle');
  String get soapNotesSectionSubtitle => translate('soapNotesSectionSubtitle');
  String get soapSubjective => translate('soapSubjective');
  String get soapObjective => translate('soapObjective');
  String get soapAssessment => translate('soapAssessment');
  String get soapPlan => translate('soapPlan');
  String get consultationFocusModeTooltip =>
      translate('consultationFocusModeTooltip');
  String get consultationFocusModeTitle =>
      translate('consultationFocusModeTitle');

  String get typeANote => translate('typeANote');
  String get appointmentDocumentation => translate('appointmentDocumentation');
  String get docModeGeneral => translate('docModeGeneral');
  String get docMode0252 => translate('docMode0252');
  String get docModeDental => translate('docModeDental');
  String get unsavedChangesSwitch => translate('unsavedChangesSwitch');
  String get saveAndSwitch => translate('saveAndSwitch');
  String get discardAndSwitch => translate('discardAndSwitch');
  String get openForm0252 => translate('openForm0252');
  String get errorOpeningDocument => translate('errorOpeningDocument');
  String get cannotOpenDocumentUrl => translate('cannotOpenDocumentUrl');
  String get errorSaving => translate('errorSaving');
  String get openRoom => translate('openRoom');
  String get fromShifaAi => translate('fromShifaAi');
  String get fromLast0252Form => translate('fromLast0252Form');

  // Account creation
  String get accountCreated => translate('accountCreated');
  String get accountAlreadyAvailable => translate('accountAlreadyAvailable');
  String get noAccountYet => translate('noAccountYet');

  // Patients
  String get chronicDiseaseUpdated => translate('chronicDiseaseUpdated');
  String get failedToUpdate => translate('failedToUpdate');
  String get errorLoadingSlots => translate('errorLoadingSlots');
  String get id => translate('id');
  String get waitingRoom => translate('waitingRoom');
  String get isWaiting => translate('isWaiting');
  String get openRoomWhenReady => translate('openRoomWhenReady');

  // Form 025-2 getters
  String get form0252 => translate('form0252');
  String get form0252MedicalDocument => translate('form0252MedicalDocument');
  String get patientId => translate('patientId');
  String get fullName => translate('fullName');
  String get age => translate('age');
  String get job => translate('job');
  String get diagnosis => translate('diagnosis');
  String get complaints => translate('complaints');
  String get otherIllnessesAndComplications =>
      translate('otherIllnessesAndComplications');
  String get moreDetailsOnAbove => translate('moreDetailsOnAbove');
  String get visualCheckup => translate('visualCheckup');
  String get occlusionBiteType => translate('occlusionBiteType');
  String get oralCavityCondition => translate('oralCavityCondition');
  String get xrayLabExaminationData => translate('xrayLabExaminationData');
  String get treatment => translate('treatment');
  String get treatmentResultProgress => translate('treatmentResultProgress');
  String get recommendationsInstructions =>
      translate('recommendationsInstructions');
  String get returnVisits => translate('returnVisits');
  String get clinicalFindingsConclusion =>
      translate('clinicalFindingsConclusion');
  String get doctorsSurname => translate('doctorsSurname');
  String get noReturnVisitsAddedYet => translate('noReturnVisitsAddedYet');
  String get addReturnVisit => translate('addReturnVisit');
  String get saveForm => translate('saveForm');
  String get dentalChart => translate('dentalChart');
  String get toothMap => translate('toothMap');
  String get toothMapState => translate('toothMapState');
  String get willBeSetAutomaticallyOnSave =>
      translate('willBeSetAutomaticallyOnSave');

  // Additional getters for new keys
  String get city => translate('city');
  String get region => translate('region');
  String get district => translate('district');
  String get postalCode => translate('postalCode');
  String get streetAddress => translate('streetAddress');
  String get personalInformation => translate('personalInformation');
  String get workplaceInformation => translate('workplaceInformation');
  String get clinicOrWorkplaceName => translate('clinicOrWorkplaceName');
  String get enterClinicOrWorkplaceName =>
      translate('enterClinicOrWorkplaceName');
  String get enterStreetAddress => translate('enterStreetAddress');
  String get streetAddressHelper => translate('streetAddressHelper');
  String get couldNotGetAddressDetails =>
      translate('couldNotGetAddressDetails');
  String get errorGettingCurrentLocation =>
      translate('errorGettingCurrentLocation');
  String get passwordUpdated => translate('passwordUpdated');
  String get photoUpdated => translate('photoUpdated');
  String get germany => translate('germany');
  String get uzbekistan => translate('uzbekistan');
  String get usa => translate('usa');
  String get otherCountry => translate('otherCountry');
  String get russian => translate('russian');
  String get german => translate('german');
  String get selectProfession => translate('selectProfession');
  String get searchProfession => translate('searchProfession');
  String get noProfessionsFound => translate('noProfessionsFound');

  // Schedule
  String get setupYourSchedule => translate('setupYourSchedule');
  String get selectWorkingDaysAndDefineSlots =>
      translate('selectWorkingDaysAndDefineSlots');
  String get scheduleValidFrom => translate('scheduleValidFrom');
  String get scheduleValidUntil => translate('scheduleValidUntil');
  String get existingCalendarPeriods => translate('existingCalendarPeriods');
  String get newPeriodMustNotOverlap => translate('newPeriodMustNotOverlap');
  String get scheduleValidFromNew => translate('scheduleValidFromNew');
  String get selectScheduleStartDate => translate('selectScheduleStartDate');
  String get selectScheduleEndDate => translate('selectScheduleEndDate');
  String get scheduleSaved => translate('scheduleSaved');
  String get errorWhileSaving => translate('errorWhileSaving');
  String get scheduleOverlapsExisting => translate('scheduleOverlapsExisting');
  String get existingSchedule => translate('existingSchedule');
  String get newScheduleMustStartAfter =>
      translate('newScheduleMustStartAfter');
  String get newScheduleMustBeBeforeOrAfter =>
      translate('newScheduleMustBeBeforeOrAfter');
  String get before => translate('before');
  String get orAfter => translate('orAfter');
  String get failedToLoadSchedule => translate('failedToLoadSchedule');
  String get failedToLoadRules => translate('failedToLoadRules');
  String get unauthorizedPleaseLoginAgain =>
      translate('unauthorizedPleaseLoginAgain');
  String get endTimeMustBeAfterStartTime =>
      translate('endTimeMustBeAfterStartTime');
  String get thisTimeOverlapsExistingSlot =>
      translate('thisTimeOverlapsExistingSlot');
  String get monday => translate('monday');
  String get tuesday => translate('tuesday');
  String get wednesday => translate('wednesday');
  String get thursday => translate('thursday');
  String get friday => translate('friday');
  String get saturday => translate('saturday');
  String get sunday => translate('sunday');
  String get daySlots => translate('daySlots');
  String get noSlotsYet => translate('noSlotsYet');
  String get timePeriod => translate('timePeriod');
  String get slotTimeframe => translate('slotTimeframe');
  String get remove => translate('remove');
  String get add => translate('add');
  String get minutes => translate('minutes');
  String get expandScheduleForDates => translate('expandScheduleForDates');
  String get expandScheduleHint => translate('expandScheduleHint');
  String get fromDate => translate('fromDate');
  String get toDate => translate('toDate');
  String get addExpansion => translate('addExpansion');
  String get noDateSpecificRules => translate('noDateSpecificRules');
  String get expansionAdded => translate('expansionAdded');
  String get expandOnlyAfterExisting => translate('expandOnlyAfterExisting');
  String get failedToStartConversation =>
      translate('failedToStartConversation');
  String get failedToSendMessage => translate('failedToSendMessage');
  String get failedToStartVideoCall => translate('failedToStartVideoCall');
  String get videoCallAvailableFiveMinBefore =>
      translate('videoCallAvailableFiveMinBefore');
  String get videoCallTooLateAfterOneHour =>
      translate('videoCallTooLateAfterOneHour');
  String get videoCallEnded => translate('videoCallEnded');
  String get callErrorOccurred => translate('callErrorOccurred');
  String get waitingForParticipants => translate('waitingForParticipants');
  String get joinVideoCall => translate('joinVideoCall');
  String get videoCallReady => translate('videoCallReady');
  String get videoCallErrorTitle => translate('videoCallErrorTitle');
  String get videoCallConnecting => translate('videoCallConnecting');
  String get videoCallNotAvailableShort =>
      translate('videoCallNotAvailableShort');
  String get videoCallJoinWindowClosedMessage =>
      translate('videoCallJoinWindowClosedMessage');
  String get videoCallNotYetAvailableMessage =>
      translate('videoCallNotYetAvailableMessage');
  String get videoCallPaymentRequiredMessage =>
      translate('videoCallPaymentRequiredMessage');
  String get clickBelowToJoinCall => translate('clickBelowToJoinCall');
  String get tokenRevoked => translate('tokenRevoked');
  String get tokenRegenerated => translate('tokenRegenerated');
  String get configUpdated => translate('configUpdated');
  String get noContact => translate('noContact');
  String get addRow => translate('addRow');
  String get removeRow => translate('removeRow');
  String get editRow => translate('editRow');
  String get collapse => translate('collapse');
  String get expand => translate('expand');
  String get socialMediaHint => translate('socialMediaHint');
  String get profileInformation => translate('profileInformation');
  String get contactDetails => translate('contactDetails');
  String get paymentAndInvoicing => translate('paymentAndInvoicing');
  String get profileInformationSaved => translate('profileInformationSaved');
  String get contactDetailsSaved => translate('contactDetailsSaved');
  String get paymentAndInvoicingSaved => translate('paymentAndInvoicingSaved');
  String get extendedProfileSaved => translate('extendedProfileSaved');
  String get settingsSubtitle => translate('settingsSubtitle');
  String get extendedProfileSubtitle => translate('extendedProfileSubtitle');
  String get updateOrChangeSchedule => translate('updateOrChangeSchedule');
  String get changeOrResetPassword => translate('changeOrResetPassword');
  String get newPasswordConfirmationMismatchError =>
      translate('newPasswordConfirmationMismatchError');
  String get passwordUpdatedSuccessfully =>
      translate('passwordUpdatedSuccessfully');
  String get phone => translate('phone');
  String get billingName => translate('billingName');
  String get billingEmail => translate('billingEmail');
  String get currentPassword => translate('currentPassword');
  String get currentPasswordIsRequired =>
      translate('currentPasswordIsRequired');
  String get newPassword => translate('newPassword');
  String get confirmNewPassword => translate('confirmNewPassword');
  String get pleaseConfirmNewPasswordError =>
      translate('pleaseConfirmNewPasswordError');
  String get settingsSaved => translate('settingsSaved');
  String get formSavedSuccessfully => translate('formSavedSuccessfully');
  String get errorSavingForm => translate('errorSavingForm');
  String get pdfGeneratedPrintingNotImplemented =>
      translate('pdfGeneratedPrintingNotImplemented');
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) {
    return ['en', 'uz', 'ru'].contains(locale.languageCode);
  }

  @override
  Future<AppLocalizations> load(Locale locale) async {
    return AppLocalizations(locale);
  }

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}
