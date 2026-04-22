package org.sainm.psy.respondent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.sainm.psy.respondent.data.AuthRepository
import org.sainm.psy.respondent.data.RespondentRepository
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.AppointmentSummary
import org.sainm.psy.respondent.data.model.CounselorOption
import org.sainm.psy.respondent.data.model.CounselorSchedule
import org.sainm.psy.respondent.data.model.CreateAppointmentRequest
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyNotification
import org.sainm.psy.respondent.data.model.MyReportSummary
import org.sainm.psy.respondent.data.model.ReportAnswerDetail
import org.sainm.psy.respondent.data.model.ReportDetail
import org.sainm.psy.respondent.data.model.SaveAnswerSheetRequest
import org.sainm.psy.respondent.data.model.SubmitAnswerSheetRequest
import org.sainm.psy.respondent.data.model.TaskQuestionItem
import org.sainm.psy.respondent.data.model.TaskQuestionPayload
import org.sainm.psy.respondent.data.remote.ApiFactory
import java.math.BigDecimal
import java.util.UUID

private data class AppDependencies(
    val authRepository: AuthRepository,
    val respondentRepository: RespondentRepository
)

private sealed class RootDestination(val route: String, val title: String, val icon: ImageVector) {
    data object Home : RootDestination("home", "首页", Icons.Outlined.Home)
    data object Tasks : RootDestination("tasks", "我的任务", Icons.AutoMirrored.Outlined.Assignment)
    data object Reports : RootDestination("reports", "我的报告", Icons.Outlined.Summarize)
    data object Appointments : RootDestination("appointments", "预约咨询", Icons.Outlined.CalendarMonth)
    data object Notifications : RootDestination("notifications", "通知消息", Icons.Outlined.Notifications)
}

private val rootDestinations = listOf(
    RootDestination.Home,
    RootDestination.Tasks,
    RootDestination.Reports,
    RootDestination.Appointments,
    RootDestination.Notifications
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsyRespondentApp() {
    val context = LocalContext.current
    val sessionStorage = remember(context) { SessionStorage(context) }
    val dependencies = remember(sessionStorage) {
        val apiFactory = ApiFactory(sessionStorage)
        AppDependencies(
            authRepository = AuthRepository(apiFactory, sessionStorage),
            respondentRepository = RespondentRepository(apiFactory)
        )
    }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var loggedIn by rememberSaveable { mutableStateOf(dependencies.authRepository.currentSession() != null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F6FA)) {
        if (!loggedIn) {
            LoginScreen(snackbarHostState = snackbarHostState) { username, password ->
                runCatching {
                    dependencies.authRepository.login(username, password)
                }.onSuccess {
                    loggedIn = true
                }.onFailure {
                    snackbarHostState.showSnackbar(it.message ?: "登录失败，请检查账号密码和服务地址。")
                }
            }
            return@Surface
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route?.substringBefore("/")
        val currentTitle = when (route) {
            RootDestination.Tasks.route -> "我的任务"
            RootDestination.Reports.route -> "我的报告"
            RootDestination.Appointments.route -> "预约咨询"
            RootDestination.Notifications.route -> "通知消息"
            "task" -> "开始答题"
            "report" -> "报告详情"
            else -> "心理测评"
        }
        val showBottomBar = route in rootDestinations.map { it.route }

        Scaffold(
            containerColor = Color(0xFFF3F6FA),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFF183B56)
                    ),
                    title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    dependencies.authRepository.logout()
                                    loggedIn = false
                                }
                            }
                        ) {
                            Text("退出")
                        }
                    }
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = Color.White) {
                        rootDestinations.forEach { item ->
                            val selected = backStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = RootDestination.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(RootDestination.Home.route) {
                    HomeScreen(
                        repository = dependencies.respondentRepository,
                        onOpenTasks = { navController.navigate(RootDestination.Tasks.route) },
                        onOpenReports = { navController.navigate(RootDestination.Reports.route) },
                        onOpenAppointments = { navController.navigate(RootDestination.Appointments.route) },
                        onOpenNotifications = { navController.navigate(RootDestination.Notifications.route) },
                        onOpenTask = { navController.navigate("task/$it") },
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(RootDestination.Tasks.route) {
                    TasksScreen(
                        repository = dependencies.respondentRepository,
                        onOpenTask = { navController.navigate("task/$it") },
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(
                    route = "task/{taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType })
                ) { entry ->
                    TaskQuestionScreen(
                        repository = dependencies.respondentRepository,
                        taskId = entry.arguments?.getLong("taskId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        onSubmitted = { navController.navigate("report/$it") }
                    )
                }
                composable(RootDestination.Reports.route) {
                    ReportsScreen(
                        repository = dependencies.respondentRepository,
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(
                    route = "report/{reportId}",
                    arguments = listOf(navArgument("reportId") { type = NavType.LongType })
                ) { entry ->
                    ReportDetailScreen(
                        repository = dependencies.respondentRepository,
                        reportId = entry.arguments?.getLong("reportId") ?: 0L,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(RootDestination.Appointments.route) {
                    AppointmentsScreen(repository = dependencies.respondentRepository)
                }
                composable(RootDestination.Notifications.route) {
                    NotificationsScreen(repository = dependencies.respondentRepository)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    snackbarHostState: SnackbarHostState,
    onLogin: suspend (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF12324D), Color(0xFF2B6F9A), Color(0xFFE7F0F7))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("被测者端", style = MaterialTheme.typography.labelLarge, color = Color(0xFF0F5F8F))
                    Text("你的心理测评入口", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "这里只保留普通用户真正需要的任务、报告、预约和通知，不再混入后台管理能力。",
                        color = Color(0xFF587082)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("测评", "报告", "预约").forEach {
                            AssistChip(
                                onClick = {},
                                label = { Text(it) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEAF3FB))
                            )
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("账号") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("请输入账号和密码。")
                                }
                            } else {
                                loading = true
                                coroutineScope.launch {
                                    onLogin(username.trim(), password)
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text(if (loading) "登录中..." else "登录")
                    }
                    Text(
                        "默认服务地址是模拟器的 10.0.2.2:8080；真机联调时请改 android-app/gradle.properties。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6A7F90)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    repository: RespondentRepository,
    onOpenTasks: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MyAssessmentTask>>(emptyList()) }
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<MyNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            tasks = repository.fetchMyTasks()
            reports = repository.fetchMyReports()
            notifications = repository.fetchMyNotifications()
        }.onFailure {
            error = it.message ?: "首页加载失败"
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading("正在加载首页...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("欢迎回来", "这里是被测者专属首页，你可以在这里完成任务、查看报告、预约咨询和处理通知。")
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("待完成测评", tasks.count { it.status != "COMPLETED" }.toString(), Modifier.weight(1f))
                MetricCard("已生成报告", reports.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("未读通知", notifications.count { !it.readFlag }.toString(), Modifier.weight(1f))
                MetricCard("预约咨询", "进入查看", Modifier.weight(1f), emphasized = false)
            }
        }
        item {
            QuickActionCard("继续测评", "优先完成未提交的测评任务。", Color(0xFF1B587D), onOpenTasks)
        }
        item {
            QuickActionCard("查看报告", "阅读你自己的结果和建议。", Color(0xFF1F744C), onOpenReports)
        }
        item {
            QuickActionCard("预约咨询", "需要帮助时可以直接预约咨询师。", Color(0xFF945C1E), onOpenAppointments)
        }
        item {
            QuickActionCard("通知消息", "查看报告、预约和任务更新。", Color(0xFF6A4BA8), onOpenNotifications)
        }
        item {
            SectionCard("最近待办") {
                val pending = tasks.filter { it.status != "COMPLETED" }.take(3)
                if (pending.isEmpty()) {
                    EmptyHint("当前没有待完成任务")
                } else {
                    pending.forEach { task ->
                        ListLine(
                            title = task.taskName,
                            subtitle = "${task.scaleName} · 截止 ${task.endTime}",
                            actionLabel = "去答题",
                            onAction = { onOpenTask(task.taskId) }
                        )
                    }
                }
            }
        }
        item {
            SectionCard("最近报告") {
                if (reports.isEmpty()) {
                    EmptyHint("当前没有可查看报告")
                } else {
                    reports.take(3).forEach { report ->
                        ListLine(
                            title = report.scaleName,
                            subtitle = "${report.taskName} · ${report.createdAt}",
                            actionLabel = "查看",
                            onAction = { onOpenReport(report.reportId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(
    repository: RespondentRepository,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MyAssessmentTask>>(emptyList()) }
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            tasks = repository.fetchMyTasks()
            reports = repository.fetchMyReports()
        }.onFailure {
            error = it.message ?: "任务加载失败"
        }
        loading = false
    }

    val visible = when (filter) {
        "PENDING" -> tasks.filter { it.status != "COMPLETED" }
        "COMPLETED" -> tasks.filter { it.status == "COMPLETED" }
        "OVERDUE" -> tasks.filter { it.status == "OVERDUE" }
        else -> tasks
    }

    if (loading) {
        FullscreenLoading("正在加载任务...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("我的任务", "优先处理未完成任务，提交后会自动进入报告页。")
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("ALL" to "全部", "PENDING" to "待完成", "COMPLETED" to "已完成", "OVERDUE" to "已逾期").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyHint("当前筛选下没有任务") }
        } else {
            items(visible, key = { it.taskId }) { task ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(task.taskName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${task.scaleName} · 截止 ${task.endTime}", color = Color(0xFF5E7384))
                        StatusPill(
                            when (task.status) {
                                "COMPLETED" -> "已完成"
                                "OVERDUE" -> "已逾期"
                                else -> "进行中"
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.status == "COMPLETED") {
                                Button(onClick = {
                                    reports.firstOrNull { it.taskId == task.taskId }?.let { onOpenReport(it.reportId) }
                                }) {
                                    Text("查看报告")
                                }
                            } else {
                                Button(onClick = { onOpenTask(task.taskId) }) {
                                    Text("继续作答")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskQuestionScreen(
    repository: RespondentRepository,
    taskId: Long,
    onBack: () -> Unit,
    onSubmitted: (Long) -> Unit
) {
    var payload by remember { mutableStateOf<TaskQuestionPayload?>(null) }
    var answerSheetId by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var versionNo by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var currentIndex by rememberSaveable(taskId) { mutableStateOf(0) }
    val answers = remember(taskId) { mutableStateListOf<AnswerItemRequest>() }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(taskId) {
        runCatching {
            repository.fetchTaskQuestions(taskId)
        }.onSuccess {
            answerSheetId = it.draftAnswerSheetId
            versionNo = it.draftVersionNo
            currentIndex = 0
            answers.clear()
            answers.addAll(
                it.draftAnswers.map { draft ->
                    AnswerItemRequest(
                        questionId = draft.questionId,
                        optionId = draft.optionId,
                        answerText = draft.answerText,
                        answerValue = draft.answerValue
                    )
                }
            )
            payload = it
        }.onFailure {
            message = it.message ?: "题目加载失败"
        }
        loading = false
    }

    val data = payload
    if (loading) {
        FullscreenLoading("正在加载题目...")
        return
    }
    if (data == null) {
        ErrorFullScreen(message ?: "没有找到题目数据。", onBack)
        return
    }
    if (data.completedFlag && data.completedReportId != null) {
        ErrorFullScreen("该任务已完成，可直接查看报告。", onBack, "查看报告") {
            onSubmitted(data.completedReportId)
        }
        return
    }

    val current = data.questions.getOrNull(currentIndex)
    val progress = if (data.questions.isEmpty()) 0f else (currentIndex + 1f) / data.questions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientHeader(data.scaleName, "任务 ${data.taskId} · 共 ${data.questions.size} 题")
        androidx.compose.material3.LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        current?.let { question ->
            ElevatedPanel(modifier = Modifier.weight(1f, fill = false)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${question.questionNo}. ${question.questionTitle}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("题型：${questionTypeLabel(question.questionType)}", color = Color(0xFF587082))
                    QuestionEditor(
                        question = question,
                        existing = answers.filter { it.questionId == question.questionId },
                        onChanged = { replacement ->
                            message = null
                            answers.removeAll { it.questionId == question.questionId }
                            answers.addAll(replacement)
                        }
                    )
                }
            }
        }
        message?.let { ErrorCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("返回")
            }
            if (data.allowSaveFlag) {
                OutlinedButton(
                    onClick = {
                        validateClientAnswers(data, answers.toList(), requireCompleteAnswers = false)?.let {
                            message = it
                            return@OutlinedButton
                        }
                        processing = true
                        scope.launch {
                            runCatching {
                                repository.saveAnswerSheet(
                                    SaveAnswerSheetRequest(
                                        taskId = data.taskId,
                                        scaleId = data.scaleId,
                                        answerSheetId = answerSheetId,
                                        versionNo = versionNo,
                                        answers = answers.toList()
                                    )
                                )
                            }.onSuccess {
                                answerSheetId = it.answerSheetId
                                versionNo = it.versionNo
                                message = "草稿已保存"
                            }.onFailure {
                                message = it.message ?: "草稿保存失败"
                            }
                            processing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !processing
                ) {
                    Text("保存")
                }
            }
            Button(
                onClick = {
                    if (currentIndex < data.questions.lastIndex) {
                        message = null
                        currentIndex += 1
                    } else {
                        validateClientAnswers(data, answers.toList(), requireCompleteAnswers = true)?.let {
                            message = it
                            return@Button
                        }
                        processing = true
                        scope.launch {
                            runCatching {
                                repository.submitAnswerSheet(
                                    SubmitAnswerSheetRequest(
                                        taskId = data.taskId,
                                        scaleId = data.scaleId,
                                        answerSheetId = answerSheetId,
                                        versionNo = versionNo,
                                        submitToken = UUID.randomUUID().toString(),
                                        answers = answers.toList()
                                    )
                                )
                            }.onSuccess {
                                onSubmitted(it.reportId)
                            }.onFailure {
                                message = it.message ?: "提交失败"
                            }
                            processing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !processing
            ) {
                Text(if (currentIndex < data.questions.lastIndex) "下一题" else "提交")
            }
        }
    }
}

@Composable
private fun ReportsScreen(
    repository: RespondentRepository,
    onOpenReport: (Long) -> Unit
) {
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            reports = repository.fetchMyReports()
        }.onFailure {
            error = it.message ?: "报告加载失败"
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading("正在加载报告...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("我的报告", "这里只展示个人端可理解的结果与建议。")
        }
        if (error != null) item { ErrorCard(error!!) }
        if (reports.isEmpty()) {
            item { EmptyHint("当前没有可查看报告") }
        } else {
            items(reports, key = { it.reportId }) { report ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(report.scaleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(report.taskName, color = Color(0xFF5E7384))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(riskLabel(report.riskLevel))
                            StatusPill("总分 ${report.totalScore}", filled = false)
                        }
                        Text(report.createdAt, color = Color(0xFF5E7384))
                        Button(onClick = { onOpenReport(report.reportId) }) {
                            Text("查看报告")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDetailScreen(
    repository: RespondentRepository,
    reportId: Long,
    onBack: () -> Unit
) {
    var report by remember { mutableStateOf<ReportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reportId) {
        runCatching {
            report = repository.fetchReportDetail(reportId)
        }.onFailure {
            error = it.message ?: "报告加载失败"
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading("正在加载报告详情...")
        return
    }
    if (report == null) {
        ErrorFullScreen(error ?: "没有找到报告", onBack)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("评估结果", riskSummary(report!!.riskLevel))
        }
        item {
            ElevatedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("当前状态", style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(riskLabel(report!!.riskLevel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(report!!.content, style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                    Text("建议下一步", style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(nextSuggestion(report!!.riskLevel), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            SectionCard("答题摘要") {
                if (report!!.answerDetails.isEmpty()) {
                    EmptyHint("当前没有答题明细")
                } else {
                    report!!.answerDetails.take(10).forEach { answer ->
                        AnswerSummaryLine(answer)
                    }
                }
            }
        }
        if (
            report!!.scoreSource != null ||
            report!!.standardScore != null ||
            report!!.zScore != null ||
            report!!.tScore != null ||
            report!!.normCode != null ||
            report!!.highRiskFlag
        ) {
            item {
                SectionCard("Scoring Details") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report!!.scoreSource?.let { Text("Score source: $it") }
                        report!!.standardScore?.let { Text("Standard score: $it") }
                        report!!.zScore?.let { Text("Z-score: $it") }
                        report!!.tScore?.let { Text("T-score: $it") }
                        report!!.normCode?.let { Text("Norm code: $it") }
                        if (report!!.highRiskFlag) {
                            StatusPill("High Risk", filled = false)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun AppointmentsScreen(repository: RespondentRepository) {
    var counselors by remember { mutableStateOf<List<CounselorOption>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<CounselorSchedule>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<AppointmentSummary>>(emptyList()) }
    var selectedCounselorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedScheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remark by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        counselors = repository.fetchCounselors()
        appointments = repository.fetchMyAppointments()
        if (selectedCounselorId != null) {
            schedules = repository.fetchCounselorSchedules(selectedCounselorId!!)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: "预约数据加载失败"
        }
        loading = false
    }

    LaunchedEffect(selectedCounselorId) {
        selectedCounselorId?.let { id ->
            runCatching {
                schedules = repository.fetchCounselorSchedules(id)
            }.onFailure {
                error = it.message ?: "排班加载失败"
            }
        }
    }

    if (loading) {
        FullscreenLoading("正在加载预约数据...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("预约咨询", "先选咨询师，再选排班时段，最后提交预约。")
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            SectionCard("新建预约") {
                if (counselors.isEmpty()) {
                    EmptyHint("当前没有可预约咨询师")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        counselors.forEach { counselor ->
                            FilterChip(
                                selected = selectedCounselorId == counselor.userId,
                                onClick = { selectedCounselorId = counselor.userId },
                                label = { Text("${counselor.displayName} (${counselor.username})") }
                            )
                        }
                        if (selectedCounselorId != null) {
                            Text("可预约时段", fontWeight = FontWeight.Bold)
                            schedules.forEach { schedule ->
                                FilterChip(
                                    selected = selectedScheduleId == schedule.id,
                                    onClick = { selectedScheduleId = schedule.id },
                                    label = { Text("${schedule.scheduleDate} ${schedule.startTime} - ${schedule.endTime}") }
                                )
                            }
                            OutlinedTextField(
                                value = remark,
                                onValueChange = { remark = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注") }
                            )
                            Button(
                                onClick = {
                                    if (selectedCounselorId == null || selectedScheduleId == null) {
                                        error = "请先选择咨询师和排班。"
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                repository.createAppointment(
                                                    CreateAppointmentRequest(
                                                        counselorUserId = selectedCounselorId!!,
                                                        scheduleId = selectedScheduleId!!,
                                                        remark = remark.ifBlank { null }
                                                    )
                                                )
                                                reload()
                                                remark = ""
                                                selectedScheduleId = null
                                            }.onFailure {
                                                error = it.message ?: "预约创建失败"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("提交预约")
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard("我的预约") {
                if (appointments.isEmpty()) {
                    EmptyHint("当前没有预约记录")
                } else {
                    appointments.forEach { appointment ->
                        AppointmentLine(appointment) {
                            scope.launch {
                                runCatching {
                                    repository.cancelAppointment(appointment.id)
                                    reload()
                                }.onFailure {
                                    error = it.message ?: "取消预约失败"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(repository: RespondentRepository) {
    var notifications by remember { mutableStateOf<List<MyNotification>>(emptyList()) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        notifications = repository.fetchMyNotifications()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: "通知加载失败"
        }
        loading = false
    }

    val visible = if (unreadOnly) notifications.filter { !it.readFlag } else notifications

    if (loading) {
        FullscreenLoading("正在加载通知...")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader("通知消息", "未读 ${notifications.count { !it.readFlag }} 条")
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = !unreadOnly },
                label = { Text(if (unreadOnly) "只看未读" else "显示全部") }
            )
        }
        if (visible.isEmpty()) {
            item { EmptyHint(if (unreadOnly) "没有未读通知" else "当前没有通知") }
        } else {
            items(visible, key = { it.id }) { notification ->
                ElevatedPanel(containerColor = if (notification.readFlag) Color.White else Color(0xFFF6FBFF)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notification.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(if (notification.readFlag) "已读" else "未读", filled = !notification.readFlag)
                        }
                        Text(notification.content, color = Color(0xFF35495A))
                        Text(notification.createdAt, color = Color(0xFF6A7F90))
                        if (!notification.readFlag) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        repository.markNotificationRead(notification.id)
                                        reload()
                                    }.onFailure {
                                        error = it.message ?: "标记已读失败"
                                    }
                                }
                            }) {
                                Text("标记已读")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditor(
    question: TaskQuestionItem,
    existing: List<AnswerItemRequest>,
    onChanged: (List<AnswerItemRequest>) -> Unit
) {
    when (question.questionType) {
        "MULTI_SELECT" -> {
            var selectedOptionIds by rememberSaveable(question.questionId) {
                mutableStateOf(existing.mapNotNull { it.optionId })
            }
            LaunchedEffect(question.questionId, existing) {
                selectedOptionIds = existing.mapNotNull { it.optionId }
            }
            val optionMap = question.options.associateBy { it.optionId }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.optionId in selectedOptionIds,
                            onCheckedChange = { checked ->
                                selectedOptionIds = if (checked) {
                                    selectedOptionIds.updatedMultiSelectOptions(option, optionMap)
                                } else {
                                    selectedOptionIds.filterNot { it == option.optionId }
                                }
                                onChanged(selectedOptionIds.map { AnswerItemRequest(question.questionId, optionId = it) })
                            }
                        )
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
            }
        }
        "TEXT" -> {
            var text by rememberSaveable(question.questionId) { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                text = existing.firstOrNull()?.answerText.orEmpty()
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChanged(if (it.isBlank()) emptyList() else listOf(AnswerItemRequest(question.questionId, answerText = it)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("请输入答案") }
            )
        }
        "SLIDER" -> {
            var value by rememberSaveable(question.questionId) { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                value = existing.firstOrNull()?.answerValue.toDisplayValue()
            }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.toDecimalInput()
                    onChanged(
                        value.toDoubleOrNull()?.let { amount ->
                            listOf(AnswerItemRequest(question.questionId, answerValue = amount))
                        } ?: emptyList()
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入分值 ${question.sliderMin ?: 0} - ${question.sliderMax ?: 100}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        "TEXT_WITH_OPTION" -> {
            var selectedOptionId by rememberSaveable(question.questionId) { mutableStateOf<Long?>(null) }
            var extraText by rememberSaveable("${question.questionId}-text") { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                selectedOptionId = existing.firstOrNull()?.optionId
                extraText = existing.firstOrNull()?.answerText.orEmpty()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    OutlinedButton(
                        onClick = {
                            selectedOptionId = option.optionId
                            onChanged(listOf(AnswerItemRequest(question.questionId, optionId = option.optionId, answerText = extraText)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedOptionId == option.optionId) Color(0xFFEAF3FB) else Color.Transparent
                        )
                    ) {
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
                OutlinedTextField(
                    value = extraText,
                    onValueChange = {
                        extraText = it
                        onChanged(
                            selectedOptionId?.let { choice ->
                                listOf(AnswerItemRequest(question.questionId, optionId = choice, answerText = it))
                            } ?: emptyList()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(question.textInputPlaceholder ?: "补充说明") }
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    val chosen = existing.firstOrNull()?.optionId == option.optionId
                    OutlinedButton(
                        onClick = { onChanged(listOf(AnswerItemRequest(question.questionId, optionId = option.optionId))) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (chosen) Color(0xFFEAF3FB) else Color.Transparent
                        )
                    ) {
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
            }
        }
    }
}

private fun List<Long>.updatedMultiSelectOptions(
    option: org.sainm.psy.respondent.data.model.TaskQuestionOption,
    optionMap: Map<Long, org.sainm.psy.respondent.data.model.TaskQuestionOption>
): List<Long> {
    if (option.exclusiveFlag) {
        return listOf(option.optionId)
    }
    return filterNot { optionMap[it]?.exclusiveFlag == true } + option.optionId
}

private fun Double?.toDisplayValue(): String =
    this?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()

private fun String.toDecimalInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val decimalPoint = filtered.indexOf('.')
    if (decimalPoint < 0) {
        return filtered
    }
    val integerPart = filtered.substring(0, decimalPoint + 1)
    val fractionPart = filtered.substring(decimalPoint + 1).replace(".", "")
    return integerPart + fractionPart
}

private fun validateClientAnswers(
    payload: TaskQuestionPayload,
    answers: List<AnswerItemRequest>,
    requireCompleteAnswers: Boolean = true
): String? {
    val questionMap = payload.questions.associateBy { it.questionId }
    val answersByQuestionId = answers.groupBy { it.questionId }

    answersByQuestionId.keys
        .firstOrNull { it !in questionMap }
        ?.let { invalidQuestionId ->
            return "Answer references an invalid question: $invalidQuestionId"
        }

    payload.questions.forEach { question ->
        val questionAnswers = answersByQuestionId[question.questionId].orEmpty()
        if (requireCompleteAnswers && question.requiredFlag && questionAnswers.isEmpty()) {
            return "Required question #${question.questionNo} has not been answered."
        }
        validateQuestionAnswers(question, questionAnswers)?.let { return it }
    }

    return null
}

private fun validateQuestionAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.isEmpty()) {
        return null
    }
    return when (question.questionType) {
        "SINGLE_CHOICE", "MATRIX" -> validateSingleChoiceAnswers(question, answers)
        "MULTI_SELECT" -> validateMultiSelectAnswers(question, answers)
        "SLIDER" -> validateSliderAnswers(question, answers)
        "TEXT" -> validateTextAnswers(question, answers)
        "TEXT_WITH_OPTION" -> validateTextWithOptionAnswers(question, answers)
        else -> "Question type ${question.questionType} is not supported."
    }
}

private fun validateSingleChoiceAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return "Question #${question.questionNo} must contain exactly one selected option."
    }
    val answer = answers.first()
    if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
        return "Question #${question.questionNo} contains an invalid option selection."
    }
    if (answer.answerValue != null) {
        return "Question #${question.questionNo} does not accept a numeric answer value."
    }
    if (!answer.answerText.isNullOrBlank()) {
        return "Question #${question.questionNo} does not accept text input."
    }
    return null
}

private fun validateMultiSelectAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.isEmpty()) {
        return "Question #${question.questionNo} must contain at least one selected option."
    }
    val optionIds = answers.mapNotNull { it.optionId }
    if (optionIds.size != answers.size || optionIds.distinct().size != optionIds.size) {
        return "Question #${question.questionNo} contains duplicate or invalid multi-select answers."
    }
    val optionMap = question.options.associateBy { it.optionId }
    if (optionIds.any { it !in optionMap }) {
        return "Question #${question.questionNo} contains an invalid option selection."
    }
    if (question.optionSelectionLimit != null && optionIds.size > question.optionSelectionLimit) {
        return "Question #${question.questionNo} exceeds the selection limit of ${question.optionSelectionLimit}."
    }
    val exclusiveSelected = optionIds.count { optionMap.getValue(it).exclusiveFlag }
    if (exclusiveSelected > 1 || (exclusiveSelected == 1 && optionIds.size > 1)) {
        return "Question #${question.questionNo} contains an exclusive option conflict."
    }
    if (answers.any { it.answerValue != null }) {
        return "Question #${question.questionNo} does not accept a numeric answer value."
    }
    if (answers.any { !it.answerText.isNullOrBlank() }) {
        return "Question #${question.questionNo} does not accept text input."
    }
    return null
}

private fun validateSliderAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return "Question #${question.questionNo} must contain exactly one slider value."
    }
    val answer = answers.first()
    val value = answer.answerValue
        ?: return "Question #${question.questionNo} requires a slider value."
    if (answer.optionId != null) {
        return "Question #${question.questionNo} does not accept option selection."
    }
    if (!answer.answerText.isNullOrBlank()) {
        return "Question #${question.questionNo} does not accept text input."
    }
    val min = question.sliderMin
    val max = question.sliderMax
    if (min == null || max == null || value < min || value > max) {
        return "Question #${question.questionNo} slider value is out of range."
    }
    question.sliderStep
        ?.takeIf { it > 0.0 }
        ?.let { step ->
            val offset = BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(min))
            if (offset.remainder(BigDecimal.valueOf(step)).compareTo(BigDecimal.ZERO) != 0) {
                return "Question #${question.questionNo} slider value must match step ${step.toDisplayValue()}."
            }
        }
    return null
}

private fun validateTextAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return "Question #${question.questionNo} must contain exactly one text answer."
    }
    val answer = answers.first()
    if (answer.optionId != null || answer.answerValue != null) {
        return "Question #${question.questionNo} does not accept option selection."
    }
    if (answer.answerText.isNullOrBlank()) {
        return "Question #${question.questionNo} requires text input."
    }
    return null
}

private fun validateTextWithOptionAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return "Question #${question.questionNo} must contain exactly one selected option."
    }
    val answer = answers.first()
    if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
        return "Question #${question.questionNo} contains an invalid option selection."
    }
    if (answer.answerValue != null) {
        return "Question #${question.questionNo} does not accept a numeric answer value."
    }
    if (question.textInputEnabled == true) {
        if (answer.answerText.isNullOrBlank()) {
            return "Question #${question.questionNo} requires text input."
        }
    } else if (!answer.answerText.isNullOrBlank()) {
        return "Question #${question.questionNo} does not accept text input."
    }
    return null
}

@Composable
private fun GradientHeader(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF163957), Color(0xFF2A6F9B), Color(0xFF75B8C2)))
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = true) {
    ElevatedPanel(modifier = modifier, containerColor = if (emphasized) Color.White else Color(0xFFF7FBFF)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF678091))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ElevatedPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun ListLine(title: String, subtitle: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF5E7384))
        TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
            Text(actionLabel)
        }
        HorizontalDivider()
    }
}

@Composable
private fun StatusPill(label: String, filled: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Color(0xFFEAF3FB) else Color(0xFFF5F7FA))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF23455F))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF718697), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedPanel(containerColor = Color(0xFFFFF4F2)) {
        Text(message, color = Color(0xFF9A3C2B))
    }
}

@Composable
private fun FullscreenLoading(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, color = Color(0xFF587082))
        }
    }
}

@Composable
private fun ErrorFullScreen(message: String, onBack: () -> Unit, actionLabel: String = "返回", action: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, textAlign = TextAlign.Center, color = Color(0xFF7A3B30))
            Button(onClick = action ?: onBack) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AnswerSummaryLine(answer: ReportAnswerDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${answer.questionNo}. ${answer.questionTitle}", fontWeight = FontWeight.Bold)
        Text(answer.optionLabel ?: answer.answerText ?: answer.answerValue?.toString() ?: "-", color = Color(0xFF567082))
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AppointmentLine(appointment: AppointmentSummary, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(appointment.counselorDisplayName ?: "咨询师 #${appointment.counselorUserId}", fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(appointment.scheduleDate, appointment.startTime, appointment.endTime).joinToString(" "),
            color = Color(0xFF5E7384)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(appointmentStatusLabel(appointment.appointmentStatus))
            StatusPill(sourceLabel(appointment.sourceType), filled = false)
        }
        appointment.remark?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFF5E7384))
        }
        if (appointment.appointmentStatus != "COMPLETED" && appointment.appointmentStatus != "CANCELLED") {
            TextButton(onClick = onCancel) {
                Text("取消预约")
            }
        }
        HorizontalDivider()
    }
}

private fun questionTypeLabel(type: String): String = when (type) {
    "SINGLE_CHOICE" -> "单选"
    "MULTI_SELECT" -> "多选"
    "SLIDER" -> "分值"
    "MATRIX" -> "矩阵"
    "TEXT" -> "文本"
    "TEXT_WITH_OPTION" -> "选项加说明"
    else -> type
}

private fun appointmentStatusLabel(status: String): String = when (status) {
    "CREATED", "CONFIRMED" -> "待处理"
    "COMPLETED" -> "已完成"
    "CANCELLED" -> "已取消"
    else -> status
}

private fun sourceLabel(source: String): String = when (source) {
    "USER" -> "用户发起"
    "ADMIN" -> "管理端创建"
    else -> source
}

private fun riskLabel(level: String): String = when (level) {
    "HIGH" -> "需要重点关注"
    "MEDIUM" -> "建议持续关注"
    else -> "整体平稳"
}

private fun riskSummary(level: String): String = when (level) {
    "HIGH" -> "本次结果提示当前状态波动较明显，建议尽快与老师或咨询师沟通。"
    "MEDIUM" -> "本次结果提示近期可能存在一定压力，请持续观察睡眠、情绪和节奏。"
    else -> "本次测评整体平稳，请继续保持规律作息与适度运动。"
}

private fun nextSuggestion(level: String): String = when (level) {
    "HIGH" -> "建议尽快预约咨询，并结合近期睡眠、压力、人际事件做进一步沟通。"
    "MEDIUM" -> "建议一到两周后再次测评，如压力持续升高，可预约咨询。"
    else -> "保持规律作息、适度运动和稳定节奏；若状态持续变化，可再次测评。"
}
