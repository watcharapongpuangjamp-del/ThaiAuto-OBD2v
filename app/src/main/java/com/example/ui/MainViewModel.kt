package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AppLanguage
import com.example.data.model.AppOperationMode
import com.example.data.model.ObdConnectionType
import com.example.data.model.ObdProtocol
import com.example.data.model.TerminalLogEntry
import com.example.data.model.UsbConnectionStats
import com.example.data.model.UsbSerialDeviceInfo
import com.example.service.UsbObdDriver
import com.example.data.model.ChatMessage
import com.example.data.model.DiagnosticSystem
import com.example.data.model.DtcCategory
import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import com.example.data.model.FreezeFrameData
import com.example.data.model.LiveSensorData
import com.example.data.model.MaintenanceLog
import com.example.data.model.MessageSender
import com.example.data.model.PredictiveVehicleHealthSummary
import com.example.data.model.ScanRecord
import com.example.data.model.ThaiCarSpec
import com.example.data.model.Vehicle
import com.example.data.model.UiCustomizationState
import com.example.data.model.NeonAccentPreset
import com.example.data.model.GaugeFontPreset
import com.example.data.repository.AiMechanicRepository
import com.example.data.repository.MaintenanceHistoryRepository
import com.example.data.repository.DtcDatabase
import com.example.data.repository.PredictiveMaintenanceEngine
import com.example.data.repository.ThaiCarDatabase
import com.example.service.DrivingMode
import com.example.service.NotificationHelper
import com.example.service.Obd2EmulatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.repository.SystemLoggerRepository

import com.example.service.VoiceAlertService

enum class PerfTestState {
    IDLE, READY, ACCELERATING, COMPLETED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _perfTestState = MutableStateFlow<PerfTestState>(PerfTestState.IDLE)
    val perfTestState: StateFlow<PerfTestState> = _perfTestState.asStateFlow()

    private val _perf0to100Time = MutableStateFlow<Float?>(null)
    val perf0to100Time: StateFlow<Float?> = _perf0to100Time.asStateFlow()

    private var perfTestStartTime: Long = 0L

    private val db = AppDatabase.getDatabase(application)
    private val vehicleDao = db.vehicleDao()
    private val maintenanceDao = db.maintenanceDao()
    private val scanRecordDao = db.scanRecordDao()
    private val dtcDao = db.dtcDao()
    val systemLogger = SystemLoggerRepository(db.systemLoggerDao())
    private val aiRepository = AiMechanicRepository()
    val maintenanceHistoryRepository = MaintenanceHistoryRepository(application)
    val emulatorService = Obd2EmulatorService(viewModelScope)
    val usbDriver = UsbObdDriver(application, viewModelScope)
    val voiceAlertService = VoiceAlertService(application)

    // Telemetry Snapshots & System Logs StateFlows
    val telemetrySnapshots = systemLogger.telemetrySnapshots
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val systemLogs = systemLogger.systemLogs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _showSnapshotDialog = MutableStateFlow(false)
    val showSnapshotDialog: StateFlow<Boolean> = _showSnapshotDialog.asStateFlow()

    private val _showSystemLogsDialog = MutableStateFlow(false)
    val showSystemLogsDialog: StateFlow<Boolean> = _showSystemLogsDialog.asStateFlow()

    private val _isVoiceAlertEnabled = MutableStateFlow(true)
    val isVoiceAlertEnabled: StateFlow<Boolean> = _isVoiceAlertEnabled.asStateFlow()

    // Strict App Operation Mode (Real Hardware vs Simulator Demo)
    private val _operationMode = MutableStateFlow(AppOperationMode.REAL_HARDWARE)
    val operationMode: StateFlow<AppOperationMode> = _operationMode.asStateFlow()

    // OBD Connection Mode State
    private val _connectionType = MutableStateFlow(ObdConnectionType.USB_OTG)
    val connectionType: StateFlow<ObdConnectionType> = _connectionType.asStateFlow()

    // USB OBD Driver States
    val availableUsbDevices: StateFlow<List<UsbSerialDeviceInfo>> = usbDriver.availableUsbDevices
    val selectedUsbDevice: StateFlow<UsbSerialDeviceInfo?> = usbDriver.selectedDevice
    val selectedBaudRate: StateFlow<Int> = usbDriver.selectedBaudRate
    val selectedProtocol: StateFlow<ObdProtocol> = usbDriver.selectedProtocol
    val isUsbConnected: StateFlow<Boolean> = usbDriver.isUsbConnected
    val isUsbConnecting: StateFlow<Boolean> = usbDriver.isConnecting
    val usbStats: StateFlow<UsbConnectionStats> = usbDriver.connectionStats
    val terminalLogs: StateFlow<List<TerminalLogEntry>> = usbDriver.terminalLogs
    val lastAtResponse: StateFlow<String> = usbDriver.lastAtResponse

    // Language State
    private val _language = MutableStateFlow(AppLanguage.TH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // Theme Mode State
    private val _themeMode = MutableStateFlow(com.example.data.model.AppThemeMode.AUTO_TIME)
    val themeMode: StateFlow<com.example.data.model.AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: com.example.data.model.AppThemeMode) {
        _themeMode.value = mode
    }

    // Cyberpunk UI Customization State
    private val _uiCustomizationState = MutableStateFlow(UiCustomizationState())
    val uiCustomizationState: StateFlow<UiCustomizationState> = _uiCustomizationState.asStateFlow()

    fun updateNeonPreset(preset: NeonAccentPreset) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(preset = preset)
    }

    fun updateCustomHexColors(primaryHex: String, secondaryHex: String) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(
            customPrimaryColorHex = primaryHex,
            customSecondaryColorHex = secondaryHex,
            preset = NeonAccentPreset.CUSTOM_RGB
        )
    }

    fun updateGaugeFont(fontPreset: GaugeFontPreset) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(gaugeFont = fontPreset)
    }

    fun updateGaugeFontScale(scale: Float) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(gaugeFontScale = scale)
    }

    fun updateGlowIntensity(intensity: Float) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(glowIntensity = intensity)
    }

    fun reorderWidgetGrid(newOrder: List<String>) {
        _uiCustomizationState.value = _uiCustomizationState.value.copy(widgetGridOrder = newOrder)
    }

    fun toggleWidgetVisibility(widgetId: String) {
        val current = _uiCustomizationState.value.hiddenWidgetIds.toMutableSet()
        if (current.contains(widgetId)) {
            current.remove(widgetId)
        } else {
            current.add(widgetId)
        }
        _uiCustomizationState.value = _uiCustomizationState.value.copy(hiddenWidgetIds = current)
    }

    // Google Drive Cloud Sync State
    private val _googleDriveConnected = MutableStateFlow(false)
    val googleDriveConnected: StateFlow<Boolean> = _googleDriveConnected.asStateFlow()

    private val _googleDriveAccountEmail = MutableStateFlow("")
    val googleDriveAccountEmail: StateFlow<String> = _googleDriveAccountEmail.asStateFlow()

    private val _isGoogleDriveSyncing = MutableStateFlow(false)
    val isGoogleDriveSyncing: StateFlow<Boolean> = _isGoogleDriveSyncing.asStateFlow()

    private val _lastCloudBackupTime = MutableStateFlow("—")
    val lastCloudBackupTime: StateFlow<String> = _lastCloudBackupTime.asStateFlow()

    // Measurement Unit System State
    private val _speedUnit = MutableStateFlow(com.example.data.model.SpeedUnit.KMH)
    val speedUnit: StateFlow<com.example.data.model.SpeedUnit> = _speedUnit.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(com.example.data.model.TemperatureUnit.CELSIUS)
    val temperatureUnit: StateFlow<com.example.data.model.TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _pressureUnit = MutableStateFlow(com.example.data.model.PressureUnit.BAR)
    val pressureUnit: StateFlow<com.example.data.model.PressureUnit> = _pressureUnit.asStateFlow()

    private val _fuelEconomyUnit = MutableStateFlow(com.example.data.model.FuelEconomyUnit.KM_PER_L)
    val fuelEconomyUnit: StateFlow<com.example.data.model.FuelEconomyUnit> = _fuelEconomyUnit.asStateFlow()

    fun setSpeedUnit(unit: com.example.data.model.SpeedUnit) { _speedUnit.value = unit }
    fun setTemperatureUnit(unit: com.example.data.model.TemperatureUnit) { _temperatureUnit.value = unit }
    fun setPressureUnit(unit: com.example.data.model.PressureUnit) { _pressureUnit.value = unit }
    fun setFuelEconomyUnit(unit: com.example.data.model.FuelEconomyUnit) { _fuelEconomyUnit.value = unit }

    // HUD Display State
    private val _showHudDialog = MutableStateFlow(false)
    val showHudDialog: StateFlow<Boolean> = _showHudDialog.asStateFlow()

    private val _isHudMirrored = MutableStateFlow(true) // Default mirrored for windshield reflection
    val isHudMirrored: StateFlow<Boolean> = _isHudMirrored.asStateFlow()

    private val _hudColorTheme = MutableStateFlow(com.example.data.model.HudColorTheme.NEON_GREEN)
    val hudColorTheme: StateFlow<com.example.data.model.HudColorTheme> = _hudColorTheme.asStateFlow()

    private val _hudLayoutMode = MutableStateFlow(com.example.data.model.HudLayoutMode.DASHBOARD_TRIO)
    val hudLayoutMode: StateFlow<com.example.data.model.HudLayoutMode> = _hudLayoutMode.asStateFlow()

    fun setShowHudDialog(show: Boolean) { _showHudDialog.value = show }
    fun toggleHudMirror() { _isHudMirrored.value = !_isHudMirrored.value }
    fun setHudColorTheme(theme: com.example.data.model.HudColorTheme) { _hudColorTheme.value = theme }
    fun setHudLayoutMode(mode: com.example.data.model.HudLayoutMode) { _hudLayoutMode.value = mode }

    // Baseline Comparison Dialog State
    private val _showBaselineDialog = MutableStateFlow(false)
    val showBaselineDialog: StateFlow<Boolean> = _showBaselineDialog.asStateFlow()

    fun setShowBaselineDialog(show: Boolean) { _showBaselineDialog.value = show }
    
    fun setShowSnapshotDialog(show: Boolean) { _showSnapshotDialog.value = show }
    fun setShowSystemLogsDialog(show: Boolean) { _showSystemLogsDialog.value = show }
    fun setVoiceAlertEnabled(enabled: Boolean) { _isVoiceAlertEnabled.value = enabled }

    // Widget Customization State
    private val _widgetSpeedVisible = MutableStateFlow(true)
    val widgetSpeedVisible: StateFlow<Boolean> = _widgetSpeedVisible.asStateFlow()

    private val _widgetBatteryVisible = MutableStateFlow(true)
    val widgetBatteryVisible: StateFlow<Boolean> = _widgetBatteryVisible.asStateFlow()

    private val _widgetCoolantVisible = MutableStateFlow(true)
    val widgetCoolantVisible: StateFlow<Boolean> = _widgetCoolantVisible.asStateFlow()

    private val _widgetDtcVisible = MutableStateFlow(true)
    val widgetDtcVisible: StateFlow<Boolean> = _widgetDtcVisible.asStateFlow()

    // Favorites & Shortcuts State
    private val _favoriteFeatureIds = MutableStateFlow<Set<String>>(
        setOf("dtc_scan", "live_telemetry", "ai_mechanic", "thai_car_specs", "predictive_health")
    )
    val favoriteFeatureIds: StateFlow<Set<String>> = _favoriteFeatureIds.asStateFlow()

    fun toggleFavoriteFeature(featureId: String) {
        val current = _favoriteFeatureIds.value.toMutableSet()
        if (current.contains(featureId)) {
            current.remove(featureId)
        } else {
            current.add(featureId)
        }
        _favoriteFeatureIds.value = current
    }

    fun isFeatureFavorite(featureId: String): Boolean {
        return _favoriteFeatureIds.value.contains(featureId)
    }

    // Navigation Tab State
    // 0: Home, 1: Diagnostic Modules, 2: Predictive Maintenance, 3: Live Telemetry, 4: AI Mechanic, 5: Service & VIN Specs
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()
    val selectedTab: StateFlow<Int> = currentTab

    // Vehicles
    val allVehicles: StateFlow<List<Vehicle>> = vehicleDao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedVehicle: StateFlow<Vehicle?> = vehicleDao.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Live Sensors strictly routed according to Operation Mode
    val liveSensors: StateFlow<LiveSensorData> = combine(
        _operationMode,
        usbDriver.realSensorData,
        emulatorService.sensorData
    ) { mode, realData, simData ->
        when (mode) {
            AppOperationMode.REAL_HARDWARE -> realData
            AppOperationMode.SIMULATOR -> simData
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LiveSensorData.disconnected())

    val freezeFrame: StateFlow<FreezeFrameData?> = emulatorService.freezeFrame
    val drivingMode: StateFlow<DrivingMode> = emulatorService.currentDrivingMode

    // Active Test & Special Functions State
    private val _isExecutingActiveTest = MutableStateFlow(false)
    val isExecutingActiveTest: StateFlow<Boolean> = _isExecutingActiveTest.asStateFlow()

    private val _activeTestLog = MutableStateFlow<List<String>>(emptyList())
    val activeTestLog: StateFlow<List<String>> = _activeTestLog.asStateFlow()

    private val _isExecutingSpecialFunction = MutableStateFlow(false)
    val isExecutingSpecialFunction: StateFlow<Boolean> = _isExecutingSpecialFunction.asStateFlow()

    private val _specialFunctionLog = MutableStateFlow<List<String>>(emptyList())
    val specialFunctionLog: StateFlow<List<String>> = _specialFunctionLog.asStateFlow()

    /**
     * Active actuator control is intentionally disabled until a vehicle-specific
     * transport can validate ECU support, transmit the selected command, and
     * confirm the real ECU response. Never synthesize an ACK or success result.
     */
    fun runActiveTest(testNameTh: String, testNameEn: String, commands: List<String>) {
        if (_isExecutingActiveTest.value) return
        val isTh = _language.value == AppLanguage.TH
        val name = if (isTh) testNameTh else testNameEn
        val message = if (isTh) {
            "ยังไม่ดำเนินการ: ยังไม่มีการตรวจสอบคำสั่งและการตอบกลับจาก ECU จริงสำหรับรถรุ่นนี้"
        } else {
            "Not executed: vehicle-specific ECU command support and real response validation are not implemented."
        }
        _activeTestLog.value = listOf(
            if (isTh) "⚠ Active Test ถูกปิดเพื่อความปลอดภัย: $name" else "⚠ Active Test blocked for safety: $name",
            message
        )
        _isExecutingActiveTest.value = false
    }

    /**
     * Special functions may write ECU configuration or perform adaptation.
     * Keep them blocked until OEM-specific security access, compatibility checks,
     * response validation, and recovery behavior are implemented.
     */
    fun runComponentRegistration(functionNameTh: String, functionNameEn: String, parameterData: String) {
        val isTh = _language.value == AppLanguage.TH
        val name = if (isTh) functionNameTh else functionNameEn
        val message = if (isTh) {
            "ยังไม่ดำเนินการ: ฟังก์ชันลงทะเบียน/Calibration ต้องมีขั้นตอน OEM และการยืนยันจาก ECU จริง"
        } else {
            "Not executed: registration/calibration requires OEM-specific procedures and confirmed ECU responses."
        }
        _specialFunctionLog.value = listOf(
            if (isTh) "⚠ ปิดฟังก์ชันเพื่อความปลอดภัย: $name" else "⚠ Function blocked for safety: $name",
            message
        )
        _isExecutingSpecialFunction.value = false
    }

    fun clearActiveTestLog() {
        _activeTestLog.value = emptyList()
    }

    fun clearSpecialFunctionLog() {
        _specialFunctionLog.value = emptyList()
    }

    // Diagnostic Modules State
    private val _selectedDiagnosticSystem = MutableStateFlow(DiagnosticSystem.ALL)
    val selectedDiagnosticSystem: StateFlow<DiagnosticSystem> = _selectedDiagnosticSystem.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // DTC Search State
    private val _dtcSearchQuery = MutableStateFlow("")
    val dtcSearchQuery: StateFlow<String> = _dtcSearchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<com.example.data.model.DtcCode>> = _dtcSearchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                dtcDao.searchDtcs(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDtcSearchQuery(query: String) {
        _dtcSearchQuery.value = query
    }

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _currentScanSystem = MutableStateFlow("")
    val currentScanSystem: StateFlow<String> = _currentScanSystem.asStateFlow()

    private val _activeDtcs = MutableStateFlow<List<DtcCode>>(emptyList())
    val activeDtcs: StateFlow<List<DtcCode>> = _activeDtcs.asStateFlow()

    private val _isMilOn = MutableStateFlow(false)
    val isMilOn: StateFlow<Boolean> = _isMilOn.asStateFlow()

    // Predictive Maintenance State
    private val _predictiveHealth = MutableStateFlow<PredictiveVehicleHealthSummary>(
        PredictiveMaintenanceEngine.analyzeVehicleHealth(
            vehicle = null,
            logs = emptyList(),
            sensors = LiveSensorData(),
            activeDtcs = emptyList(),
            drivingMode = DrivingMode.CRUISE
        )
    )
    val predictiveHealth: StateFlow<PredictiveVehicleHealthSummary> = _predictiveHealth.asStateFlow()

    // AI Mechanic Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _enableHighThinking = MutableStateFlow(true)
    val enableHighThinking: StateFlow<Boolean> = _enableHighThinking.asStateFlow()

    // Deep Diagnostic Report
    private val _diagnosticReport = MutableStateFlow<String?>(null)
    val diagnosticReport: StateFlow<String?> = _diagnosticReport.asStateFlow()

    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    // Repair Manual State
    private val _repairManualContent = MutableStateFlow<String?>(null)
    val repairManualContent: StateFlow<String?> = _repairManualContent.asStateFlow()

    private val _isFetchingManual = MutableStateFlow(false)
    val isFetchingManual: StateFlow<Boolean> = _isFetchingManual.asStateFlow()

    fun fetchRepairManual() {
        if (_isFetchingManual.value) return
        _isFetchingManual.value = true
        viewModelScope.launch {
            val content = aiRepository.generateRepairManual(
                selectedVehicle.value,
                activeDtcs.value,
                language.value
            )
            _repairManualContent.value = content
            _isFetchingManual.value = false
        }
    }

    fun clearRepairManual() {
        _repairManualContent.value = null
    }

    // VIN Scanner / Specs
    private val _vinInput = MutableStateFlow("MR0BA3CD2M0123456")
    val vinInput: StateFlow<String> = _vinInput.asStateFlow()

    private val _decodedVinResult = MutableStateFlow<Map<String, String>>(emptyMap())
    val decodedVinResult: StateFlow<Map<String, String>> = _decodedVinResult.asStateFlow()

    private val _selectedCarSpec = MutableStateFlow<ThaiCarSpec?>(ThaiCarDatabase.popularThaiCars.firstOrNull())
    val selectedCarSpec: StateFlow<ThaiCarSpec?> = _selectedCarSpec.asStateFlow()

    // Maintenance Logs
    private val _maintenanceLogs = MutableStateFlow<List<MaintenanceLog>>(emptyList())
    val maintenanceLogs: StateFlow<List<MaintenanceLog>> = _maintenanceLogs.asStateFlow()

    // Scan Records
    val scanHistory: StateFlow<List<ScanRecord>> = scanRecordDao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cloud Database & Vault Sync State
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _lastCloudSyncTimestamp = MutableStateFlow<Long>(0L)
    val lastCloudSyncTimestamp: StateFlow<Long> = _lastCloudSyncTimestamp.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("Cloud Sync unavailable: no remote service connected")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    // Notification Log History State for Dashboard Notification Center
    private val _recentNotifications = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val recentNotifications: StateFlow<List<Pair<Long, String>>> = _recentNotifications.asStateFlow()

    // Telemetry Graph History Points (for live charts)
    private val _rpmHistory = MutableStateFlow<List<Int>>(emptyList())
    val rpmHistory: StateFlow<List<Int>> = _rpmHistory.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<Int>>(emptyList())
    val speedHistory: StateFlow<List<Int>> = _speedHistory.asStateFlow()

    init {
        initializeSampleData()
        initializeDtcDatabase()
        decodeCurrentVin()
        initWelcomeChatMessage()
        observeSensorHistoryAndPredictive()
    }

    private fun initializeDtcDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = dtcDao.getCount()
            if (count == 0) {
                dtcDao.insertAll(com.example.data.repository.DtcDatabase.allCodes)
            }
        }
    }

    /**
     * Real-hardware mode must start without fictitious vehicles or service history.
     * Demo fixtures should be loaded only through an explicit, clearly labeled
     * simulator/demo action; never seed them into a user's production database.
     */
    private fun initializeSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMaintenanceLogs()
            recalculatePredictiveHealth()
        }
    }

    fun setOperationMode(mode: AppOperationMode) {
        _operationMode.value = mode
        if (mode == AppOperationMode.SIMULATOR) {
            emulatorService.startTelemetrySimulation()
            _activeDtcs.value = listOf(
                DtcDatabase.findByCode("P0171") ?: DtcDatabase.allCodes[0],
                DtcDatabase.findByCode("P0087") ?: DtcDatabase.allCodes[1],
                DtcDatabase.findByCode("C0035") ?: DtcDatabase.allCodes[6]
            )
            _isMilOn.value = true
            emulatorService.triggerSimulatedFault("P0171")
        } else {
            emulatorService.stopTelemetrySimulation()
            if (!usbDriver.isUsbConnected.value) {
                _activeDtcs.value = emptyList()
                _isMilOn.value = false
            }
        }
        recalculatePredictiveHealth()
    }

    private fun observeSensorHistoryAndPredictive() {
        viewModelScope.launch {
            liveSensors.collect { sensor ->
                val currentRpm = _rpmHistory.value.toMutableList()
                currentRpm.add(sensor.rpm)
                if (currentRpm.size > 15) currentRpm.removeAt(0)
                _rpmHistory.value = currentRpm

                val currentSpeed = _speedHistory.value.toMutableList()
                currentSpeed.add(sensor.speedKmh)
                if (currentSpeed.size > 15) currentSpeed.removeAt(0)
                _speedHistory.value = currentSpeed

                // Performance Test Logic (0-100 km/h)
                val state = _perfTestState.value
                val speed = sensor.speedKmh
                when (state) {
                    PerfTestState.READY -> {
                        if (speed > 0) {
                            _perfTestState.value = PerfTestState.ACCELERATING
                            perfTestStartTime = System.currentTimeMillis()
                        }
                    }
                    PerfTestState.ACCELERATING -> {
                        if (speed >= 100) {
                            _perfTestState.value = PerfTestState.COMPLETED
                            val timeTakenMs = System.currentTimeMillis() - perfTestStartTime
                            _perf0to100Time.value = timeTakenMs / 1000f
                        } else if (speed == 0) {
                            _perfTestState.value = PerfTestState.READY // Reset if stopped
                        }
                    }
                    else -> {}
                }

                // Voice Alerts Logic
                if (_isVoiceAlertEnabled.value) {
                    val isTh = _language.value == AppLanguage.TH
                    if (sensor.coolantTempC > 105) {
                        systemLogger.w("SAFETY", "High Coolant Temperature Alert", "Temp=${sensor.coolantTempC}°C")
                        voiceAlertService.speakAlert(if (isTh) "คำเตือน ความร้อนเครื่องยนต์สูงเกินระดับปลอดภัย กรุณาจอดพัก" else "Warning, engine coolant temperature is critically high. Please pull over safely.")
                    } else if (sensor.batteryVoltage > 0f && sensor.batteryVoltage < 11.5f) {
                        systemLogger.w("SAFETY", "Low Battery Voltage Alert", "Voltage=${sensor.batteryVoltage}V")
                        voiceAlertService.speakAlert(if (isTh) "คำเตือน แรงดันแบตเตอรี่ต่ำกว่าปกติ" else "Warning, battery voltage is low.")
                    } else if (sensor.rpm > 4500) {
                        systemLogger.w("SAFETY", "High RPM Peak Alert", "RPM=${sensor.rpm}")
                        voiceAlertService.speakAlert(if (isTh) "รอบเครื่องยนต์สูงเกินไป กรุณาลดความเร็ว" else "High RPM peak reached. Please reduce throttle.")
                    }
                }

                // Periodically update predictive health with live data
                recalculatePredictiveHealth()
            }
        }
    }

    fun recalculatePredictiveHealth() {
        val vehicle = selectedVehicle.value
        val logs = _maintenanceLogs.value
        // Use the same source selected for the visible telemetry. In real-hardware
        // mode this is USB ECU data, never the simulator's sensor stream.
        val sensors = liveSensors.value
        val dtcs = _activeDtcs.value
        val mode = if (_operationMode.value == AppOperationMode.SIMULATOR) {
            emulatorService.currentDrivingMode.value
        } else {
            DrivingMode.IDLE
        }

        _predictiveHealth.value = PredictiveMaintenanceEngine.analyzeVehicleHealth(
            vehicle = vehicle,
            logs = logs,
            sensors = sensors,
            activeDtcs = dtcs,
            drivingMode = mode
        )
    }

    private fun initWelcomeChatMessage() {
        val isTh = _language.value == AppLanguage.TH
        val welcome = if (isTh) {
            """
            สวัสดีครับ! ผมคือ **ช่าง AI ผู้เชี่ยวชาญ (Master Mechanic)** ประจำระบบวิเคราะห์ OBD-II
            ผมพร้อมช่วยวิเคราะห์ปัญหาเครื่องยนต์, ระบบเกียร์ออโต้/CVT, เบรก ABS, ถุงลมนิรภัย SRS, ตัวถัง BCM, ถอดรหัส DTC, พยากรณ์ความเสี่ยงชิ้นส่วนล่วงหน้า และประเมินราคาอะไหล่แท้/เทียบในไทยครับ

            🚗 เลือกดูโมดูลวิเคราะห์ หรือพิมพ์สอบถามอาการรถได้ทันทีครับ!
            """.trimIndent()
        } else {
            """
            Hello! I am your **AI Master Diagnostic Technician** for full vehicle OBD-II & UDS diagnostics.
            I specialize in Powertrain (ECM), Transmission (TCM), Anti-Lock Brakes (ABS), Airbags (SRS), Body Electronics (BCM), Predictive Component Failure Analytics, and Thailand parts repair pricing.

            How can I assist with your vehicle today?
            """.trimIndent()
        }

        _chatMessages.value = listOf(
            ChatMessage(
                sender = MessageSender.AI_MECHANIC,
                text = welcome
            )
        )
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        initWelcomeChatMessage()
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.TH) AppLanguage.EN else AppLanguage.TH
        setLanguage(next)
    }

    fun setTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun selectDiagnosticSystem(system: DiagnosticSystem) {
        _selectedDiagnosticSystem.value = system
    }

    fun selectVehicle(vehicle: Vehicle) {
        viewModelScope.launch(Dispatchers.IO) {
            vehicleDao.clearSelection()
            vehicleDao.setSelected(vehicle.id)
            refreshMaintenanceLogs()
            recalculatePredictiveHealth()
        }
    }

    fun addNewVehicle(
        make: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        province: String,
        vin: String,
        mileage: Int,
        fuel: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newVehicle = Vehicle(
                make = make,
                model = model,
                year = year,
                engine = engine,
                licensePlate = plate,
                provinceTh = province,
                vin = vin,
                currentMileageKm = mileage,
                fuelType = fuel,
                isSelected = true,
                healthScore = 95
            )
            vehicleDao.clearSelection()
            vehicleDao.insertVehicle(newVehicle)
            refreshMaintenanceLogs()
            recalculatePredictiveHealth()
        }
    }

    fun refreshMaintenanceLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            maintenanceDao.getAllLogs().collect { logs ->
                _maintenanceLogs.value = logs
                recalculatePredictiveHealth()
            }
        }
    }

    fun addMaintenanceRecord(
        serviceTypeTh: String,
        serviceTypeEn: String,
        mileage: Int,
        nextMileage: Int,
        cost: Double,
        garage: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = selectedVehicle.value ?: return@launch
            val log = MaintenanceLog(
                vehicleId = vehicle.id,
                serviceTypeTh = serviceTypeTh,
                serviceTypeEn = serviceTypeEn,
                mileageAtServiceKm = mileage,
                nextServiceMileageKm = nextMileage,
                serviceDate = System.currentTimeMillis(),
                nextServiceDate = System.currentTimeMillis() + 180L * 24 * 3600 * 1000,
                costThb = cost,
                garageNameTh = garage,
                notes = notes
            )
            maintenanceDao.insertLog(log)
            refreshMaintenanceLogs()

            if (mileage > vehicle.currentMileageKm) {
                vehicleDao.updateVehicle(vehicle.copy(currentMileageKm = mileage))
            }
        }
    }

    fun addMaintenanceLog(
        serviceTypeTh: String,
        serviceTypeEn: String,
        mileage: Int,
        nextMileage: Int,
        cost: Double,
        garage: String,
        notes: String
    ) = addMaintenanceRecord(serviceTypeTh, serviceTypeEn, mileage, nextMileage, cost, garage, notes)

    fun deleteMaintenanceLog(log: MaintenanceLog) {
        viewModelScope.launch(Dispatchers.IO) {
            maintenanceDao.deleteLog(log)
            refreshMaintenanceLogs()
            recalculatePredictiveHealth()
        }
    }

    // Push Notification Trigger Methods
    fun triggerServiceNotificationReminder() = triggerMaintenanceNotification()

    fun triggerMaintenanceNotification() {
        val vehicle = selectedVehicle.value ?: return
        val context = getApplication<Application>()
        val isTh = _language.value == AppLanguage.TH
        val carName = "${vehicle.make} ${vehicle.model}"
        val title = if (isTh) "🔔 ตรวจสอบกำหนดบำรุงรักษา: $carName" else "🔔 Review Maintenance Schedule: $carName"
        val msg = if (isTh) {
            "โปรดตรวจสอบประวัติและกำหนดบำรุงรักษาที่บันทึกไว้ในแอป ระบบยังไม่ได้ยืนยันว่าถึงกำหนดเข้ารับบริการ"
        } else {
            "Review the maintenance history and schedule recorded in the app. Service due status has not been verified."
        }
        NotificationHelper.sendScheduledMaintenanceNotification(context, title, msg, notificationId = 1001)
        _recentNotifications.value = listOf((System.currentTimeMillis()) to "$title - $msg") + _recentNotifications.value
    }

    fun triggerCriticalAlertNotification(customMessage: String? = null) {
        val msg = customMessage?.takeIf { it.isNotBlank() } ?: return
        val context = getApplication<Application>()
        val isTh = _language.value == AppLanguage.TH
        val title = if (isTh) "🚨 แจ้งเตือนวิกฤตจากข้อมูลที่ตรวจพบ" else "🚨 Critical Alert From Observed Data"
        NotificationHelper.sendCriticalAlertNotification(context, title, msg, notificationId = 1002)
        _recentNotifications.value = listOf((System.currentTimeMillis()) to "$title - $msg") + _recentNotifications.value
    }

    fun triggerDiagnosticFindingsNotification(customMessage: String? = null) {
        val msg = customMessage?.takeIf { it.isNotBlank() } ?: return
        val context = getApplication<Application>()
        val isTh = _language.value == AppLanguage.TH
        val title = if (isTh) "🔍 ผลการตรวจวินิจฉัย" else "🔍 Diagnostic Findings"
        NotificationHelper.sendDiagnosticFindingNotification(context, title, msg, notificationId = 1003)
        _recentNotifications.value = listOf((System.currentTimeMillis()) to "$title - $msg") + _recentNotifications.value
    }

    // Cloud Database Synchronization & Backup
    // Do not report success until a real remote operation is implemented and acknowledged.
    fun syncDatabaseWithCloud(onComplete: (Boolean) -> Unit = {}) {
        _isCloudSyncing.value = false
        _cloudSyncStatus.value = if (_language.value == AppLanguage.TH) {
            "ยังไม่พร้อมซิงค์: ยังไม่ได้เชื่อมต่อบริการ Cloud Sync จริง"
        } else {
            "Sync unavailable: a real Cloud Sync service is not connected"
        }
        onComplete(false)
    }

    // Local refresh is not a cloud restore; fail closed until remote download is implemented.
    fun restoreDatabaseFromCloud(onComplete: (Boolean) -> Unit = {}) {
        _isCloudSyncing.value = false
        _cloudSyncStatus.value = if (_language.value == AppLanguage.TH) {
            "กู้คืนจากคลาวด์ไม่ได้: ยังไม่ได้เชื่อมต่อบริการ Cloud Restore จริง"
        } else {
            "Restore unavailable: a real Cloud Restore service is not connected"
        }
        onComplete(false)
    }

    // Advanced Diagnostic Module Scanner (Targeted per Module or All)
    fun runModuleDiagnosticScan(system: DiagnosticSystem = _selectedDiagnosticSystem.value) {
        if (_isScanning.value) return

        // Strict Real Hardware Check
        if (_operationMode.value == AppOperationMode.REAL_HARDWARE && !usbDriver.isUsbConnected.value) {
            val isTh = _language.value == AppLanguage.TH
            val msg = if (isTh) {
                "⚠️ ไม่พบการเชื่อมต่อสาย OBD2 USB: กรุณาเสียบสาย OTG และกด 'เชื่อมต่อ USB' ในหน้าต่างตั้งค่าก่อนสแกน (หรือสลับไปที่โหมดจำลอง)"
            } else {
                "⚠️ OBD2 USB is not connected. Please plug OTG cable and tap 'Connect USB' before scanning (or switch to Simulator Mode)."
            }
            _currentScanSystem.value = msg
            val context = getApplication<Application>()
            NotificationHelper.sendCriticalAlertNotification(context, if (isTh) "⚠️ ไม่พบการเชื่อมต่อ OBD2 USB" else "⚠️ USB Disconnected", msg, 1007)
            return
        }

        if (_operationMode.value == AppOperationMode.REAL_HARDWARE &&
            system !in listOf(DiagnosticSystem.ALL, DiagnosticSystem.ENGINE)
        ) {
            _currentScanSystem.value = if (_language.value == AppLanguage.TH) {
                "ยังไม่รองรับการสแกน ECU เฉพาะระบบนี้: ขณะนี้รองรับเฉพาะ Generic OBD-II สำหรับรหัสเครื่องยนต์/ระบบส่งกำลังที่ ECU ตอบกลับ"
            } else {
                "System-specific ECU scan is not implemented. Current hardware path supports generic OBD-II responses only."
            }
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0f

            val steps = if (_operationMode.value == AppOperationMode.REAL_HARDWARE) {
                listOf(
                    "Detecting OBD protocol via adapter...",
                    "Validating ECU response to supported-PID query...",
                    "Reading confirmed DTCs (Mode 03)...",
                    "Reading pending DTCs (Mode 07)...",
                    "Reading permanent DTCs (Mode 0A)..."
                ).map { it to it }
            } else when (system) {
                DiagnosticSystem.ALL -> listOf(
                    "Connecting via ISO 15765-4 High-Speed CAN..." to "เชื่อมต่อผ่านเครือข่ายความเร็วสูง CAN Bus 500k...",
                    "Scanning Engine ECM Module (P0xxx)..." to "กำลังสแกนกล่องเครื่องยนต์ ECM (รหัส P)...",
                    "Scanning Powertrain TCM Module (P07xx, P08xx)..." to "กำลังสแกนกล่องเกียร์และระบบส่งกำลัง TCM...",
                    "Scanning Chassis ABS / ESC Module (C0xxx)..." to "กำลังสแกนระบบแชสซีส์ เบรก ABS & ช่วงล่าง (รหัส C)...",
                    "Scanning Electrical & BCM Module (B1xxx, U0xxx)..." to "กำลังสแกนกล่องควบคุมไฟฟ้าตัวถัง BCM & เครือข่าย...",
                    "Reading Freeze Frame Snapshots & PID Live Data..." to "กำลังดึงข้อมูล Freeze Frame และค่าเซนเซอร์สด..."
                )
                DiagnosticSystem.ENGINE -> listOf(
                    "Establishing UDS handshake with ECM (0x7E0)..." to "เชื่อมต่อคำสั่ง UDS กับกล่อง ECM (0x7E0)...",
                    "Querying Mode 03 & 07 Diagnostic Trouble Codes..." to "ส่งคำสั่ง Mode 03 & 07 อ่านรหัสข้อผิดพลาดเครื่องยนต์...",
                    "Analyzing Fuel Trim & Common Rail Pressure..." to "ตรวจวิเคราะห์ค่าส่วนผสมน้ำมันและแรงดันรางคอมมอนเรล...",
                    "Reading Cylinder Misfire Counters..." to "ตรวจสอบประวัติการจุดระเบิดผิดพลาดรายสูบ..."
                )
                DiagnosticSystem.POWERTRAIN, DiagnosticSystem.TRANSMISSION -> listOf(
                    "Establishing UDS handshake with TCM (0x7E1)..." to "เชื่อมต่อคำสั่ง UDS กับกล่องสมองกลเกียร์ TCM (0x7E1)...",
                    "Reading Transmission Fluid Temperature & Line Pressure..." to "อ่านค่าอุณหภูมิน้ำมันเกียร์และแรงดันไฮดรอลิก...",
                    "Testing Shift Solenoids & TCC Lockup Clutch..." to "ทดสอบโซลินอยด์เปลี่ยนเกียร์และคลัตช์ทอร์คคอนเวอร์เตอร์...",
                    "Checking Gear Ratio Slip Counters..." to "ตรวจเช็คอัตราทดเกียร์และอาการเกียร์ลื่น..."
                )
                DiagnosticSystem.CHASSIS, DiagnosticSystem.ABS -> listOf(
                    "Establishing UDS handshake with EBCM (0x7E2)..." to "เชื่อมต่อคำสั่ง UDS กับกล่องปั๊มเบรก ABS (0x7E2)...",
                    "Reading 4-Wheel Speed Sensor Pulses..." to "อ่านสัญญาณพัลส์ความเร็วล้อทั้ง 4 ล้อ (FL, FR, RL, RR)...",
                    "Testing ABS Pump Motor Relay & Pressure Sensors..." to "ทดสอบรีเลย์มอเตอร์ปั๊ม ABS และเซนเซอร์แรงดันเบรก...",
                    "Verifying Steering Angle & Yaw Rate Sensors..." to "ตรวจสอบเซนเซอร์องศาพวงมาลัยและแรงเหวี่ยงหนีศูนย์..."
                )
                DiagnosticSystem.IMMOBILIZER -> listOf(
                    "Establishing UDS handshake with Immobilizer ICU (0x7E5)..." to "เชื่อมต่อคำสั่ง UDS กับกล่อง Immobilizer (0x7E5)...",
                    "Pinging Transponder Antenna Coil & Smart Key ID..." to "ตรวจสอบการรับส่งสัญญาณเสาอากาศสายกุญแจชิพ...",
                    "Checking Key Encryption Seed & ECU Pairing Key..." to "ตรวจสอบรหัสเข้ารหัสกุญแจและการจับคู่กล่อง ECU...",
                    "Verifying Anti-Theft Fuel Cut Off Status..." to "ตรวจสอบระบบตัดการจ่ายน้ำมันเชื้อเพลิงป้องกันการโจรกรรม..."
                )
                DiagnosticSystem.ELECTRICAL, DiagnosticSystem.BODY, DiagnosticSystem.AIRBAG, DiagnosticSystem.NETWORK -> listOf(
                    "Establishing UDS handshake with BCM (0x7E4)..." to "เชื่อมต่อคำสั่ง UDS กับกล่องควบคุมตัวถัง BCM (0x7E4)...",
                    "Reading 12V Battery Voltage & Alternator Duty..." to "อ่านแรงดันไฟแบตเตอรี่และกระแสชาร์จไดชาร์จ...",
                    "Testing Smart Key Transponder & Cabin Electronics..." to "ทดสอบสัญญาณชิพกุญแจ Smart Key และระบบไฟฟ้าตัวถัง...",
                    "Pinging Gateway & CAN Bus Error Frame Counters..." to "ตรวจสอบเครือข่ายสื่อสารระหว่างกล่องควบคุม..."
                )
            }

            for (i in steps.indices) {
                val step = steps[i]
                _currentScanSystem.value = if (_language.value == AppLanguage.TH) step.second else step.first
                _scanProgress.value = (i + 1).toFloat() / steps.size
                delay(350)
            }

            val detected = if (_operationMode.value == AppOperationMode.REAL_HARDWARE) {
                // Execute Real Hardware Agnostic Scan via OBD-II Protocol Engine
                val scanResult = usbDriver.runDiagnosticScan(
                    timeoutMs = 2000,
                    maxRetries = 2,
                    onProgress = { progress, step ->
                        _scanProgress.value = progress
                        _currentScanSystem.value = if (_language.value == AppLanguage.TH) {
                            "กำลังสแกนผ่านโปรโตคอลจริง [$step]"
                        } else {
                            "Scanning via Active Protocol [$step]"
                        }
                    }
                )

                // Log raw evidence to System Logger
                systemLogger.i("DIAGNOSTIC", "Session ID: ${scanResult.sessionId} | Protocol: ${scanResult.protocol} | Status: ${scanResult.status} | DTC Count: ${scanResult.dtcList.size}")
                scanResult.rawEvidence.forEach { ev ->
                    systemLogger.log(if (ev.isSuccess) "INFO" else "WARN", "OBD_TX_RX", "CMD: ${ev.command} | RES: ${ev.rawResponse}", "Latency: ${ev.latencyMs}ms")
                }

                if (scanResult.status != com.example.service.DiagnosticScanStatus.SUCCESS &&
                    scanResult.status != com.example.service.DiagnosticScanStatus.NO_DTC
                ) {
                    _isScanning.value = false
                    _scanProgress.value = 0f
                    _currentScanSystem.value = if (_language.value == AppLanguage.TH) {
                        "สแกนไม่สำเร็จ: ไม่ได้รับผลตอบกลับ ECU ที่ยืนยันได้ (${scanResult.status})"
                    } else {
                        "Scan failed: no verified ECU scan result (${scanResult.status})."
                    }
                    _diagnosticReport.value = _currentScanSystem.value
                    return@launch
                }

                // Map only DTCs returned by a completed generic OBD-II response.
                scanResult.dtcList.map { res ->
                    DtcDatabase.findByCode(res.code) ?: DtcCode(
                        code = res.code,
                        category = when (res.code.firstOrNull()) {
                            'C' -> DtcCategory.CHASSIS
                            'B' -> DtcCategory.BODY
                            'U' -> DtcCategory.NETWORK
                            else -> DtcCategory.POWERTRAIN
                        },
                        enTitle = res.descriptionEn,
                        thTitle = res.descriptionTh,
                        enDescription = res.descriptionEn,
                        thDescription = res.descriptionTh,
                        severity = DtcSeverity.WARNING,
                        commonCausesTh = listOf("ตรวจพบข้อผิดพลาดจากเซ็นเซอร์ ECU ผ่าน OBD-II Protocol"),
                        commonCausesEn = listOf("Fault detected from ECU sensor via OBD-II Protocol"),
                        symptomsTh = listOf("ไฟ Check Engine สว่างขึ้น หรือบันทึกรหัสในหน่วยความจำ ECU"),
                        symptomsEn = listOf("Check Engine light on or stored in ECU memory"),
                        estimatedCostThb = "ตามตารางประเมินชิ้นส่วน",
                        diyDifficulty = "Medium",
                        affectedSystem = "Generic OBD-II ECU System"
                    )
                }
            } else {
                when (system) {
                    DiagnosticSystem.ALL -> listOf(
                        DtcDatabase.findByCode("P0171") ?: DtcDatabase.allCodes[0],
                        DtcDatabase.findByCode("P0087") ?: DtcDatabase.allCodes[1],
                        DtcDatabase.findByCode("C0035") ?: DtcDatabase.allCodes[6],
                        DtcDatabase.findByCode("P0741") ?: DtcDatabase.allCodes[8]
                    )
                    DiagnosticSystem.ENGINE -> listOf(
                        DtcDatabase.findByCode("P0171") ?: DtcDatabase.allCodes[0],
                        DtcDatabase.findByCode("P0087") ?: DtcDatabase.allCodes[1]
                    )
                    DiagnosticSystem.POWERTRAIN, DiagnosticSystem.TRANSMISSION -> listOf(
                        DtcDatabase.findByCode("P0741") ?: DtcDatabase.allCodes[8],
                        DtcDatabase.findByCode("P0730") ?: DtcDatabase.allCodes[7]
                    )
                    DiagnosticSystem.CHASSIS, DiagnosticSystem.ABS -> listOf(
                        DtcDatabase.findByCode("C0035") ?: DtcDatabase.allCodes[6],
                        DtcDatabase.findByCode("C0110") ?: DtcDatabase.allCodes[8]
                    )
                    DiagnosticSystem.ELECTRICAL, DiagnosticSystem.BODY, DiagnosticSystem.AIRBAG, DiagnosticSystem.NETWORK, DiagnosticSystem.IMMOBILIZER -> listOf(
                        DtcDatabase.findByCode("B1318") ?: DtcDatabase.allCodes[12],
                        DtcDatabase.findByCode("U0100") ?: DtcDatabase.allCodes[14]
                    )
                }
            }

            _activeDtcs.value = detected
            _isMilOn.value = detected.isNotEmpty()
            _isScanning.value = false
            _currentScanSystem.value = if (_operationMode.value == AppOperationMode.SIMULATOR) {
                if (_language.value == AppLanguage.TH) {
                    "การสแกนจำลองเสร็จสิ้น: สร้างรหัสจำลอง ${detected.size} รายการ"
                } else {
                    "Simulation complete: ${detected.size} simulated DTCs generated."
                }
            } else {
                if (_language.value == AppLanguage.TH) {
                    "สแกน Generic OBD-II เสร็จสิ้น: ECU ตอบกลับ ${detected.size} รหัส (ไม่ใช่การสแกน ECU ทุกโมดูล)"
                } else {
                    "Generic OBD-II scan complete: ${detected.size} DTCs returned by the ECU (not a full all-module scan)."
                }
            }

            if (_operationMode.value == AppOperationMode.SIMULATOR && detected.isNotEmpty()) {
                emulatorService.triggerSimulatedFault(detected.first().code)
            }
            recalculatePredictiveHealth()

            // Save Scan Record to Room
            val vehicle = selectedVehicle.value
            if (vehicle != null) {
                val sensor = liveSensors.value
                val record = ScanRecord(
                    vehicleId = vehicle.id,
                    timestamp = System.currentTimeMillis(),
                    dtcCodes = detected.joinToString(", ") { it.code },
                    dtcCount = detected.size,
                    milStatus = detected.isNotEmpty(),
                    healthSummaryTh = "สแกน ${system.titleTh}: ตรวจพบ ${detected.size} รหัส (${detected.joinToString { it.code }})",
                    healthSummaryEn = "Scan ${system.titleEn}: Found ${detected.size} DTCs (${detected.joinToString { it.code }})",
                    rpm = sensor.rpm,
                    coolantTempC = sensor.coolantTempC,
                    speedKmh = sensor.speedKmh,
                    batteryVoltage = sensor.batteryVoltage
                )
                scanRecordDao.insertRecord(record)
            }

            // Dispatch Push Notifications for Diagnostic Findings & Critical Alerts
            val context = getApplication<Application>()
            val isTh = _language.value == AppLanguage.TH
            val findingTitle = if (_operationMode.value == AppOperationMode.SIMULATOR) {
                if (isTh) "🔍 ผลการสแกนจำลอง ${system.titleTh}" else "🔍 Simulated ${system.titleEn} Findings"
            } else {
                if (isTh) "🔍 ผลการสแกน Generic OBD-II" else "🔍 Generic OBD-II Scan Findings"
            }
            val findingMsg = if (_operationMode.value == AppOperationMode.SIMULATOR) {
                if (isTh) {
                    "ผลจำลอง: สร้างรหัส ${detected.size} รายการ (${detected.joinToString { it.code }}); Freeze Frame เป็นข้อมูลจำลอง"
                } else {
                    "Simulation result: generated ${detected.size} DTCs (${detected.joinToString { it.code }}); Freeze Frame is simulated."
                }
            } else {
                if (isTh) {
                    "ECU ตอบกลับรหัส ${detected.size} รายการ (${detected.joinToString { it.code }}); การสแกนนี้ยังไม่ได้อ่าน Freeze Frame"
                } else {
                    "ECU returned ${detected.size} DTCs (${detected.joinToString { it.code }}); Freeze Frame was not read by this scan path."
                }
            }
            NotificationHelper.sendDiagnosticFindingNotification(context, findingTitle, findingMsg, 1005)

            if (detected.any { it.severity == com.example.data.model.DtcSeverity.CRITICAL }) {
                val critTitle = if (isTh) "🚨 ตรวจพบรหัสข้อผิดพลาดระดับวิกฤต!" else "🚨 Critical Diagnostic Trouble Code!"
                val critMsg = if (isTh) {
                    "พบรหัสที่มีความเสี่ยงต่อระบบขับเคลื่อน แนะนำปรึกษาช่าง AI หรือนำรถเข้าตรวจเช็ค"
                } else {
                    "High-risk diagnostic code detected. Consult AI mechanic or service immediately."
                }
                NotificationHelper.sendCriticalAlertNotification(context, critTitle, critMsg, 1006)
            }
        }
    }

    // Clear Fault Codes (Mode 04 / UDS 0x14 ECU Reset) for specific system or all
    fun clearDtcFaultCodes(system: DiagnosticSystem = DiagnosticSystem.ALL) {
        if (_operationMode.value == AppOperationMode.REAL_HARDWARE) {
            _diagnosticReport.value = if (_language.value == AppLanguage.TH) {
                "ยังไม่ได้ลบรหัสจาก ECU: ต้องส่งคำสั่งล้าง DTC ไปยังกล่องควบคุมและยืนยันผลตอบกลับจริงก่อน ระบบจึงจะเปลี่ยนสถานะรหัสขัดข้องได้"
            } else {
                "DTCs were not cleared: the ECU clear command and its real response must be verified before changing fault status."
            }
            return
        }

        viewModelScope.launch {
            if (system == DiagnosticSystem.ALL) {
                _activeDtcs.value = emptyList()
            } else {
                val systemCodes = DtcDatabase.getCodesBySystem(system).map { it.code }.toSet()
                _activeDtcs.value = _activeDtcs.value.filterNot { it.code in systemCodes }
            }
            _isMilOn.value = _activeDtcs.value.isNotEmpty()
            _diagnosticReport.value = null
            emulatorService.clearDtcAndResetEcu()
            // Clearing simulated codes is not evidence that the real vehicle is healthy.
            recalculatePredictiveHealth()
        }
    }

    fun setDrivingMode(mode: DrivingMode) {
        emulatorService.setDrivingMode(mode)
    }

    fun triggerSimulatedFault(code: String) {
        emulatorService.triggerSimulatedFault(code)
        val dtc = DtcDatabase.findByCode(code) ?: DtcCode(
            code = code,
            category = com.example.data.model.DtcCategory.POWERTRAIN,
            enTitle = "Simulated fault code $code",
            thTitle = "ความผิดปกติในระบบจำลอง $code",
            enDescription = "Fault generated by CAN simulation engine for testing",
            thDescription = "โค้ดข้อผิดพลาดที่สร้างขึ้นจากระบบจำลองเพื่อการทดสอบ",
            severity = com.example.data.model.DtcSeverity.WARNING,
            commonCausesTh = listOf("การทดสอบสถานการณ์จำลอง"),
            commonCausesEn = listOf("Simulation scenario trigger"),
            symptomsTh = listOf("ตรวจพบความผิดปกติจากระบบจำลอง"),
            symptomsEn = listOf("Detected from simulated CAN bus"),
            estimatedCostThb = "฿0 - จำลอง",
            diyDifficulty = "Demo",
            affectedSystem = "ระบบจำลอง CAN Bus"
        )
        if (_activeDtcs.value.none { it.code == code }) {
            _activeDtcs.value = _activeDtcs.value + dtc
            _isMilOn.value = true
        }
        recalculatePredictiveHealth()
    }

    // AI Mechanic Chat
    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val userMsg = ChatMessage(sender = MessageSender.USER, text = userText)
        val updatedList = _chatMessages.value + userMsg
        _chatMessages.value = updatedList

        viewModelScope.launch {
            _isAiThinking.value = true
            val vehicle = selectedVehicle.value
            val vContext = if (vehicle != null) {
                "${vehicle.make} ${vehicle.model} (${vehicle.year}), ไมล์ ${vehicle.currentMileageKm} km, เชื้อเพลิง ${vehicle.fuelType}"
            } else {
                if (_language.value == AppLanguage.TH) "ยังไม่ได้เลือกรถ" else "No vehicle selected"
            }

            val replyText = aiRepository.chat(
                conversation = updatedList,
                userPrompt = userText,
                enableHighThinking = _enableHighThinking.value,
                vehicleContext = vContext,
                language = _language.value
            )

            _chatMessages.value = _chatMessages.value + ChatMessage(
                sender = MessageSender.AI_MECHANIC,
                text = replyText,
                isThinking = _enableHighThinking.value
            )
            _isAiThinking.value = false
        }
    }

    fun toggleHighThinking(enabled: Boolean) {
        _enableHighThinking.value = enabled
    }

    // Deep AI Diagnostic Report Generation
    fun generateAiDeepReport() {
        val dtcs = _activeDtcs.value
        val vehicle = selectedVehicle.value
        if (vehicle == null) {
            _diagnosticReport.value = if (_language.value == AppLanguage.TH) {
                "ยังสร้างรายงานไม่ได้: กรุณาเลือกข้อมูลรถก่อน ระบบจะไม่สมมติรถหรือเลขตัวถังให้เอง"
            } else {
                "Report not generated: select a vehicle first. The app will not invent vehicle details."
            }
            return
        }
        if (dtcs.isEmpty()) {
            _diagnosticReport.value = if (_language.value == AppLanguage.TH) {
                "ยังสร้างรายงานข้อขัดข้องไม่ได้: ไม่มีรหัส DTC ที่ตรวจพบในขณะนี้ กรุณาสแกนระบบก่อน"
            } else {
                "Diagnostic report not generated: no detected DTCs are available. Run a scan first."
            }
            return
        }

        viewModelScope.launch {
            _isGeneratingReport.value = true
            try {
                _diagnosticReport.value = aiRepository.analyzeDeepDiagnosis(
                    dtcs = dtcs,
                    freezeFrame = if (_operationMode.value == AppOperationMode.SIMULATOR) emulatorService.freezeFrame.value else null,
                    vehicle = vehicle,
                    language = _language.value
                )
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    // VIN Decoder
    fun setVinInput(vin: String) {
        _vinInput.value = vin
        decodeCurrentVin()
    }

    private fun decodeCurrentVin() {
        val result = ThaiCarDatabase.decodeVin(_vinInput.value)
        _decodedVinResult.value = result

        val matchedCar = ThaiCarDatabase.findCarByVin(_vinInput.value)
        if (matchedCar != null) {
            _selectedCarSpec.value = matchedCar
        }
    }

    fun selectCarSpec(spec: ThaiCarSpec) {
        _selectedCarSpec.value = spec
        if (spec.vinPrefixes.isNotEmpty()) {
            _vinInput.value = spec.vinPrefixes.first() + "1234567890"
            decodeCurrentVin()
        }
    }

    // OBD2 Connection & USB Manager Operations
    fun setConnectionType(type: ObdConnectionType) {
        _connectionType.value = type
    }

    fun refreshUsbDevices() {
        usbDriver.refreshAvailableUsbDevices()
    }

    fun selectUsbDevice(device: UsbSerialDeviceInfo) {
        usbDriver.selectDevice(device)
    }

    fun setUsbBaudRate(baudRate: Int) {
        usbDriver.setBaudRate(baudRate)
    }

    fun setUsbProtocol(protocol: ObdProtocol) {
        usbDriver.setProtocol(protocol)
    }

    fun connectUsbObd(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        usbDriver.connectUsb(
            onSuccess = {
                val chipName = usbDriver.selectedDevice.value?.chipsetName ?: "FTDI FT232R"
                val baud = usbDriver.selectedBaudRate.value
                val updatedSensors = liveSensors.value.copy(
                    isConnected = true,
                    connectionMode = "USB OTG ($chipName @ $baud bps)"
                )
                // update notification
                triggerMaintenanceNotification()
                onSuccess()
            },
            onError = onError
        )
    }

    fun disconnectUsbObd() {
        usbDriver.disconnectUsb()
    }

    fun sendTerminalCommand(command: String, onResult: (String) -> Unit = {}) {
        usbDriver.sendCustomCommand(command, onResult)
    }

    fun clearTerminalLogs() {
        usbDriver.clearTerminalLogs()
    }

    // Google Drive integration is not implemented yet; fail closed rather than simulating success.
    fun connectGoogleDrive(email: String) {
        _googleDriveConnected.value = false
        _googleDriveAccountEmail.value = ""
    }

    fun disconnectGoogleDrive() {
        _googleDriveConnected.value = false
        _googleDriveAccountEmail.value = ""
    }

    fun backupToGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        _isGoogleDriveSyncing.value = false
        val msg = if (_language.value == AppLanguage.TH) {
            "สำรองข้อมูลไม่ได้: ยังไม่ได้เชื่อมต่อบริการ Google Drive จริง"
        } else {
            "Backup unavailable: a real Google Drive service is not connected"
        }
        onComplete(false, msg)
    }

    fun restoreFromGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        _isGoogleDriveSyncing.value = false
        val msg = if (_language.value == AppLanguage.TH) {
            "กู้คืนข้อมูลไม่ได้: ยังไม่ได้เชื่อมต่อบริการ Google Drive จริง"
        } else {
            "Restore unavailable: a real Google Drive service is not connected"
        }
        onComplete(false, msg)
    }

    fun toggleWidgetSpeed() { _widgetSpeedVisible.value = !_widgetSpeedVisible.value }
    fun toggleWidgetBattery() { _widgetBatteryVisible.value = !_widgetBatteryVisible.value }
    fun toggleWidgetCoolant() { _widgetCoolantVisible.value = !_widgetCoolantVisible.value }
    fun toggleWidgetDtc() { _widgetDtcVisible.value = !_widgetDtcVisible.value }
    fun toggleAllWidgets(visible: Boolean) {
        _widgetSpeedVisible.value = visible
        _widgetBatteryVisible.value = visible
        _widgetCoolantVisible.value = visible
        _widgetDtcVisible.value = visible
    }

    fun startPerformanceTest() {
        _perfTestState.value = PerfTestState.READY
        _perf0to100Time.value = null
    }

    fun stopPerformanceTest() {
        _perfTestState.value = PerfTestState.IDLE
        _perf0to100Time.value = null
    }

    // --- Telemetry Snapshot Management ---
    fun captureTelemetrySnapshot(title: String, reason: String, notes: String) {
        viewModelScope.launch {
            val vName = selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "Unknown Vehicle"
            val dtcs = _activeDtcs.value.joinToString(", ") { it.code }
            systemLogger.captureSnapshot(
                vehicleName = vName,
                title = title,
                reason = reason,
                sensors = liveSensors.value,
                activeDtcs = dtcs,
                notes = notes
            )
            systemLogger.i("SYSTEM", "Snapshot Captured: $title")
        }
    }

    fun deleteSnapshot(id: Long) {
        viewModelScope.launch {
            systemLogger.deleteSnapshot(id)
            systemLogger.i("SYSTEM", "Snapshot deleted: $id")
        }
    }

    fun clearAllSnapshots() {
        viewModelScope.launch {
            systemLogger.clearAllSnapshots()
            systemLogger.i("SYSTEM", "All snapshots cleared")
        }
    }

    fun clearSystemLogs() {
        viewModelScope.launch {
            systemLogger.clearAllLogs()
        }
    }
}
