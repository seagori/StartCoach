package com.seagori.hgr.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    KOREAN("ko"),
    JAPANESE("ja"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    CHINESE_TRADITIONAL("zh-Hant"),
    ENGLISH("en");

    companion object {
        fun fromStored(value: String?): AppLanguage =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class AppLang { KO, JA, ZH_HANS, ZH_HANT, EN }

fun resolveAppLang(language: AppLanguage): AppLang = when (language) {
    AppLanguage.KOREAN -> AppLang.KO
    AppLanguage.JAPANESE -> AppLang.JA
    AppLanguage.CHINESE_SIMPLIFIED -> AppLang.ZH_HANS
    AppLanguage.CHINESE_TRADITIONAL -> AppLang.ZH_HANT
    AppLanguage.ENGLISH -> AppLang.EN
    AppLanguage.SYSTEM -> when (Locale.getDefault().language) {
        "ko" -> AppLang.KO
        "ja" -> AppLang.JA
        "zh" -> if (isTraditionalChinese(Locale.getDefault())) AppLang.ZH_HANT else AppLang.ZH_HANS
        else -> AppLang.EN
    }
}

/** 스크립트(Hant) 우선, 없으면 국가/지역(TW·HK·MO)으로 정체를 판별한다. */
private fun isTraditionalChinese(locale: Locale): Boolean {
    val script = locale.script
    if (script.isNotEmpty()) return script.equals("Hant", ignoreCase = true)
    if (locale.toLanguageTag().contains("hant", ignoreCase = true)) return true
    return locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
}

/** 앱 전체에서 사용하는 번역 문자열. 시스템 언어가 기준이며 설정에서 즉시 바꿀 수 있다. */
class AppStrings(val lang: AppLang) {
    private fun pick(
        ko: String,
        ja: String,
        zhHans: String,
        zhHant: String,
        en: String
    ): String = when (lang) {
        AppLang.KO -> ko
        AppLang.JA -> ja
        AppLang.ZH_HANS -> zhHans
        AppLang.ZH_HANT -> zhHant
        AppLang.EN -> en
    }

    // 공통
    val appName = pick("한걸음", "一歩", "一小步", "一小步", "One Step")
    val back = pick("뒤로", "戻る", "返回", "返回", "Back")
    val delete = pick("삭제", "削除", "删除", "刪除", "Delete")
    val close = pick("닫기", "閉じる", "关闭", "關閉", "Close")
    val save = pick("저장", "保存", "保存", "儲存", "Save")
    val cancel = pick("취소", "キャンセル", "取消", "取消", "Cancel")
    val goalFallback = pick("목표", "目標", "目标", "目標", "Goal")
    val materialFallback = pick("자료", "資料", "资料", "資料", "Material")

    // 하단 탭
    val navHome = pick("홈", "ホーム", "首页", "首頁", "Home")
    val navRecords = pick("기록", "記録", "记录", "紀錄", "Records")
    val navGoals = pick("목표", "目標", "目标", "目標", "Goals")
    val navSettings = pick("설정", "設定", "设置", "設定", "Settings")

    // 홈
    val homeTitle = pick(
        "오늘,\n무엇을 할까요?",
        "今日、\n何をしますか？",
        "今天\n想做什么？",
        "今天\n想做什麼？",
        "What do you\nwant to do today?"
    )
    val homeInputHint = pick(
        "하고 싶은 것 입력하기",
        "やりたいことを入力",
        "输入你想做的事",
        "輸入你想做的事",
        "Enter what you want to do"
    )
    val homeInputPlaceholder = pick(
        "운동, 일본어, …",
        "運動、韓国語、…",
        "运动、日语、…",
        "運動、日語、…",
        "Exercise, Japanese, …"
    )
    val homeShowSuggestions = pick("제안 보기", "提案を見る", "查看建议", "查看建議", "Show suggestions")
    val homeGoalsTitle = pick("내 목표", "自分の目標", "我的目标", "我的目標", "My goals")
    val homeNewStart = pick("새로 시작하기", "新しく始める", "重新开始", "重新開始", "Start fresh")
    fun attempts(count: Int) = pick(
        "${count}회 시도",
        "${count}回実践",
        "尝试 $count 次",
        "嘗試 $count 次",
        if (count == 1) "1 attempt" else "$count attempts"
    )

    val homeEmpty = pick(
        "아직 목표가 없어요. 위에서 첫 목표를 입력해 보세요.",
        "まだ目標がありません。上で最初の目標を入力してみましょう。",
        "还没有目标。请在上方输入第一个目标。",
        "還沒有目標。請在上方輸入第一個目標。",
        "No goals yet. Enter your first goal above."
    )
    val errorEnterTask = pick(
        "할 일을 입력해 주세요",
        "やることを入力してください",
        "请输入要做的事",
        "請輸入要做的事",
        "Please enter what you want to do"
    )

    // 기록
    val recordsTitle = pick("기록", "記録", "记录", "紀錄", "Records")
    val recordsSubtitle = pick(
        "지난 2주의 활동과 날짜별 기록입니다.",
        "過去2週間の活動と日付ごとの記録です。",
        "最近两周的活动和按日期排列的记录。",
        "最近兩週的活動與依日期排列的紀錄。",
        "Your last two weeks of activity and dated records."
    )
    val growth = pick("성장", "成長", "成长", "成長", "Growth")
    fun firstAttempts(count: Int) = pick(
        "시작한 시도 ${count}회",
        "始めた回数: ${count}回",
        "开始尝试 $count 次",
        "開始嘗試 $count 次",
        "Started attempts: $count"
    )

    fun startSuccessRate(percent: Int) = pick(
        "시작 성공률 ${percent}%",
        "開始成功率 ${percent}%",
        "开始成功率 ${percent}%",
        "開始成功率 ${percent}%",
        "Start success rate: $percent%"
    )

    fun actualVolume(actual: Int, estimated: Int) = pick(
        "실제 수행 ${actual}회 (추정 ${estimated}회)",
        "実際の実行 ${actual}回（推定 ${estimated}回）",
        "实际完成 $actual 次（估算 $estimated 次）",
        "實際完成 $actual 次（估算 $estimated 次）",
        "Actual: $actual (estimated: $estimated)"
    )

    fun difficultyTrend(value: Int): String {
        val sign = if (value >= 0) "+" else ""
        return pick(
            "난도 변화 $sign$value",
            "難易度の変化 $sign$value",
            "难度变化 $sign$value",
            "難度變化 $sign$value",
            "Difficulty change $sign$value"
        )
    }

    val recordsEmpty = pick(
        "아직 기록이 없어요. 작은 행동부터 시작해 보세요.",
        "まだ記録がありません。小さな行動から始めてみましょう。",
        "还没有记录。从一个小行动开始吧。",
        "還沒有紀錄。從一個小行動開始吧。",
        "No records yet. Start with a small action."
    )
    val attempted = pick("시도함", "実施済み", "已尝试", "已嘗試", "Attempted")
    val notAttempted = pick("미시도", "未実施", "未尝试", "未嘗試", "Not attempted")
    val estimated = pick("추정", "推定", "估算", "估算", "Estimated")
    val actual = pick("실제", "実際", "实际", "實際", "Actual")

    // 기록: 지난 2주 활동
    val lastTwoWeeks = pick("지난 2주", "過去2週間", "最近两周", "最近兩週", "Last 2 weeks")
    fun activeDaysLabel(active: Int, total: Int) = pick(
        "${total}일 중 ${active}일 활동",
        "過去${total}日のうち${active}日活動",
        "${total} 天中 ${active} 天有活动",
        "${total} 天中 ${active} 天有活動",
        "Active on $active of $total days"
    )

    val streakPrefix = pick("", "", "连续", "連續", "")
    val streakSuffix = pick("일 연속", "日連続", "天", "天", "day streak")
    fun achievementLabel(percent: Int) = pick(
        "활동일 비율: $percent%",
        "活動日の割合: $percent%",
        "活动天数占比：$percent%",
        "活動天數占比：$percent%",
        "Active days: $percent%"
    )

    fun thisWeekAttempts(count: Int) = pick(
        "이번 주 ${count}회",
        "今週 ${count}回",
        "本周 $count 次",
        "本週 $count 次",
        if (count == 1) "This week: 1 attempt" else "This week: $count attempts"
    )

    fun lastWeekAttempts(count: Int) = pick(
        "지난주 ${count}회",
        "先週 ${count}回",
        "上周 $count 次",
        "上週 $count 次",
        if (count == 1) "Last week: 1 attempt" else "Last week: $count attempts"
    )

    fun weekDelta(percent: Int): String {
        val sign = if (percent >= 0) "+" else ""
        return pick(
            "지난주보다 $sign$percent%",
            "先週比 $sign$percent%",
            "较上周 $sign$percent%",
            "較上週 $sign$percent%",
            "$sign$percent% vs last week"
        )
    }

    val noLastWeek = pick("지난주 기록 없음", "先週の記録なし", "上周没有记录", "上週沒有紀錄", "No activity last week")
    fun activityCount(count: Int) = pick(
        "${count}회",
        "${count}回",
        "${count} 次",
        "${count} 次",
        if (count == 1) "1 check-in" else "$count check-ins"
    )

    fun completionCount(count: Int) = pick("완료 $count", "完了 $count", "完成 $count", "完成 $count", "$count done")
    fun minuteCount(minutes: Int) =
        pick("${minutes}분", "${minutes}分", "${minutes} 分钟", "${minutes} 分鐘", "$minutes min")

    val noActivity = pick("활동 없음", "活動なし", "无活动", "無活動", "No activity")
    val todayNoActivity = pick("아직 기록 없음", "まだ記録なし", "今天还没有记录", "今天還沒有紀錄", "No check-in yet")
    val activityEmptyNote = pick(
        "기록이 쌓이면 막대가 채워져요.",
        "記録がたまるとグラフが埋まります。",
        "记录累积后，柱状图会逐渐填满。",
        "紀錄累積後，長條圖會逐漸填滿。",
        "Your bars fill in as you record."
    )
    val todayLabel = pick("오늘", "今日", "今天", "今天", "Today")
    fun weekdayShort(dayOfWeek: Int): String {
        val names = when (lang) {
            AppLang.KO -> listOf("월", "화", "수", "목", "금", "토", "일")
            AppLang.JA -> listOf("月", "火", "水", "木", "金", "土", "日")
            AppLang.ZH_HANS -> listOf("一", "二", "三", "四", "五", "六", "日")
            AppLang.ZH_HANT -> listOf("一", "二", "三", "四", "五", "六", "日")
            AppLang.EN -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        }
        return names[(dayOfWeek - 1).coerceIn(0, names.lastIndex)]
    }

    // 목표
    val goalsTitle = pick("목표", "目標", "目标", "目標", "Goals")
    val newGoal = pick("새 목표", "新しい目標", "新目标", "新目標", "New goal")
    val noLongTermTarget = pick("장기 목표 없음", "長期目標なし", "无长期目标", "無長期目標", "No long-term target")
    fun goalMeta(status: String, target: String) = "$status · $target"
    val relatedRecords = pick("관련 기록", "関連記録", "相关记录", "相關紀錄", "Related records")
    val goalCreateTitle = pick("목표 만들기", "目標を作成", "创建目标", "建立目標", "Create goal")
    val goalEditTitle = pick("목표 수정", "目標を編集", "编辑目标", "編輯目標", "Edit goal")
    val goalTitleLabel = pick("제목", "タイトル", "标题", "標題", "Title")
    val longTermTargetLabel = pick("장기 목표", "長期目標", "长期目标", "長期目標", "Long-term target")
    val learningMaterials = pick("학습 자료", "学習資料", "学习资料", "學習資料", "Learning materials")
    val importMarkdown = pick("마크다운 가져오기", "Markdownを読み込む", "导入 Markdown", "匯入 Markdown", "Import Markdown")
    val materialImported = pick("자료를 넣었어요", "資料を追加しました", "已添加资料", "已新增資料", "Material added")
    val importFailed = pick("가져오기 실패", "読み込み失敗", "导入失败", "匯入失敗", "Import failed")

    // 학습 경로
    val pathSection = pick("학습 경로", "学習プラン", "学习路径", "學習路徑", "Learning path")
    val buildPath = pick("경로 만들기", "プランを作成", "生成路径", "建立路徑", "Build path")
    val rebuildPath = pick("경로 다시 만들기", "プランを作り直す", "重新生成路径", "重新建立路徑", "Rebuild path")
    val pathBuilding = pick("경로를 설계하는 중…", "プランを設計中…", "正在设计路径…", "正在設計路徑…", "Designing the path…")
    val pathBuilt = pick("경로를 만들었어요", "プランを作成しました", "路径已生成", "已建立路徑", "Path created")
    val pathBuildFailed =
        pick("경로를 만들지 못했어요", "プランを作成できませんでした", "无法生成路径", "無法建立路徑", "Couldn't build the path")
    val pathBuildEmpty = pick(
        "경로에 쓸 단계를 받지 못했습니다",
        "プランに使えるステップを受け取れませんでした",
        "没有收到可用于路径的步骤",
        "沒有收到可用於路徑的步驟",
        "No usable steps were returned for the path"
    )
    val pathFallbackTitle = pick("학습 경로", "学習プラン", "学习路径", "學習路徑", "Learning path")
    val noPathYet = pick("아직 경로가 없어요", "まだ学習プランがありません", "还没有学习路径", "還沒有學習路徑", "No path yet")
    val pathMaterialHint = pick(
        "자세한 커리큘럼은 마크다운 자료로 넣어 주세요. 자료의 목차를 바탕으로 단계를 설계합니다.",
        "詳しいカリキュラムはMarkdown資料として読み込んでください。資料の目次をもとに学習プランを作成します。",
        "如需详细课程，请导入 Markdown 资料。应用会根据资料目录设计学习路径。",
        "如需詳細課程，請匯入 Markdown 資料。應用程式會根據資料目錄設計學習路徑。",
        "For a detailed curriculum, import a Markdown material. Steps are designed from its outline."
    )
    val pathMaterialSource = pick("자료 기반", "資料ベース", "基于资料", "基於資料", "From material")
    fun stepProgress(done: Int, total: Int) = pick(
        "$done/$total 단계",
        "$done/$total ステップ",
        "$done/$total 步",
        "$done/$total 步",
        "$done/$total steps"
    )

    fun nextStepLabel(title: String) =
        pick("다음: $title", "次: $title", "下一步：$title", "下一步：$title", "Next: $title")

    val allStepsDone = pick(
        "모든 단계를 마쳤어요",
        "すべてのステップを終えました",
        "已完成所有步骤",
        "已完成所有步驟",
        "You finished every step"
    )
    val allStepsDoneNote = pick(
        "스스로 확인한 기록이 쌓였습니다. 기록 탭에서 돌아볼 수 있어요.",
        "自分で確認した記録が積み重なりました。記録タブで振り返れます。",
        "你自行确认的记录已保存。可以在“记录”标签页中回顾。",
        "你自行確認的紀錄已儲存。可以在「紀錄」標籤頁中回顧。",
        "Your self-checks are saved. Review them in Records."
    )
    val stepCriteriaTitle = pick("스스로 확인할 기준", "自分で確認する基準", "自查标准", "自我檢查標準", "Check it yourself")
    val selfCheckOnly = pick(
        "사진 없이 스스로 평가한 결과만 기록합니다.",
        "写真なしで自己評価した結果だけを記録します。",
        "只记录你自己评估的结果，无需照片。",
        "只記錄你自己評估的結果，無需照片。",
        "Only your own self-assessment is recorded. No photos."
    )
    val stepDoneAction =
        pick("기준을 모두 만족했어요", "基準をすべて満たしました", "满足所有标准", "符合所有標準", "I met every criterion")
    val stepPartialAction =
        pick("일부만 만족했어요", "一部だけ満たしました", "只满足部分标准", "只符合部分標準", "I met some criteria")
    val currentStep = pick("현재 단계", "現在のステップ", "当前步骤", "目前步驟", "Current step")

    // 설정: 모델 프로필
    val fastModelSection =
        pick("빠른 제안 모델", "クイック提案モデル", "快速建议模型", "快速建議模型", "Quick suggestion model")
    val fastModelNote = pick(
        "목표를 처음 입력했을 때 즉석 제안을 만듭니다. 짧고 빠른 모델을 권장합니다.",
        "目標を最初に入力したときの即時提案を作ります。短く速いモデルがおすすめです。",
        "首次输入目标时生成即时建议。建议使用短小快速的模型。",
        "首次輸入目標時產生即時建議。建議使用短小快速的模型。",
        "Creates instant suggestions when a goal is first entered. A short, fast model is recommended."
    )
    val coachModelSection = pick(
        "경로·세션 코치 모델",
        "学習プラン・セッションコーチモデル",
        "路径与会话教练模型",
        "路徑與會話教練模型",
        "Path & session coach model"
    )
    val coachModelNote = pick(
        "마크다운 자료로 학습 경로를 설계하고, 각 단계의 연습을 안내합니다. 주소를 비우면 빠른 제안 모델을 함께 사용합니다.",
        "Markdown資料から学習プランを作成し、各ステップの練習を案内します。アドレスを空にするとクイック提案モデルを共用します。",
        "使用 Markdown 资料设计学习路径，并指导每一步练习。地址留空时会共用快速建议模型。",
        "使用 Markdown 資料設計學習路徑，並指導每一步練習。網址留空時會共用快速建議模型。",
        "Designs the path from Markdown material and guides each step. Leave the URL empty to reuse the quick suggestion model."
    )
    val coachReasoning = pick("추론 강도", "推論の強さ", "推理强度", "推理強度", "Reasoning effort")
    val coachReasoningNote = pick(
        "추론을 지원하는 모델에서만 적용됩니다.",
        "推論対応モデルでのみ適用されます。",
        "仅适用于支持推理的模型。",
        "僅適用於支援推理的模型。",
        "Only applies to models that support reasoning."
    )
    val reasoningLow = pick("낮음", "低", "低", "低", "Low")
    val reasoningMedium = pick("중간", "中", "中", "中", "Medium")
    val reasoningHigh = pick("높음", "高", "高", "高", "High")
    val coachNotConfigured = pick(
        "경로·코치 모델이 설정되지 않았어요. 설정 탭에서 주소와 모델을 저장해 주세요.",
        "学習プラン・コーチモデルが設定されていません。設定タブでアドレスとモデルを保存してください。",
        "尚未配置路径与教练模型。请在设置中保存地址和模型。",
        "尚未設定路徑與教練模型。請在設定中儲存網址和模型。",
        "The path/coach model isn't configured. Save its URL and model in Settings."
    )
    val testStepTitle = pick("연습 단계", "練習ステップ", "练习步骤", "練習步驟", "Practice step")
    val testStepInstruction = pick(
        "작은 연습을 하나 골라 해보세요.",
        "小さな練習を一つ選んでみましょう。",
        "选一个小练习试试吧。",
        "選一個小練習試試看。",
        "Pick one small practice."
    )
    val testStepCriterion = pick(
        "스스로 결과를 확인했는가",
        "自分の結果を確認したか",
        "是否自行确认了结果",
        "是否自行確認了結果",
        "I checked the result myself"
    )

    // 상태/결과
    val outcomeDoneShort = pick("완전히 함", "全部できた", "全部完成", "全部完成", "Completed")
    val outcomePartialShort = pick("조금 함", "少しできた", "完成一部分", "完成一部分", "Partially done")
    val outcomeNoneShort = pick("못함", "できなかった", "未完成", "未完成", "Not done")
    val outcomeLaterShort = pick("다음에", "あとで", "以后", "以後", "Later")
    val statusActive = pick("진행", "進行中", "进行中", "進行中", "Active")
    val statusPaused = pick("중단", "停止", "已暂停", "已暫停", "Paused")
    val statusCompleted = pick("완료", "完了", "已完成", "已完成", "Completed")
    val statusArchived = pick("보관됨", "アーカイブ", "已归档", "已封存", "Archived")
    val archiveTitle = pick("보관함", "アーカイブ", "归档", "封存", "Archive")
    val archiveSubtitle = pick(
        "보관한 목표와 기록입니다. 복원하면 다시 목록에 나타납니다.",
        "アーカイブした目標と記録です。復元すると一覧に戻ります。",
        "已归档的目标和记录。恢复后会重新出现在列表中。",
        "已封存的目標和紀錄。還原後會重新出現在清單中。",
        "Archived goals and records. Restore them to show them in the lists again."
    )
    val archiveGoals = pick("보관한 목표", "アーカイブした目標", "已归档的目标", "已封存的目標", "Archived goals")
    val archiveRecords = pick("보관한 기록", "アーカイブした記録", "已归档的记录", "已封存的紀錄", "Archived records")
    val archiveAction = pick("보관", "アーカイブ", "归档", "封存", "Archive")
    val unarchiveAction = pick("복원", "復元", "恢复", "還原", "Restore")
    val openArchive = pick("보관함 보기", "アーカイブを見る", "查看归档", "查看封存", "Open archive")
    val archiveEmpty = pick(
        "보관한 항목이 없습니다.",
        "アーカイブした項目はありません。",
        "没有已归档的项目。",
        "沒有已封存的項目。",
        "Nothing is archived."
    )

    // 제안/점검
    val suggestionTitle = pick("제안", "提案", "建议", "建議", "Suggestions")
    val suggestionHeading = pick(
        "지금 할 수 있는 걸 골라볼까요?",
        "今できることを選んでみましょう。",
        "来选一件现在能做的事吧。",
        "來選一件現在能做的事吧。",
        "Pick something you can do now."
    )
    val suggestionEmpty = pick(
        "AI 제안을 아직 불러오지 못했어요.",
        "AIの提案をまだ読み込めませんでした。",
        "暂时无法加载 AI 建议。",
        "暫時無法載入 AI 建議。",
        "Couldn't load AI suggestions yet."
    )
    val suggestionLoading = pick(
        "제안을 만드는 중…",
        "提案を作成中…",
        "正在生成建议…",
        "正在產生建議…",
        "Creating suggestions…"
    )
    val clarificationHint = pick(
        "조금 더 구체적으로 알려주세요.",
        "もう少し具体的に教えてください。",
        "请再说得具体一点。",
        "請再說得具體一點。",
        "Tell me a little more."
    )
    val skipClarification = pick("건너뛰기", "スキップ", "跳过", "略過", "Skip")
    val otherSuggestions = pick("다른 제안 보기", "別の提案を見る", "查看其他建议", "查看其他建議", "See other suggestions")
    val customActionLabel = pick("직접 입력", "直接入力", "手动输入", "手動輸入", "Enter your own")
    val customActionHint = pick(
        "지금 할 행동을 직접 적어 보세요",
        "今やる行動を直接入力してください",
        "直接写下你现在要做的事",
        "直接寫下你現在要做的事",
        "Type what you'll do now"
    )
    val confirmSuggestion = pick("이걸로 할게요", "これにします", "就选这个", "就選這個", "I'll do this")
    val checkTitle = pick("점검", "振り返り", "打卡", "打卡", "Check-in")
    val smallAction = pick("작은 행동", "小さな行動", "小行动", "小行動", "Small action")
    val checkHeading = pick("해보셨나요?", "やってみましたか？", "做过了吗？", "做過了嗎？", "Did you try it?")
    val checkHint = pick(
        "정확한 횟수보다 시도 자체가 중요해요.",
        "正確な回数より、試したこと自体が大切です。",
        "重要的不是准确次数，而是尝试本身。",
        "重要的不是準確次數，而是嘗試本身。",
        "Trying matters more than the exact count."
    )
    val outcomeDoneAction = pick("완전히 했어요", "全部できました", "全部完成了", "全部完成了", "I completed it")
    val outcomePartialAction = pick("조금 했어요", "少しできました", "完成了一部分", "完成了一部分", "I did part of it")
    val outcomeNoneAction = pick("못했어요", "できませんでした", "没有完成", "沒有完成", "I didn't do it")
    val outcomeLaterAction = pick("다음에 할게요", "あとでやります", "以后再做", "之後再做", "I'll do it later")
    val recorded = pick("기록했어요", "記録しました", "已记录", "已記錄", "Recorded")
    val localOnlyNote = pick(
        "기록은 이 기기에만 저장됩니다.",
        "記録はこの端末にのみ保存されます。",
        "记录只保存在本设备上。",
        "紀錄只儲存在本裝置上。",
        "Records are saved only on this device."
    )
    val stop = pick("그만하기", "終了する", "停止", "停止", "Stop")
    val nextSuggestion = pick("다음 제안", "次の提案", "下一个建议", "下一個建議", "Next suggestion")

    // 설정
    val settingsTitle = pick("설정", "設定", "设置", "設定", "Settings")
    val languageSection = pick("언어", "言語", "语言", "語言", "Language")
    val languageSystem = pick("시스템 기본", "システムに合わせる", "跟随系统", "跟隨系統", "System default")
    val languageKorean = "한국어"
    val languageJapanese = "日本語"
    val languageChineseSimplified = "简体中文"
    val languageChineseTraditional = "正體中文"
    val languageEnglish = "English"
    val languagePromptName = pick(
        "Korean",
        "Japanese",
        "Simplified Chinese",
        "Traditional Chinese",
        "English"
    )
    val modelConnection = pick("모델 연결", "モデル接続", "模型连接", "模型連線", "Model connection")
    val remotePrivacy = pick(
        "원격 모드에서는 목표·기억·학습 자료 내용과 단계 정보가 설정한 서버로 전송됩니다.",
        "リモートモードでは、目標・記憶・学習資料の内容とステップ情報が設定したサーバーに送信されます。",
        "在远程模式下，目标、记忆、学习资料内容和步骤信息会发送到所设置的服务器。",
        "在遠端模式下，目標、記憶、學習資料內容與步驟資訊會傳送到所設定的伺服器。",
        "In remote mode, goals, memories, material content, and step details are sent to your server."
    )
    val desktopKeyStorageNote = pick(
        "데스크탑은 API 키를 로컬 설정에 저장합니다. 공용 PC에서는 사용 후 삭제하세요.",
        "デスクトップではAPIキーをローカル設定に保存します。共用PCでは使用後に削除してください。",
        "桌面版会将 API 密钥保存在本地设置中。在公用电脑上使用后请删除。",
        "桌面版會將 API 金鑰儲存在本機設定中。在公用電腦上使用後請刪除。",
        "The desktop app stores API keys in local settings. Delete them after use on a shared PC."
    )
    val apiUrlLabel = pick("API 주소", "APIアドレス", "API 地址", "API 網址", "API URL")
    val modelIdLabel = pick("모델 ID", "モデルID", "模型 ID", "模型 ID", "Model ID")
    val apiKeyLabel = pick("API 키", "APIキー", "API 密钥", "API 金鑰", "API key")
    val apiKeySavedHint = pick(
        "저장된 키 사용 중 · 새 키 입력 시 교체",
        "保存済みキーを使用中 · 新しいキーを入力すると置き換え",
        "正在使用已保存的密钥 · 输入新密钥即可替换",
        "正在使用已儲存的金鑰 · 輸入新金鑰即可取代",
        "Using saved key · enter a new one to replace"
    )
    val apiKeyInputHint = pick("API 키 입력", "APIキーを入力", "输入 API 密钥", "輸入 API 金鑰", "Enter API key")
    val savedKeyLabel = pick("저장된 키", "保存済みキー", "已保存的密钥", "已儲存的金鑰", "Saved key")
    val useDefaultProfile = pick("기본값 사용", "既定値を使用", "使用默认值", "使用預設值", "Use defaults")
    val removeSavedKey =
        pick("저장된 키 삭제", "保存済みキーを削除", "删除已保存的密钥", "刪除已儲存的金鑰", "Delete saved key")
    val saved = pick("저장됨", "保存済み", "已保存", "已儲存", "Saved")
    val saveConnection = pick("연결 설정 저장", "接続設定を保存", "保存连接设置", "儲存連線設定", "Save connection")
    val testConnection = pick("연결 시험", "接続テスト", "测试连接", "測試連線", "Test connection")
    val testingConnection = pick("연결 확인 중…", "接続を確認中…", "正在检查连接…", "正在檢查連線…", "Testing connection…")
    val dataManagement = pick("데이터 관리", "データ管理", "数据管理", "資料管理", "Data management")
    val syncSection = pick("P2P 동기화", "P2P同期", "P2P 同步", "P2P 同步", "P2P sync")
    val syncDescription = pick(
        "같은 Wi-Fi에서 Windows 앱(서버)과 Android(클라이언트)를 직접 동기화합니다. Android끼리의 동기화는 지원하지 않습니다.",
        "同じWi-FiでWindowsアプリ（サーバー）とAndroid（クライアント）を直接同期します。Android端末同士の同期には対応していません。",
        "在同一 Wi-Fi 下，直接同步 Windows 应用（服务器）与 Android（客户端）。不支持 Android 设备之间的同步。",
        "在同一個 Wi-Fi 下，直接同步 Windows 應用程式（伺服器）與 Android（用戶端）。不支援 Android 裝置之間的同步。",
        "Sync the Windows app (server) and Android (client) directly on the same Wi-Fi. Android-to-Android sync is not supported."
    )
    val syncServerOn = pick("동기화 서버 켜기", "同期サーバーを起動", "启动同步服务器", "啟動同步伺服器", "Start sync server")
    val syncServerOff = pick("동기화 서버 끄기", "同期サーバーを停止", "停止同步服务器", "停止同步伺服器", "Stop sync server")
    val syncPairingCode = pick("페어링 코드", "ペアリングコード", "配对码", "配對碼", "Pairing code")
    val syncFingerprint = pick(
        "인증서 fingerprint",
        "証明書のfingerprint",
        "证书 fingerprint",
        "憑證 fingerprint",
        "Certificate fingerprint"
    )
    val syncNewCode = pick("새 코드 만들기", "新しいコード", "生成新代码", "產生新代碼", "New code")
    val syncPairedDevices = pick("연결된 기기", "接続済み端末", "已配对设备", "已配對裝置", "Paired devices")
    val syncNoDevices = pick(
        "아직 연결된 기기가 없습니다.",
        "接続済みの端末はありません。",
        "还没有已配对的设备。",
        "還沒有已配對的裝置。",
        "No paired devices yet."
    )
    val syncPort = pick("포트", "ポート", "端口", "連接埠", "Port")
    val syncLastSync = pick("마지막 동기화", "最終同期", "上次同步", "上次同步", "Last sync")
    val syncNever = pick("없음", "なし", "无", "無", "Never")
    val syncServerHint = pick(
        "Windows에서 서버를 켜고 Android 설정의 P2P 동기화에서 기기를 검색하세요.",
        "Windowsでサーバーを起動し、Android設定のP2P同期で端末を検索してください。",
        "在 Windows 上启动服务器，然后在 Android 设置的 P2P 同步中搜索设备。",
        "在 Windows 上啟動伺服器，然後在 Android 設定的 P2P 同步中搜尋裝置。",
        "Start the server here, then search for it in P2P sync on Android."
    )
    val syncDiscover = pick("기기 검색", "端末を検索", "搜索设备", "搜尋裝置", "Find devices")
    val syncStopDiscover = pick("검색 중지", "検索を停止", "停止搜索", "停止搜尋", "Stop searching")
    val syncDiscovering = pick("검색 중…", "検索中…", "正在搜索…", "正在搜尋…", "Searching…")
    val syncDiscovered = pick("발견된 기기", "見つかった端末", "发现的设备", "找到的裝置", "Devices found")
    val syncNoServersFound =
        pick("발견된 기기가 없습니다.", "見つかった端末はありません。", "没有发现设备。", "沒有找到裝置。", "No devices found.")
    val syncPair = pick("페어링", "ペアリング", "配对", "配對", "Pair")
    val syncPairing = pick("페어링 중…", "ペアリング中…", "正在配对…", "正在配對…", "Pairing…")
    val syncPairCodeHint = pick(
        "Windows 화면의 6자리 코드를 입력하세요.",
        "Windows画面の6桁のコードを入力してください。",
        "请输入 Windows 屏幕上显示的 6 位代码。",
        "請輸入 Windows 畫面上顯示的 6 位數代碼。",
        "Enter the 6-digit code shown on Windows."
    )
    val syncPairedTo = pick("연결됨", "接続済み", "已配对", "已配對", "Paired")
    val syncForget = pick("연결 해제", "接続解除", "解除配对", "解除配對", "Forget device")
    val syncNow = pick("지금 동기화", "今すぐ同期", "立即同步", "立即同步", "Sync now")
    val syncSyncing = pick("동기화 중…", "同期中…", "正在同步…", "正在同步…", "Syncing…")
    val syncCompleted = pick("동기화 완료", "同期完了", "同步完成", "同步完成", "Sync complete")
    val syncNotPaired = pick(
        "연결된 서버가 없습니다.",
        "接続されたサーバーがありません。",
        "尚未配对服务器。",
        "尚未配對伺服器。",
        "No server paired."
    )
    val syncManualAddress =
        pick("IP 주소 직접 입력", "IPアドレスを直接入力", "手动输入 IP 地址", "手動輸入 IP 位址", "Enter IP address")
    val syncManualHint = pick(
        "예: 192.168.0.10:45871",
        "例: 192.168.0.10:45871",
        "例如：192.168.0.10:45871",
        "例如：192.168.0.10:45871",
        "e.g. 192.168.0.10:45871"
    )
    val syncQrScan = pick("QR 코드 스캔", "QRコードをスキャン", "扫描二维码", "掃描 QR Code", "Scan QR code")
    val syncQrTitle = pick(
        "Windows 화면의 QR을 스캔하세요",
        "Windows画面のQRコードを読み取ってください",
        "扫描 Windows 屏幕上的二维码",
        "掃描 Windows 畫面上的 QR Code",
        "Scan the QR code displayed on Windows"
    )
    val syncQrDesktopTitle = pick(
        "Android에서 QR을 스캔하세요",
        "AndroidからQRコードを読み取ってください",
        "请用 Android 扫描二维码",
        "請用 Android 掃描 QR Code",
        "Scan this QR code with Android"
    )
    val syncQrHint = pick(
        "Windows 설정의 P2P 동기화에 있는 QR을 비추면 주소·인증서·페어링 코드가 자동으로 채워집니다.",
        "Windowsの設定にあるP2P同期のQRコードを読み取ると、アドレス・証明書・ペアリングコードが自動入力されます。",
        "扫描 Windows 设置中 P2P 同步页面的二维码，地址、证书和配对码会自动填入。",
        "掃描 Windows 設定中 P2P 同步頁面的 QR Code，網址、憑證與配對碼會自動填入。",
        "Point at the QR in P2P sync on Windows to fill the address, certificate and pairing code automatically."
    )
    val syncQrCameraDenied = pick(
        "카메라 권한이 필요합니다.",
        "カメラ権限が必要です。",
        "需要相机权限。",
        "需要相機權限。",
        "Camera permission is required."
    )
    val syncQrGrantCamera = pick("카메라 권한 허용", "カメラ権限を許可", "允许使用相机", "允許使用相機", "Allow camera")
    val syncQrInvalid = pick(
        "이 앱의 페어링 QR이 아닙니다.",
        "このアプリのペアリングQRではありません。",
        "这不是本应用的配对二维码。",
        "這不是本應用程式的配對 QR Code。",
        "That is not a pairing QR from this app."
    )
    val syncQrNoNetwork = pick(
        "네트워크 주소를 찾지 못했습니다. 수동 연결을 사용하세요.",
        "ネットワークアドレスが見つかりません。手動接続を使用してください。",
        "未找到网络地址。请使用手动配对。",
        "找不到網路位址。請使用手動配對。",
        "No network address found. Use manual pairing."
    )
    val syncInvalidAddress = pick(
        "주소 형식이 올바르지 않습니다.",
        "アドレスの形式が正しくありません。",
        "地址格式不正确。",
        "位址格式不正確。",
        "Invalid address format."
    )
    val syncPermissionRequired = pick(
        "Android 17에서는 로컬 네트워크 권한이 필요할 수 있습니다.",
        "Android 17ではローカルネットワーク権限が必要な場合があります。",
        "Android 17 可能需要本地网络权限。",
        "Android 17 可能需要本機網路權限。",
        "Android 17 may require local network permission."
    )
    val syncFingerprintNote = pick(
        "Android에 표시된 fingerprint와 같아야 합니다.",
        "Androidに表示されたfingerprintと一致する必要があります。",
        "必须与 Android 上显示的 fingerprint 一致。",
        "必須與 Android 上顯示的 fingerprint 一致。",
        "It must match the fingerprint shown on Android."
    )
    val syncFingerprintRequired = pick(
        "인증서 fingerprint가 필요합니다. Windows 설정 화면에 표시된 값을 입력하세요.",
        "証明書のfingerprintが必要です。Windowsの設定画面に表示された値を入力してください。",
        "需要证书 fingerprint。请输入 Windows 设置画面中显示的值。",
        "需要憑證 fingerprint。請輸入 Windows 設定畫面中顯示的值。",
        "Certificate fingerprint is required. Enter the value shown in Windows settings."
    )
    val syncNoFingerprint = pick(
        "fingerprint가 없어 이 기기는 선택할 수 없습니다.",
        "fingerprintがないため、この端末は選択できません。",
        "没有 fingerprint，无法选择此设备。",
        "沒有 fingerprint，無法選擇此裝置。",
        "This device cannot be selected without a fingerprint."
    )
    val syncSecureStorageRequired = pick(
        "페어링 정보를 안전하게 저장할 수 없습니다. 앱을 재설치하거나 기기 잠금을 확인해 주세요.",
        "ペアリング情報を安全に保存できません。アプリを再インストールするか、端末のロックを確認してください。",
        "无法安全保存配对信息。请重新安装应用或检查设备锁设置。",
        "無法安全儲存配對資訊。請重新安裝應用程式或檢查裝置鎖定設定。",
        "Cannot store pairing data securely. Reinstall the app or check device lock settings."
    )
    val syncRecordTooLarge = pick(
        "한 항목이 너무 커서 동기화할 수 없습니다. 학습 자료를 나눠 가져오세요.",
        "1件が大きすぎて同期できません。学習資料を分割して取り込んでください。",
        "某个项目太大，无法同步。请拆分学习资料后再导入。",
        "某個項目太大，無法同步。請分割學習資料後再匯入。",
        "One item is too large to sync. Import the material in smaller parts."
    )
    val syncTooManyChunks = pick(
        "동기화할 변경이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
        "同期する変更が多すぎます。しばらくしてから再試行してください。",
        "要同步的变更太多。请稍后再试。",
        "要同步的變更太多。請稍後再試。",
        "Too many changes to sync at once. Try again in a moment."
    )
    val backupDescription = pick(
        "전체 데이터와 설정(API 키 포함)을 암호화 ZIP으로 내보내거나 교체합니다. Windows 앱과 같은 백업 파일을 주고받을 수 있습니다.",
        "すべてのデータと設定（APIキーを含む）を暗号化ZIPで書き出すか置き換えます。Windowsアプリと同じバックアップファイルを使えます。",
        "将全部数据和设置（包括 API 密钥）导出为加密 ZIP 或进行替换。可与 Windows 应用互相使用同一备份文件。",
        "將所有資料與設定（包括 API 金鑰）匯出為加密 ZIP 或進行取代。可與 Windows 應用程式共用相同備份檔案。",
        "Export or replace all data and settings (including API keys) as an encrypted ZIP. The same backup file works with the Windows app."
    )
    val backupExport = pick("백업 내보내기", "バックアップを書き出す", "导出备份", "匯出備份", "Export backup")
    val backupImportReplace = pick(
        "백업 불러오기 (교체)",
        "バックアップを読み込む（置き換え）",
        "导入备份（替换）",
        "匯入備份（取代）",
        "Import backup (replace)"
    )
    val clearAllRecords = pick("모든 데이터 지우기", "すべてのデータを消去", "清除所有数据", "清除所有資料", "Clear all data")
    val backupPasswordTitle = pick("백업 암호", "バックアップパスワード", "备份密码", "備份密碼", "Backup password")
    val backupImportTitle = pick("백업 불러오기", "バックアップを読み込む", "导入备份", "匯入備份", "Import backup")
    val backupExportExplanation = pick(
        "8자 이상의 암호로 ZIP 안 데이터를 암호화합니다.",
        "8文字以上のパスワードでZIP内のデータを暗号化します。",
        "使用至少 8 个字符的密码加密 ZIP 内的数据。",
        "使用至少 8 個字元的密碼加密 ZIP 內的資料。",
        "Encrypt the ZIP data with a password of at least 8 characters."
    )
    val backupImportExplanation = pick(
        "이 앱에서 내보낸 암호화 백업의 암호를 입력하세요. 평문 백업은 지원하지 않습니다.",
        "このアプリで書き出した暗号化バックアップのパスワードを入力してください。平文バックアップは使えません。",
        "请输入本应用导出的加密备份密码。不支持未加密的备份。",
        "請輸入本應用程式匯出的加密備份密碼。不支援未加密的備份。",
        "Enter the password for an encrypted backup exported by this app. Plain backups are not supported."
    )
    val export = pick("내보내기", "書き出す", "导出", "匯出", "Export")
    val import = pick("불러오기", "読み込む", "导入", "匯入", "Import")

    // 라이선스
    val licenseSection = pick("정보", "情報", "信息", "資訊", "About")
    val licenseInfo = pick("라이선스 정보", "ライセンス情報", "许可证信息", "授權資訊", "License information")

    // 설정 메시지
    val invalidHttpsUrl = pick(
        "API 주소는 HTTPS 형식이어야 합니다",
        "APIアドレスはHTTPS形式である必要があります",
        "API 地址必须使用 HTTPS",
        "API 網址必須使用 HTTPS",
        "API URL must use HTTPS"
    )
    val secureStoreUnavailable = pick(
        "보안 저장소를 사용할 수 없어 키를 저장하지 못했습니다",
        "安全な保存領域が使用できないため、キーを保存できませんでした",
        "无法使用安全存储，密钥未能保存",
        "無法使用安全儲存空間，金鑰未能儲存",
        "Couldn't save the key because secure storage is unavailable"
    )
    val keyDeleted = pick(
        "저장된 키를 삭제했습니다",
        "保存済みキーを削除しました",
        "已删除保存的密钥",
        "已刪除儲存的金鑰",
        "Deleted the saved key"
    )
    val connectionFailed = pick("연결 실패", "接続失敗", "连接失败", "連線失敗", "Connection failed")
    val backupFailed = pick("백업 실패", "バックアップ失敗", "备份失败", "備份失敗", "Backup failed")
    val backupPasswordMin = pick(
        "백업 암호는 8자 이상이어야 합니다",
        "バックアップパスワードは8文字以上である必要があります",
        "备份密码必须至少为 8 个字符",
        "備份密碼必須至少為 8 個字元",
        "Backup password must be at least 8 characters"
    )
    val backupExported = pick(
        "암호화된 백업을 저장했어요",
        "暗号化バックアップを保存しました",
        "已保存加密备份",
        "已儲存加密備份",
        "Saved encrypted backup"
    )
    val backupImported =
        pick("백업을 불러왔어요", "バックアップを読み込みました", "已导入备份", "已匯入備份", "Imported backup")

    fun connectionSuccess(count: Int) = pick(
        "연결됨 · 제안 ${count}개 수신",
        "接続済み · 提案${count}件を受信",
        "已连接 · 收到 $count 条建议",
        "已連線 · 收到 $count 則建議",
        if (count == 1) "Connected · received 1 suggestion" else "Connected · received $count suggestions"
    )

    val connectionClarify = pick(
        "연결됨 · 확인 질문 수신",
        "接続済み · 確認質問を受信",
        "已连接 · 收到确认问题",
        "已連線 · 收到確認問題",
        "Connected · clarification question received"
    )

    fun connectionWithDuration(message: String, elapsedMs: Long): String {
        val seconds = String.format(Locale.US, "%.2f", elapsedMs / 1000.0)
        return pick(
            "$message · ${seconds}초",
            "$message · ${seconds}秒",
            "$message · ${seconds} 秒",
            "$message · ${seconds} 秒",
            "$message · ${seconds}s"
        )
    }

    // 원격/오류
    val remoteDisabled = pick(
        "원격 연결이 꺼져 있거나 API 주소가 비어 있습니다",
        "リモート接続がオフか、APIアドレスが空です",
        "远程连接已关闭或 API 地址为空",
        "遠端連線已關閉或 API 網址為空",
        "Remote connection is off or the API URL is empty"
    )
    val testGoalTitle = pick("테스트", "テスト", "测试", "測試", "Test")
    fun serverResponse(code: Int) =
        pick("서버 응답 $code", "サーバー応答 $code", "服务器响应 $code", "伺服器回應 $code", "Server response $code")

    val errorUnauthorized = pick(
        "API 키가 올바르지 않습니다 (401)",
        "APIキーが正しくありません (401)",
        "API 密钥无效（401）",
        "API 金鑰無效（401）",
        "Invalid API key (401)"
    )
    val errorForbidden = pick(
        "API 키 권한 또는 모델 액세스를 확인하세요 (403)",
        "APIキーの権限またはモデルアクセスを確認してください (403)",
        "请检查 API 密钥权限或模型访问权限（403）",
        "請檢查 API 金鑰權限或模型存取權限（403）",
        "Check API key permissions or model access (403)"
    )
    val errorNotFound = pick(
        "API 주소 또는 모델 ID를 확인하세요 (404)",
        "APIアドレスまたはモデルIDを確認してください (404)",
        "请检查 API 地址或模型 ID（404）",
        "請檢查 API 網址或模型 ID（404）",
        "Check the API URL or model ID (404)"
    )
    val errorRateLimited = pick(
        "요청 한도를 초과했습니다 (429)",
        "リクエスト上限を超えました (429)",
        "已超出请求限额（429）",
        "已超出請求上限（429）",
        "Rate limit exceeded (429)"
    )
    val responseTooLarge = pick("응답이 너무 큽니다", "応答が大きすぎます", "响应过大", "回應過大", "Response is too large")
    val serverUnreachable = pick(
        "서버 주소를 확인할 수 없습니다",
        "サーバーアドレスを確認できません",
        "无法解析服务器地址",
        "無法解析伺服器位址",
        "Couldn't resolve the server address"
    )
    val serverTimeout = pick(
        "서버 응답이 지연됩니다",
        "サーバーの応答が遅れています",
        "服务器响应超时",
        "伺服器回應逾時",
        "Server response timed out"
    )
    val sslFailed = pick(
        "보안 연결(HTTPS)에 실패했습니다",
        "セキュア接続（HTTPS）に失敗しました",
        "安全连接（HTTPS）失败",
        "安全連線（HTTPS）失敗",
        "Secure connection (HTTPS) failed"
    )
    val sessionMissing =
        pick("세션이 없습니다", "セッションがありません", "没有进行中的练习", "沒有進行中的練習", "No active session")
    val goalNotFound = pick("목표를 찾을 수 없습니다", "目標が見つかりません", "找不到目标", "找不到目標", "Goal not found")
    val goalMissing = pick("목표 없음", "目標なし", "无目标", "無目標", "No goal")
    val remoteNotConfigured = pick(
        "AI 연결이 설정되지 않았어요. 설정 탭에서 모델 연결을 먼저 저장해 주세요.",
        "AI接続が設定されていません。設定タブでモデル接続を先に保存してください。",
        "尚未配置 AI 连接。请先在设置中保存模型连接。",
        "尚未設定 AI 連線。請先在設定中儲存模型連線。",
        "AI isn't configured. Save a model connection in Settings first."
    )
    val serverConnectionFailed = pick(
        "서버 연결에 실패했어요",
        "サーバー接続に失敗しました",
        "无法连接到服务器",
        "無法連線到伺服器",
        "Couldn't connect to the server"
    )
    val noSuggestions =
        pick("제안을 받지 못했어요", "提案を受け取れませんでした", "未收到建议", "未收到建議", "No suggestions received")

    fun retryLater(message: String): String {
        val base = message.trim().trimEnd('.', '。')
        if (base.isBlank()) return pick(
            "잠시 후 다시 시도해 주세요.",
            "しばらくしてからもう一度お試しください。",
            "请稍后再试。",
            "請稍後再試。",
            "Please try again in a moment."
        )
        return pick(
            "${base}. 잠시 후 다시 시도해 주세요.",
            "${base}。しばらくしてからもう一度お試しください。",
            "${base}，请稍后再试。",
            "${base}，請稍後再試。",
            "${base}. Please try again in a moment."
        )
    }

    fun outcomeAttempted(action: String): String {
        val clean = action.trim().trimEnd('.', '。')
        return pick(
            "이번에는 이렇게 해봤어요: $clean. 작은 한 걸음이 쌓였어요.",
            "今回は次のことに取り組みました: $clean。小さな一歩になりました。",
            "这次尝试了：$clean。又迈出了一小步。",
            "這次嘗試了：$clean。又邁出了一小步。",
            "You tried: $clean. That's one small step forward."
        )
    }

    val outcomeNotAttempted = pick(
        "이번에는 실행하지 않았어요. 부담이 적은 단계부터 다시 시작할 수 있어요.",
        "今回は実行しませんでした。負担の少ない段階からやり直せます。",
        "这次没有执行。可以从负担更轻的步骤重新开始。",
        "這次沒有執行。可以從負擔更輕的步驟重新開始。",
        "You didn't do it this time. You can restart from a lighter step."
    )
    val difficultyLowerNeeded = pick(
        "부담을 더 낮춰야 함",
        "負担をもっと下げる必要あり",
        "需要进一步降低难度",
        "需要進一步降低難度",
        "Needs lower difficulty"
    )

    fun memorySummary(title: String, outcome: String) = pick(
        "${title} 최근 결과: $outcome",
        "${title} 最近の結果: $outcome",
        "$title 最近结果：$outcome",
        "$title 最近結果：$outcome",
        "Latest result for $title: $outcome"
    )

    val fileReadFailed =
        pick("파일을 읽지 못했습니다", "ファイルを読み込めませんでした", "无法读取文件", "無法讀取檔案", "Couldn't read the file")

    // 백업/입력 검증
    fun backupPasswordTooShort(min: Int) = pick(
        "백업 암호는 ${min}자 이상이어야 합니다",
        "バックアップパスワードは${min}文字以上である必要があります",
        "备份密码必须至少为 $min 个字符",
        "備份密碼必須至少為 $min 個字元",
        "Backup password must be at least $min characters"
    )

    val cannotOpenFile =
        pick("파일을 열 수 없습니다", "ファイルを開けません", "无法打开文件", "無法開啟檔案", "Couldn't open the file")
    val unsupportedBackupVersion = pick(
        "지원하지 않는 백업 버전",
        "サポートされていないバックアップバージョン",
        "不支持的备份版本",
        "不支援的備份版本",
        "Unsupported backup version"
    )
    val tooManyZipEntries = pick(
        "압축 파일 항목이 너무 많습니다",
        "圧縮ファイルの項目が多すぎます",
        "压缩文件中的项目过多",
        "壓縮檔中的項目過多",
        "Too many entries in the archive"
    )

    fun unknownBackupEntry(name: String) = pick(
        "알 수 없는 백업 항목: $name",
        "不明なバックアップ項目: $name",
        "未知备份项目：$name",
        "未知的備份項目：$name",
        "Unknown backup entry: $name"
    )

    val backupPasswordRequired = pick(
        "암호화된 백업에는 암호가 필요합니다",
        "暗号化バックアップにはパスワードが必要です",
        "加密备份需要密码",
        "加密備份需要密碼",
        "Encrypted backups require a password"
    )
    val missingEncryptedPayload = pick(
        "암호화 데이터(payload.bin)가 없습니다",
        "暗号化データ（payload.bin）がありません",
        "缺少加密数据（payload.bin）",
        "缺少加密資料（payload.bin）",
        "Encrypted data (payload.bin) is missing"
    )
    val backupDataNotFound = pick(
        "백업 데이터를 찾을 수 없습니다",
        "バックアップデータが見つかりません",
        "找不到备份数据",
        "找不到備份資料",
        "Backup data not found"
    )
    val invalidBackupEntry =
        pick("잘못된 백업 항목", "無効なバックアップ項目", "无效的备份项目", "無效的備份項目", "Invalid backup entry")
    val invalidBackupPath =
        pick("잘못된 백업 경로", "無効なバックアップパス", "无效的备份路径", "無效的備份路徑", "Invalid backup path")

    fun disallowedEntry(name: String) = pick(
        "허용되지 않는 항목: $name",
        "許可されていない項目: $name",
        "不允许的项目：$name",
        "不允許的項目：$name",
        "Entry not allowed: $name"
    )

    val backupPasswordMissing = pick(
        "백업 암호가 필요합니다",
        "バックアップパスワードが必要です",
        "需要备份密码",
        "需要備份密碼",
        "Backup password required"
    )
    val corruptedBackup = pick(
        "손상된 백업 파일입니다",
        "破損したバックアップファイルです",
        "备份文件已损坏",
        "備份檔案已損毀",
        "The backup file is corrupted"
    )

    fun inputTooLarge(maxMb: Int) = pick(
        "입력이 너무 큽니다 (최대 ${maxMb}MB)",
        "入力が大きすぎます（最大${maxMb}MB）",
        "输入过大（最大 ${maxMb}MB）",
        "輸入過大（最大 ${maxMb}MB）",
        "Input is too large (max ${maxMb}MB)"
    )

    // 학습 자료 진행
    fun progressBeforeStart(title: String) = pick(
        "$title · 시작 전",
        "$title · 開始前",
        "$title · 未开始",
        "$title · 未開始",
        "$title · Not started"
    )

    val promptCurrent = pick("현재: ", "現在: ", "当前：", "目前：", "Current: ")
    val promptNext = pick("\n\n다음: ", "\n\n次: ", "\n\n下一步：", "\n\n下一步：", "\n\nNext: ")

    // 빠른 시작 / 위젯 / 바로가기 / 트레이 / 미니 바
    val quickStartTitle = pick("지금 바로 할 수 있는 일", "今すぐできること", "现在就能做的事", "現在就能做的事", "Ready to start")
    val quickStartButton = pick("이것만 하고 시작하기", "これだけやって始める", "就从这件小事开始", "就從這件小事開始", "Start with just this")
    val quickStartFallback = pick("가볍게 1분만 시작하기", "軽く1分だけ始める", "轻松开始 1 分钟", "輕鬆開始 1 分鐘", "Try for just 1 minute")
    val pinGoalShortcut = pick("홈 화면에 바로가기 고정", "ホーム画面にショートカットを固定", "固定快捷方式到主屏幕", "固定捷徑到主畫面", "Pin shortcut to home")
    val widgetEmpty = pick(
        "목표를 추가하면 다음 행동이 여기에 표시됩니다.",
        "目標を追加すると次の行動がここに表示されます。",
        "添加目标后，下一步行动会显示在这里。",
        "新增目標後，下一步行動會顯示在這裡。",
        "Add a goal to see your next small action here."
    )
    val widgetOpenApp = pick("탭해서 바로 시작하기", "タップしてすぐ始める", "点击立即开始", "點擊立即開始", "Tap to start now")
    val quickTileSection = pick("빠른 설정 타일", "クイック設定タイル", "快速设置磁贴", "快速設定圖塊", "Quick Settings Tile")
    val quickTileToggle = pick("알림바 빠른 시작 타일 사용", "通知バーのクイック開始タイルを使う", "启用通知栏快速开始磁贴", "啟用通知列快速開始圖塊", "Enable Quick Settings tile")
    val quickTileAdd = pick("알림바에 타일 추가", "通知バーにタイルを追加", "添加磁贴到通知栏", "新增圖塊到通知列", "Add tile to Quick Settings")
    val quickTileOpenFull = pick("전체 화면으로 열기", "全画面で開く", "在完整界面中打开", "在完整畫面中開啟", "Open full app")
    val desktopTrayOpen = pick("창 열기", "ウィンドウを開く", "打开窗口", "開啟視窗", "Open window")
    val desktopMiniBarMode = pick("미니 바 모드", "ミニバーモード", "迷你栏模式", "迷你列模式", "Mini-bar mode")
    val desktopTrayExit = pick("종료", "終了", "退出", "結束", "Exit")
    val desktopMiniNext = pick("다음 할 일: ", "次の行動: ", "下一步: ", "下一步: ", "Next: ")
    val desktopMiniStart = pick("시작", "開始", "开始", "開始", "Start")

    // 동기화/AI 상태 메시지
    val aiResponseParseFailed = pick(
        "AI 응답 형식을 해석하지 못했습니다",
        "AIの応答形式を解釈できませんでした",
        "无法解析 AI 响应格式",
        "無法解析 AI 回應格式",
        "Couldn't parse the AI response format"
    )
    fun syncDiscoveryFailedCode(code: Int) = pick(
        "기기 검색을 시작하지 못했습니다 ($code)",
        "端末の検索を開始できませんでした ($code)",
        "无法开始搜索设备 ($code)",
        "無法開始搜尋裝置 ($code)",
        "Couldn't start device discovery ($code)"
    )
    fun syncDiscoveryFailedMessage(msg: String?) = pick(
        "기기 검색을 시작하지 못했습니다: ${msg.orEmpty()}",
        "端末の検索を開始できませんでした: ${msg.orEmpty()}",
        "无法开始搜索设备：${msg.orEmpty()}",
        "無法開始搜尋裝置：${msg.orEmpty()}",
        "Couldn't start device discovery: ${msg.orEmpty()}"
    )
    fun syncCertPrepareFailed(msg: String?) = pick(
        "동기화 인증서를 준비하지 못했습니다: ${msg.orEmpty()}",
        "同期用証明書を準備できませんでした: ${msg.orEmpty()}",
        "无法准备同步证书：${msg.orEmpty()}",
        "無法準備同步憑證：${msg.orEmpty()}",
        "Couldn't prepare sync certificate: ${msg.orEmpty()}"
    )
    fun syncServerStartFailed(msg: String?) = pick(
        "동기화 서버를 시작하지 못했습니다: ${msg.orEmpty()}",
        "同期サーバーを起動できませんでした: ${msg.orEmpty()}",
        "无法启动同步服务器：${msg.orEmpty()}",
        "無法啟動同步伺服器：${msg.orEmpty()}",
        "Couldn't start sync server: ${msg.orEmpty()}"
    )
    val syncMdnsAdvertiseFailed = pick(
        "mDNS 광고에 실패했습니다. Android에서 IP로 직접 연결할 수 있습니다.",
        "mDNS公開に失敗しました。AndroidからIPで直接接続できます。",
        "mDNS 广播失败。可在 Android 上通过 IP 直接连接。",
        "mDNS 廣播失敗。可在 Android 上透過 IP 直接連線。",
        "mDNS advertisement failed. You can connect directly by IP from Android."
    )
    val syncServerStarted = pick(
        "동기화 서버가 켜졌습니다.",
        "同期サーバーを起動しました。",
        "同步服务器已启动。",
        "同步伺服器已啟動。",
        "Sync server started."
    )
    val syncServerStopped = pick(
        "동기화 서버를 껐습니다.",
        "同期サーバーを停止しました。",
        "同步服务器已停止。",
        "同步伺服器已停止。",
        "Sync server stopped."
    )
    val syncPairingCodeReset = pick(
        "페어링 시도 초과로 코드가 재설정되었습니다",
        "ペアリング試行回数超過のためコードが再設定されました",
        "配对尝试次数过多，代码已重置",
        "配對嘗試次數過多，代碼已重設",
        "Pairing code reset due to too many attempts"
    )
    fun syncPairingCodeMismatchCount(attempts: Int, max: Int) = pick(
        "페어링 실패: 코드 불일치 ($attempts/$max)",
        "ペアリング失敗: コード不一致 ($attempts/$max)",
        "配对失败：代码不匹配 ($attempts/$max)",
        "配對失敗：代碼不符 ($attempts/$max)",
        "Pairing failed: code mismatch ($attempts/$max)"
    )
    val syncPairingCodeMismatch = pick(
        "페어링 코드가 다릅니다",
        "ペアリングコードが一致しません",
        "配对码不正确",
        "配對碼不正確",
        "Pairing code does not match"
    )
    fun syncDeviceCompleted(deviceName: String) = pick(
        "$deviceName 동기화 완료",
        "$deviceName 同期完了",
        "$deviceName 同步完成",
        "$deviceName 同步完成",
        "$deviceName synced"
    )
}

object AppLocale {
    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language

    @Volatile
    private var cached: AppStrings? = null

    val strings: AppStrings
        get() {
            val lang = resolveAppLang(_language.value)
            val existing = cached
            if (existing?.lang == lang) return existing
            return synchronized(this) {
                cached?.takeIf { it.lang == lang } ?: AppStrings(lang).also { cached = it }
            }
        }

    fun init(language: AppLanguage) {
        _language.value = language
    }

    fun set(language: AppLanguage) {
        _language.value = language
    }

    fun stringsFor(language: AppLanguage): AppStrings = AppStrings(resolveAppLang(language))
}

val LocalStrings = staticCompositionLocalOf { AppStrings(AppLang.KO) }
