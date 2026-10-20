package com.ainovel.local.ui.localization

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as MaterialText

enum class AppLanguage(val storedValue: String) {
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en"),
}

object AppLanguageStore {
    private const val PREFERENCES_NAME = "language_preferences"
    private const val LANGUAGE_KEY = "selected_language"

    fun read(context: Context): AppLanguage? {
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
            ?: return null
        return AppLanguage.entries.firstOrNull { it.storedValue == stored }
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language.storedValue)
            .apply()
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SIMPLIFIED_CHINESE }

object AppLanguageRuntime {
    @Volatile
    var current: AppLanguage = AppLanguage.SIMPLIFIED_CHINESE
}

fun localized(text: String, language: AppLanguage = AppLanguageRuntime.current): String {
    if (language == AppLanguage.SIMPLIFIED_CHINESE || text.none(::isHanCharacter)) return text
    exactEnglish[text]?.let { return it }
    return translateDynamicText(text)
}

private fun isHanCharacter(char: Char): Boolean = Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = localized(text, LocalAppLanguage.current),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

private val exactEnglish = mapOf(
    "绀海之约" to "Azure Waters Promise",
    "写作" to "Write",
    "记忆" to "Memory",
    "灵感" to "Ideas",
    "足迹" to "History",
    "设置" to "Settings",
    "语言" to "Language",
    "切换后立即应用，并会记住你的选择。" to "Changes apply instantly and your choice will be remembered.",
    "故事正在生长" to "The story is growing",
    "故事记忆" to "Story Memory",
    "灵感对谈" to "Creative Chat",
    "创作足迹" to "Writing History",
    "返回上一页" to "Back",
    "返回作品列表" to "Back to library",
    "保存" to "Save",
    "取消" to "Cancel",
    "删除" to "Delete",
    "关闭" to "Close",
    "知道了" to "Got it",
    "新建" to "New",
    "编辑" to "Edit",
    "上移" to "Move up",
    "下移" to "Move down",
    "请选择" to "Select",
    "尚未配置模型" to "No model configured",
    "未命名章节" to "Untitled chapter",
    "发送前检查" to "Review before sending",
    "停止生成" to "Stop generating",
    "思考过程" to "Reasoning",
    "展开思考过程" to "Expand reasoning",
    "折叠思考过程" to "Collapse reasoning",
    "生成完成" to "Completed",
    "中途停下" to "Stopped early",
    "正文" to "Chapter text",
    "总结" to "Summary",
    "候选" to "Candidate",
    "世界书" to "World book",
    "预设" to "Preset",
    "助手" to "Assistant",
    "系统消息" to "System message",
    "用户消息" to "User message",
    "模型回复" to "Model response",
    "新消息" to "New message",
    "本次创作要求" to "Writing request",
    "总结指令" to "Summary instructions",
    "总结密度" to "Summary density",
    "助手指令" to "Assistant instructions",
    "正文候选" to "Draft candidates",
    "总结候选" to "Summary candidates",
    "创作助手" to "Writing assistant",
    "模型验证" to "Model validation",
    "已接受正文" to "Accepted chapter text",
    "已接受总结" to "Accepted summary",
    "发送内容" to "Messages",
    "完整请求" to "Full request",
    "（这里还没有内容）" to "(No content yet)",
    "（暂无内容）" to "(No content)",
    "自动保存" to "Auto-saved",
    "章节" to "Chapters",
    "故事顺序" to "Story order",
    "创建下一章" to "Create next chapter",
    "章节回收区" to "Chapter trash",
    "恢复章节" to "Restore chapter",
    "上移章节" to "Move chapter up",
    "下移章节" to "Move chapter down",
    "移入章节回收区" to "Move chapter to trash",
    "移入回收区" to "Move to trash",
    "将章节移入回收区？" to "Move this chapter to trash?",
    "编辑章节标题" to "Edit chapter title",
    "彻底删除章节" to "Permanently delete chapter",
    "彻底删除章节？" to "Permanently delete this chapter?",
    "彻底删除" to "Delete permanently",
    "彻底删除后，该章节的正文与版本将永久移除，无法恢复。" to "Once permanently deleted, this chapter's text and versions will be removed forever and cannot be recovered.",
    "章节已彻底删除" to "Chapter permanently deleted",
    "彻底删除章节失败" to "Could not permanently delete the chapter",
    "只能彻底删除回收区中的章节" to "Only chapters in Trash can be permanently deleted",
    "章节会暂时收进回收区，正文、版本与创作记录都不会丢失。它将不再参与后续生成，依赖它的总结也会失效；恢复章节后，需要重新整理这些总结。" to "The chapter will be kept in Trash. Its text, versions, and writing history will remain intact, but it will no longer be used for generation and dependent summaries will become outdated. Restore it and refresh those summaries when needed.",
    "展开章节列表" to "Expand chapter list",
    "折叠章节列表" to "Collapse chapter list",
    "展开章节信息" to "Expand chapter details",
    "折叠章节信息" to "Collapse chapter details",
    "章节名（可以留空）" to "Chapter title (optional)",
    "保存章节名称" to "Save chapter title",
    "聊天、思考过程和未采用的候选都不会混入正文" to "Chats, reasoning, and unused candidates never enter your chapter text",
    "这里还没有可以书写的章节" to "There is no editable chapter yet",
    "从这里写下第一句话吧，你的每次修改都会悄悄保存。" to "Write the first line here. Every change is saved quietly.",
    "查看上下文使用量" to "View context usage",
    "上下文使用量" to "Context usage",
    "最近一次上下文用量" to "Latest context usage",
    "圆环记录的是模型上一次实际收到的内容，不做猜测。故事或记忆发生变化后，旧结果仍会保留，并提醒你内容已有变化。" to "The ring shows what the model actually received last time, without estimates. If your story or memory changes, the previous result remains and is marked as outdated.",
    "查看" to "View",
    "删除全部候选" to "Delete all candidates",
    "这一次，灵感没有顺利抵达" to "The idea did not arrive this time",
    "你的创作要求还在。再次尝试时，会沿用刚才确认过的故事记忆、模型和参数。" to "Your writing request is still here. A retry will use the story memory, model, and settings you just reviewed.",
    "按原内容重试" to "Retry with the same request",
    "放弃" to "Discard",
    "这一次想怎么写？" to "How should this part unfold?",
    "写下续写方向" to "Describe where the story should go",
    "下一章并生成" to "Next chapter and generate",
    "检查并生成" to "Review and generate",
    "这一次，你想让故事如何继续？" to "How would you like the story to continue?",
    "例如：延续紧张氛围，让主角发现时间线的矛盾" to "For example: Keep the tension and let the protagonist notice a contradiction in the timeline",
    "可以先记下下一次的想法；处理完当前候选后再继续生成，这段草稿会一直保留。" to "You can note your next idea now. Finish the current candidates first; this draft will stay here.",
    "下一章" to "Next chapter",
    "删除全部候选？" to "Delete all candidates?",
    "全部删除" to "Delete all",
    "返回创作要求" to "Back to writing request",
    "上一个候选" to "Previous candidate",
    "下一个候选" to "Next candidate",
    "用刚才确认的内容重新生成" to "Regenerate with the reviewed request",
    "删除当前候选" to "Delete current candidate",
    "中途停下，仍可采用" to "Stopped early, still usable",
    "已记录实际用量" to "Actual usage recorded",
    "只供你查看，不会混入正文，也不会带到下一次创作中。" to "For your reference only. It will not enter the chapter or the next generation.",
    "正在梳理思路，完成后可以展开查看。" to "Organizing the reasoning. You can expand it when ready.",
    "这一次没有开启思考，或当前模型不支持展示思考过程。" to "Reasoning was disabled, or this model cannot display it.",
    "这一次没有留下可展示的思考过程。" to "No reasoning is available to display this time.",
    "接受总结" to "Accept summary",
    "接受并追加" to "Accept and append",
    "删除当前候选？" to "Delete this candidate?",
    "我的书架" to "My Library",
    "愿每一个故事，都能在这里拥有属于自己的灵魂。" to "May every story find a soul of its own here.",
    "这里正等着第一个故事" to "Your first story is waiting",
    "先为它取个名字吧，第一章会和它一起悄然诞生。" to "Give it a name, and its first chapter will quietly begin beside it.",
    "开始一个故事" to "Start a story",
    "新建" to "New",
    "开启新故事" to "Begin a new story",
    "故事的名字" to "Story title",
    "以后也可以随时为它换上喜欢的封面" to "You can change its cover whenever you like",
    "放上书架" to "Add to library",
    "正在打开图片" to "Opening image",
    "正在展开这张图片…" to "Preparing this image…",
    "无法使用这张图片" to "This image cannot be used",
    "无法读取所选图片" to "Could not read the selected image",
    "只属于你的故事" to "A story of your own",
    "调整封面" to "Adjust cover",
    "双指缩放 · 单指拖动" to "Pinch to zoom · Drag to move",
    "使用封面" to "Use cover",
    "取消裁剪" to "Cancel cropping",
    "封面比例 2:3" to "Cover ratio 2:3",
    "复位" to "Reset",
    "缩放" to "Zoom",
    "移动图片" to "Move image",
    "灵感对谈" to "Creative Chat",
    "这段对话会一直陪着当前故事，也不会擅自改动正文。" to "This conversation stays with the current story and never changes your chapter text on its own.",
    "清空对话" to "Clear conversation",
    "从一个困住你的问题开始" to "Start with a question that has you stuck",
    "例如：“主角此时为什么会相信对手？请区分已知事实、推断和新建议。”" to "For example: “Why would the protagonist trust the rival now? Separate known facts, inferences, and new suggestions.”",
    "想聊些什么？" to "What would you like to explore?",
    "一起看看人物动机、冲突或故事的下一步" to "Explore character motives, conflict, or where the story goes next",
    "要告别这段对话吗？" to "Clear this conversation?",
    "清空后，当前对话与相关记录都会永久消失，无法找回；正文不会受到影响。" to "The conversation and its records will be permanently removed. Your chapter text will not be affected.",
    "正在寻找线索…" to "Looking for clues…",
    "正在梳理思路" to "Organizing thoughts",
    "灵感正在路上…" to "An idea is on its way…",
    "灵感回应" to "Assistant",
    "你的问题" to "You",
    "只在这里陪你梳理，不会带进下一轮对话。" to "Shown only to help you follow the reasoning; it will not be carried into the next message.",
    "这一次没有开启思考。" to "Reasoning was not enabled this time.",
    "每一次生成都会留下可以回看的痕迹；以后调整模型或故事记忆，也不会改变过去。" to "Every generation leaves a record you can revisit. Later model or memory changes will not rewrite the past.",
    "全部" to "All",
    "不限时间" to "Any time",
    "清理" to "Clean up",
    "还没有留下符合条件的创作记录。" to "No writing records match these filters yet.",
    "确认批量清理？" to "Confirm cleanup?",
    "清理可安全移除的记录" to "Remove safe records",
    "已保存发送前的完整记录" to "The complete pre-send record was saved",
    "未返回用量" to "Usage not returned",
    "暂无用量记录" to "No usage record",
    "内容后来有过变化" to "Content changed later",
    "模型返回的实际用量" to "Actual usage returned by the model",
    "模型原稿和你的修改稿都会留在“候选”页，可以分别查看或复制。" to "The model draft and your edited version remain on the Candidate tab for separate viewing or copying.",
    "尝试撤销这次正文追加" to "Try to undo this append",
    "复制当前内容" to "Copy current content",
    "准备发送" to "Ready to send",
    "创作中" to "Generating",
    "已完成" to "Completed",
    "未完成" to "Failed",
    "模型检查" to "Model check",
    "发送前，再看一眼" to "One last look before sending",
    "内容还没有发出。确认无误后，再把它交给模型。" to "Nothing has been sent yet. Review it, then hand it to the model.",
    "密钥和只用于本地辨认的名称，都不会随内容发送。" to "API keys and local-only names are never sent with your content.",
    "再改一改" to "Keep editing",
    "确认发送" to "Confirm and send",
    "设置" to "Settings",
    "密钥由 Android Keystore 保护，普通数据库和请求历史中不保存明文。" to "Keys are protected by Android Keystore and are never stored as plain text in the database or request history.",
    "待手动验证" to "Manual verification needed",
    "未连接" to "Not connected",
    "模型供应商" to "Model providers",
    "选择供应商后配置 API；保存前会调用模型列表接口真实验证。" to "Choose a provider and configure its API. The model list endpoint is checked before saving.",
    "API 已验证" to "API verified",
    "API 已保存，待验证模型" to "API saved; model verification pending",
    "API 未配置" to "API not configured",
    "新 API Key（留空表示不修改）" to "New API key (leave blank to keep the current key)",
    "显示 API Key" to "Show API key",
    "隐藏 API Key" to "Hide API key",
    "验证并安全保存" to "Verify and save securely",
    "刷新模型" to "Refresh models",
    "验证中…" to "Verifying…",
    "本作任务模型" to "Models for this story",
    "正文、总结和助手可分别选择。手动 ID 会用低成本真实请求验证。" to "Choose separate models for writing, summaries, and the assistant. Manual IDs are verified with a low-cost request.",
    "供应商" to "Provider",
    "正文模型" to "Writing model",
    "总结模型" to "Summary model",
    "助手模型" to "Assistant model",
    "手动模型 ID" to "Manual model ID",
    "验证并添加" to "Verify and add",
    "生成参数" to "Generation settings",
    "流式输出" to "Stream output",
    "边接收边持久化；中断时保留不完整内容。" to "Save content as it arrives and keep partial output if interrupted.",
    "开启思考" to "Enable reasoning",
    "推理内容与最终文本分开保存，不进入正文。" to "Reasoning is stored separately from the final text and never enters the chapter.",
    "流式显示思考" to "Stream reasoning",
    "推理强度" to "Reasoning effort",
    "弱" to "Low",
    "中" to "Medium",
    "强" to "High",
    "不支持" to "Unsupported",
    "温度" to "Temperature",
    "思考模式不使用温度参数。" to "Temperature is not used while reasoning is enabled.",
    "最大上下文 Token" to "Maximum context tokens",
    "本地默认上限" to "Local default limit",
    "本地手动上限" to "Local manual limit",
    "本作独立指令" to "Story-specific instructions",
    "保存本作指令" to "Save story instructions",
    "保存为应用默认" to "Save as app default",
    "应用默认总结指令" to "Default summary instructions",
    "重置本作总结" to "Reset story summary instructions",
    "应用默认恢复出厂" to "Restore factory default",
    "完整加密备份" to "Complete encrypted backup",
    "创建完整备份" to "Create complete backup",
    "选择保存位置" to "Choose save location",
    "选择备份恢复" to "Choose backup to restore",
    "备份密码" to "Backup password",
    "再次输入" to "Enter again",
    "显示备份密码" to "Show backup password",
    "隐藏备份密码" to "Hide backup password",
    "显示确认密码" to "Show confirmation password",
    "隐藏确认密码" to "Hide confirmation password",
    "先解密并预览" to "Decrypt and preview",
    "确认完整覆盖？" to "Replace all current data?",
    "创建回滚备份并覆盖" to "Create rollback backup and replace",
    "有恢复前回滚备份" to "A pre-restore rollback backup is available",
    "恢复覆盖前状态" to "Restore pre-replacement state",
    "本作预设" to "Story presets",
    "本作世界书" to "Story world book",
    "灵感素材库" to "Idea library",
    "素材库里的内容不会自行进入任何作品。" to "Items in the idea library never enter a story on their own.",
    "这些约定会排在世界书之前，按你安排的顺序陪模型理解故事。名称只在这里可见。" to "These instructions appear before the world book in the order you choose. Their names are visible only here.",
    "这里收纳故事的世界规则，启用后会完整陪伴每一次创作。" to "Keep the rules of your story world here. Enabled entries accompany every generation in full.",
    "这里像一只安静的抽屉。只有复制到当前作品并主动启用后，素材才会参与创作。" to "Think of this as a quiet drawer. An item joins generation only after you copy it into the current story and enable it.",
    "还没有条目" to "No entries yet",
    "这里还没有这类素材" to "No items of this kind yet",
    "已陪伴创作" to "Enabled",
    "暂不启用" to "Disabled",
    "珍藏于素材库" to "Saved in idea library",
    "可用" to "Ready",
    "待更新" to "Needs refresh",
    "已失效" to "Outdated",
    "故事已经变化，建议重新整理" to "The story has changed; refresh recommended",
    "来源已经变化，暂时不会参与创作" to "Its sources changed, so it is temporarily excluded from generation",
    "先收好，暂不启用" to "Save without enabling",
    "让它陪伴本作创作" to "Enable for this story",
    "给自己看的名字" to "Private name",
    "会交给模型的内容" to "Content sent to the model",
    "这条内容会从当前作品中移除，过去的创作记录仍会原样保留。" to "This entry will be removed from the current story. Past writing records will remain unchanged.",
    "这条素材会从素材库中删除；已经复制到各部作品里的内容不会受到影响。" to "This item will be removed from the idea library. Copies already used in stories will not be affected.",
    "复制到本作" to "Copy to this story",
    "复制到全局库" to "Copy to idea library",
    "整理故事总结" to "Create story summary",
    "选择想要凝练的内容" to "Choose what to summarize",
    "你勾选的正文与仍然有效的总结，才会陪伴下一次创作。接受新总结后，它的直接来源会自动退出，避免重复。" to "Only selected chapters and valid summaries accompany the next generation. When a new summary is accepted, its direct sources are removed to avoid repetition.",
    "恢复推荐选择" to "Restore recommended selection",
    "只会整理你在这里选中的章节和总结，其余内容不会被带入。" to "Only the chapters and summaries selected here will be summarized.",
    "这里还没有被选中的故事记忆" to "No story memory selected yet",
    "这段内容已经包含在所选总结中" to "This content is already covered by a selected summary",
    "已有总结" to "Existing summary",
    "简洁" to "Concise",
    "标准" to "Standard",
    "详细" to "Detailed",
    "总结标题" to "Summary title",
    "总结标题不能为空" to "Summary title cannot be empty",
    "总结内容不能为空" to "Summary content cannot be empty",
    "总结不存在" to "Summary not found",
    "删除总结失败" to "Could not delete the summary",
    "总结已删除" to "Summary deleted",
    "删除后，这份总结会从故事记忆中移除；依赖它的后续总结会失效，过去的创作记录仍会保留。" to "This summary will be removed from story memory. Dependent summaries will become outdated, while past writing records will remain.",
    "总结正文" to "Summary text",
    "修改这份总结后，依赖它的后续总结会提醒你重新整理。" to "Editing this summary will mark dependent summaries for refresh.",
    "暂无正文" to "No chapter text",
    "本地默认上限" to "Local default limit",
    "本地手动上限" to "Local custom limit",
    "数值会进入请求检查和不可变快照。" to "This value appears in the request review and immutable snapshot.",
    "当前模型未声明支持思考内容。" to "The current model does not declare reasoning support.",
    "当前模型只支持思考开关，强度标记为 unsupported，不会发送 reasoning_effort。" to "The current model supports only the reasoning switch. Reasoning effort is unsupported and will not be sent.",
    "只在开启思考时有效。" to "Available only when reasoning is enabled.",
    "正文、总结和助手可分别选择。手动 ID 会用低成本真实请求验证。" to "Writing, summary, and assistant models can be selected separately. Manual IDs are checked with a low-cost live request.",
    "先验证厂商连接，或在下方填写手动模型 ID。" to "Verify a provider connection first, or enter a manual model ID below.",
    "选择已配置供应商" to "Choose a configured provider",
    "手动模型所属供应商" to "Provider for the manual model",
    "本作保存后独立维护；应用默认值只影响以后新建的小说。" to "Once saved, this story keeps its own copy. App defaults affect only new stories.",
    "可将当前本作总结指令保存给以后新建小说；恢复出厂不会改动任何已有小说。" to "You can use this story’s summary instructions as the default for future stories. Restoring factory defaults never changes existing stories.",
    "单个 .ainovelbackup 包含全部持久化业务数据和加密密钥区。备份密码不上传、不代管。" to "One .ainovelbackup file contains all persistent story data and the encrypted key vault. The backup password is never uploaded or managed for you.",
    "创建备份" to "Create backup",
    "选择保存位置" to "Choose save location",
    "至少 8 个字符。忘记密码将无法恢复备份。" to "Use at least 8 characters. A forgotten password cannot be recovered.",
    "此时不会修改当前数据。校验通过后再确认覆盖。" to "Current data is not changed during preview. You can confirm replacement after verification.",
    "请输入上次恢复时使用的备份密码。" to "Enter the password used for the previous restore.",
    "使用上次恢复时的备份密码可回到覆盖前状态。" to "Use the password from the last restore to return to the pre-replacement state.",
    "回滚" to "Rollback",
    "解密预览" to "Decrypted preview",
    "创建回滚备份并覆盖" to "Create rollback backup and replace",
    "模型列表暂不可用，连接信息已加密保存。请在“本作任务模型”中填写手动模型 ID，并完成低成本真实生成验证。" to "The model list is temporarily unavailable, but connection details were saved securely. Enter a manual model ID under Models for this story and complete a low-cost live verification.",
    "通用 OpenAI 兼容接口" to "Generic OpenAI-compatible API",
    "通用 Anthropic 兼容接口" to "Generic Anthropic-compatible API",
    "OpenAI 兼容" to "OpenAI compatible",
    "Anthropic 原生" to "Native Anthropic",
    "Anthropic 兼容" to "Anthropic compatible",
    "使用官方 /models 与 /messages 接口；高级思考参数暂不主动发送。" to "Uses the official /models and /messages endpoints. Advanced reasoning parameters are not sent yet.",
    "填写服务商给出的完整 Base URL；若不能获取模型列表，可手动验证模型 ID。" to "Enter the provider’s complete Base URL. If the model list is unavailable, verify a model ID manually.",
    "填写包含 /v1 的 Base URL；支持 /models 与 /messages。" to "Enter a Base URL containing /v1. Supports /models and /messages.",
    "阿里云百炼 / 通义" to "Alibaba Cloud Model Studio / Qwen",
    "智谱 GLM" to "Zhipu GLM",
    "硅基流动" to "SiliconFlow",
    "只属于你的故事" to "A story of your own",
    "封面裁剪区域，可拖动和双指缩放" to "Cover crop area; drag or pinch to zoom",
    "缩小封面图片" to "Zoom cover image out",
    "放大封面图片" to "Zoom cover image in",
    "向左移动封面图片" to "Move cover image left",
    "向右移动封面图片" to "Move cover image right",
    "向上移动封面图片" to "Move cover image up",
    "向下移动封面图片" to "Move cover image down",
    "模型原稿：\n" to "Model draft:\n",
    "\n\n作者修改稿：\n" to "\n\nEdited version:\n",
    "\n\n思考过程（不会进入正文或总结）：\n" to "\n\nReasoning (never included in chapter text or summaries):\n",
    "新的候选已经准备好，还没有写入正文" to "New candidates are ready and have not been added to the chapter yet",
    "生成停在半途，已为你保留当前内容" to "Generation stopped early; the content received so far has been preserved",
    "新封面已经换好了" to "The new cover is ready",
    "新章节已经展开，确认发送内容后就会继续创作" to "The new chapter is open. Review and confirm the request to continue writing",
    "故事顺序已经更新，受影响的总结需要重新整理" to "Story order updated; affected summaries need to be refreshed",
    "章节已经收进回收区，依赖它的总结需要重新整理" to "Chapter moved to Trash; dependent summaries need to be refreshed",
    "章节已经回来了；相关总结仍需要重新整理" to "Chapter restored; related summaries still need to be refreshed",
    "章节已经在边界" to "The chapter is already at the edge",
    "候选已放下，正文没有改变" to "Candidate discarded; chapter text was not changed",
    "已经停下生成，收到的内容为你保留在候选中" to "Generation stopped; received content was kept as a candidate",
    "已经停下重新生成，收到的内容为你保留在候选中" to "Regeneration stopped; received content was kept as a candidate",
    "已经续写到章节末尾，需要时可以在“足迹”里撤销" to "The text was appended to the chapter. You can undo it later from History",
    "总结已经采用，原始内容已从下一次创作中退出，避免重复" to "Summary accepted; its source content was removed from the next generation to avoid repetition",
    "这次续写已从正文中撤回，创作记录仍然保留" to "This append was removed from the chapter; its writing record remains",
    "这次续写之前已经撤回了" to "This append was already undone",
    "正文后来又有修改。为了不误删后续文字，这次没有自动撤回" to "The chapter changed afterward, so the append was not removed to avoid deleting later edits",
    "这段对话和相关记录已经清空" to "The conversation and its records were cleared",
    "本作指令已保存" to "Story instructions saved",
    "当前总结指令将用于以后新建的小说" to "These summary instructions will be used for future stories",
    "本作总结指令已重置为当前应用默认值" to "This story’s summary instructions were reset to the current app default",
    "以后新建小说使用的总结指令已恢复出厂版本" to "Summary instructions for future stories were restored to the factory default",
    "最大上下文已保存，刷新模型不会覆盖" to "Maximum context saved; refreshing models will not overwrite it",
    "已经放进当前作品，并会陪伴下一次创作" to "Added to the current story and enabled for the next generation",
    "已经收进灵感素材库，不会自行参与任何作品" to "Saved in the idea library; it will not enter any story on its own",
    "完整备份已创建" to "Complete backup created",
    "完整加密备份已创建" to "Complete encrypted backup created",
    "完整恢复成功；恢复前数据已保留为加密回滚备份" to "Restore completed. The previous data was kept as an encrypted rollback backup",
    "已恢复上次覆盖前的数据" to "Restored the data from before the last replacement",
    "没有可用的恢复前备份" to "No pre-restore backup is available",
    "用户停止生成" to "Generation stopped by user",
    "请先填写本次创作要求" to "Enter a writing request first",
    "请先配置正文模型" to "Configure a writing model first",
    "请先选择正文模型" to "Select a writing model first",
    "请先停止生成或等待生成完成" to "Stop the current generation or wait for it to finish",
    "请至少选择一个总结来源" to "Select at least one summary source",
    "请输入要讨论的问题" to "Enter a question to discuss",
    "请输入大于零的整数" to "Enter an integer greater than zero",
    "至少保留一个可编辑章节" to "Keep at least one editable chapter",
    "小说不存在" to "Story not found",
    "接受记录不存在" to "Acceptance record not found",
    "该请求不能从这里发送" to "This request cannot be sent from here",
    "未知厂商" to "Unknown provider",
    "未知错误" to "Unknown error",
    "无法读取选定文件" to "Could not read the selected file",
    "无法写入选定位置" to "Could not write to the selected location",
    "已纳入上下文" to "Included in context",
    "删除全部候选？" to "Delete all candidates?",
    "删除当前候选？" to "Delete this candidate?",
    "总结候选" to "Summary candidates",
    "正文候选" to "Draft candidates",
    "故事顺序" to "Story order",
    "模型原稿和你的修改稿都会留在“候选”页，可以分别查看或复制。" to "The model draft and your edited version remain under Candidate, where each can be viewed or copied.",
    "完整请求" to "Full request",
    "发送内容" to "Messages",
    "故事记忆" to "Story memory",
    "确认完整覆盖？" to "Replace all current data?",
    "恢复推荐选择" to "Restore recommended selection",
    "显示 API Key" to "Show API key",
    "隐藏 API Key" to "Hide API key",
    "Google Cloud Vertex AI" to "Google Cloud Vertex AI",
    "Gemini / Vertex AI 原生" to "Native Gemini / Vertex AI",
    "Vertex AI 原生" to "Native Vertex AI",
    "支持 Gemini API Key，或直接粘贴 / 导入 Google Cloud Vertex AI 服务账号 JSON 凭证。" to "Supports Gemini API keys or directly pasting / importing Google Cloud Vertex AI service account JSON credentials.",
    "导入或粘贴 Google Cloud 服务账号 (Service Account) JSON 密钥，自动签发 OAuth2 令牌并填充项目地址。" to "Import or paste a Google Cloud Service Account JSON key to automatically issue OAuth2 tokens and populate the project URL.",
    "导入服务账号 JSON" to "Import Service Account JSON",
    "API Key 或服务账号 JSON" to "API Key or Service Account JSON",
    "新 API Key 或服务账号 JSON（留空表示不修改）" to "New API key or Service Account JSON (leave blank to keep the current key)",
    "服务账号 JSON 或 API Key" to "Service Account JSON or API Key",
    "新服务账号 JSON 或 API Key（留空表示不修改）" to "New Service Account JSON or API key (leave blank to keep the current key)",
    "已成功导入服务账号 JSON" to "Service account JSON imported successfully",
    "导入服务账号 JSON 失败" to "Failed to import service account JSON",
    "服务账号内容必须为 JSON 对象" to "Service account content must be a JSON object",
    "检测到 OAuth 客户端 JSON 而非服务账号（Service Account）密钥 JSON，请导入包含 private_key 与 client_email 的服务账号 JSON" to "Detected OAuth client JSON instead of Service Account key JSON. Please import a service account JSON containing private_key and client_email",
    "服务账号 JSON 缺少 project_id" to "Service account JSON missing project_id",
    "服务账号 JSON 缺少 client_email" to "Service account JSON missing client_email",
    "服务账号 JSON 缺少有效的 private_key（需包含 PEM 格式私钥）" to "Service account JSON missing valid private_key (must contain PEM format private key)",
    "OAuth2 响应缺少 access_token" to "OAuth2 response missing access_token",
    "Google OAuth2 返回缺失 access_token" to "Google OAuth2 response missing access_token",
    "灵感内容已复制到剪贴板" to "Idea copied to clipboard",
    "保存灵感失败" to "Could not save idea",
    "请输入给自己看的名字" to "Please enter a private name",
    "请输入灵感内容" to "Please enter idea content",
    "请先选择作品" to "Please select a story first",
    "已收入灵感素材库，可在“记忆 · 灵感素材”中查看" to "Saved to idea library. View in “Memory · Ideas”",
    "已加入本作世界书，将陪伴下一次创作" to "Added to story world book; will accompany the next generation",
    "已加入本作预设，将陪伴下一次创作" to "Added to story presets; will accompany the next generation",
    "已存入本作，暂未启用" to "Saved to story, currently disabled",
    "填入本章要求失败" to "Could not apply chapter requirement",
    "请输入本次创作要求" to "Please enter a writing request",
    "请输入世界书条目名称" to "Please enter a world book entry name",
    "已填入本章创作要求，并同步加入本作世界书" to "Applied as chapter writing request and added to story world book",
    "已填入本章创作要求" to "Applied as chapter writing request",
    "复制" to "Copy",
    "收入灵感库" to "Save to idea library",
    "用作本章要求" to "Use as chapter request",
    "收入灵感资料库" to "Save to Idea Library",
    "经你确认后才会保存。你可以先删减或修改内容，再选择存放到全局灵感素材库或直接加入本作世界书。" to "Saved only after your confirmation. You can edit or trim content before saving to the global idea library or adding directly to this story's world book.",
    "灵感素材库（全局）" to "Idea library (Global)",
    "本作世界书（直接用于下一章）" to "Story world book (For next chapter)",
    "灵感内容（可编辑裁剪）" to "Idea content (editable)",
    "确认收入" to "Confirm and save",
    "用作本章创作要求" to "Use as Chapter Request",
    "确认后会将以下内容填入写作页的“本次创作要求”，并可同时加入本作世界书陪伴下一章创作。" to "After confirmation, this will be filled into Writing Request on the write tab, and can optionally join the story world book for the next chapter.",
    "本次创作要求（可编辑裁剪）" to "Writing request (editable)",
    "同时加入本作世界书" to "Also add to story world book",
    "世界书条目名称" to "World book entry name",
    "确认并前往写作" to "Confirm and start writing",
    "导出 EPUB" to "Export EPUB",
    "导出本作 EPUB" to "Export story as EPUB",
    "EPUB 电子书已导出" to "EPUB ebook exported",
    "导出 EPUB 失败" to "Could not export EPUB",
    "还没有可导出的正文内容" to "No chapter text available to export yet",
    "将当前作品的正文导出为标准 EPUB 电子书，方便在各类阅读器中阅读。" to "Export the current story as a standard EPUB ebook to read on e-readers.",
)

private fun translateDynamicText(source: String): String {
    Regex("""^灵感记录 #(\d+)$""").matchEntire(source)?.let {
        return "Idea record #${it.groupValues[1]}"
    }
    Regex("""^下一章灵感 #(\d+)$""").matchEntire(source)?.let {
        return "Next chapter idea #${it.groupValues[1]}"
    }
    Regex("""^保存灵感失败：(.*)$""").matchEntire(source)?.let {
        return "Could not save idea: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^填入本章要求失败：(.*)$""").matchEntire(source)?.let {
        return "Could not apply chapter requirement: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^导出 EPUB 失败：(.*)$""").matchEntire(source)?.let {
        return "Could not export EPUB: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^删除总结失败：(.*)$""").matchEntire(source)?.let {
        return "Could not delete the summary: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^保存总结失败：(.*)$""").matchEntire(source)?.let {
        return "Could not save the summary: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^彻底删除章节失败：(.*)$""").matchEntire(source)?.let {
        return "Could not permanently delete the chapter: ${localized(it.groupValues[1], AppLanguage.ENGLISH)}"
    }
    Regex("""^已加载服务账号：(.+)（项目：(.+)）$""").matchEntire(source)?.let {
        return "Loaded service account: ${it.groupValues[1]} (project: ${it.groupValues[2]})"
    }
    Regex("""^服务账号 JSON 格式错误：(.*)$""").matchEntire(source)?.let {
        return "Invalid service account JSON format: ${it.groupValues[1]}"
    }
    Regex("""^服务账号凭证解析失败：(.*)$""").matchEntire(source)?.let {
        return "Failed to parse service account credentials: ${it.groupValues[1]}"
    }
    Regex("""^服务账号私钥解析或签名失败：(.*)$""").matchEntire(source)?.let {
        return "Failed to parse or sign service account private key: ${it.groupValues[1]}"
    }
    Regex("""^获取 Google OAuth2 访问令牌失败：(.*)$""").matchEntire(source)?.let {
        return "Failed to acquire Google OAuth2 access token: ${it.groupValues[1]}"
    }
    Regex("""^无法连接 Google OAuth2 服务(?:器)?：(.*)$""").matchEntire(source)?.let {
        return "Unable to connect to Google OAuth2 server: ${it.groupValues[1]}"
    }
    Regex("""^Google OAuth2 返回的不是合法的 JSON：(.*)$""").matchEntire(source)?.let {
        return "Google OAuth2 returned invalid JSON: ${it.groupValues[1]}"
    }
    Regex("""^Google 服务账号认证失败（HTTP (\d+)）：(.*)$""").matchEntire(source)?.let {
        return "Google service account authentication failed (HTTP ${it.groupValues[1]}): ${it.groupValues[2]}"
    }
    Regex("""^(\d+) 本作品$""").matchEntire(source)?.let { return "${it.groupValues[1]} stories" }
    Regex("""^(\d+) 个候选待选择$""").matchEntire(source)?.let { return "${it.groupValues[1]} candidates awaiting selection" }
    Regex("""^候选 (\d+) · 还没有写入正文$""").matchEntire(source)?.let { return "Candidate ${it.groupValues[1]} · Not added to the chapter yet" }
    Regex("""^候选 (\d+) · (生成完成|中途停下)$""").matchEntire(source)?.let {
        return "Candidate ${it.groupValues[1]} · ${if (it.groupValues[2] == "生成完成") "Completed" else "Stopped early"}"
    }
    Regex("""^候选 (\d+) 的正文草稿与思考过程会被移除。已经写入的正文不会受影响，创作记录和用量信息仍会保留。$""").matchEntire(source)?.let {
        return "Candidate ${it.groupValues[1]}'s draft and reasoning will be removed. Accepted chapter text is unaffected, and writing history and usage records will remain."
    }
    Regex("""^这组的 (\d+) 个候选会全部移除，连同对应的正文草稿与思考过程。已经写入的正文不会受影响，创作记录和用量信息仍会保留。$""").matchEntire(source)?.let {
        return "All ${it.groupValues[1]} candidates in this group, including their drafts and reasoning, will be removed. Accepted chapter text is unaffected, and writing history and usage records will remain."
    }
    Regex("""^第 (\d+) 章 · 自动保存$""").matchEntire(source)?.let { return "Chapter ${it.groupValues[1]} · Auto-saved" }
    Regex("""^第(\d+)章 · (.*)$""").matchEntire(source)?.let { return "Chapter ${it.groupValues[1]} · ${localized(it.groupValues[2], AppLanguage.ENGLISH)}" }
    Regex("""^最近一次创作使用了 ([\d.]+)%，输入 ([\d,]+) Token，可用上限 ([\d,]+) Token(。此后故事内容已经变化)?$""").matchEntire(source)?.let {
        val changed = if (it.groupValues[4].isNotEmpty()) ". Story content has changed since then" else ""
        return "The latest generation used ${it.groupValues[1]}% of context: ${it.groupValues[2]} input tokens out of a ${it.groupValues[3]}-token limit$changed"
    }
    Regex("""^这次筛选找到 (\d+) 条记录，其中 (\d+) 条可以安全清理；另外 (\d+) 条仍与正文或版本相关，会继续保留。$""").matchEntire(source)?.let {
        return "This filter found ${it.groupValues[1]} records. ${it.groupValues[2]} can be safely removed; ${it.groupValues[3]} are still linked to chapter text or versions and will be kept."
    }
    Regex("""^同时会移除 (\d+) 个未采用的候选，以及 (\d+) 条对应的用量记录。$""").matchEntire(source)?.let {
        return "This will also remove ${it.groupValues[1]} unused candidates and ${it.groupValues[2]} related usage records."
    }
    Regex("""^本次用量：输入 (\d+) · 输出 (\d+) · 总计 (\d+) Token$""").matchEntire(source)?.let {
        return "Usage: ${it.groupValues[1]} input · ${it.groupValues[2]} output · ${it.groupValues[3]} total tokens"
    }
    Regex("""^备份包含 (\d+) 部小说、(\d+) 个章节、(\d+) 个总结、(\d+) 个候选、(\d+) 条请求。\n\n恢复不会静默合并，将完整替换当前数据；软件会先自动保留当前状态供回滚。$""").matchEntire(source)?.let {
        return "The backup contains ${it.groupValues[1]} stories, ${it.groupValues[2]} chapters, ${it.groupValues[3]} summaries, ${it.groupValues[4]} candidates, and ${it.groupValues[5]} requests.\n\nRestore replaces current data instead of silently merging it. The app first preserves the current state for rollback."
    }
    Regex("""^连接成功，已获取 (\d+) 个模型$""").matchEntire(source)?.let { return "Connected. Loaded ${it.groupValues[1]} models" }
    Regex("""^模型 (.+) 已通过真实生成验证$""").matchEntire(source)?.let { return "Model ${it.groupValues[1]} passed live generation verification" }
    Regex("""^已刷新 (\d+) 个模型，自定义上下文上限保持不变$""").matchEntire(source)?.let { return "Refreshed ${it.groupValues[1]} models; custom context limits were preserved" }
    Regex("""^已移除 (\d+) 个候选，正文没有改变$""").matchEntire(source)?.let { return "Removed ${it.groupValues[1]} candidates; chapter text was unchanged" }
    Regex("""^这个候选已经移除，还剩 (\d+) 个$""").matchEntire(source)?.let { return "Candidate removed; ${it.groupValues[1]} remain" }
    Regex("""^已清理 (\d+) 条创作记录、(\d+) 个未采用候选和 (\d+) 条用量记录；另有 (\d+) 条仍与正文或版本相关，已经为你保留$""").matchEntire(source)?.let {
        return "Removed ${it.groupValues[1]} writing records, ${it.groupValues[2]} unused candidates, and ${it.groupValues[3]} usage records. Kept ${it.groupValues[4]} records still linked to chapter text or versions"
    }
    var text = source
    val replacements = listOf(
        " 个已连接" to " connected",
        " 本作品" to " stories",
        " 个候选待选择" to " candidates awaiting selection",
        "候选 " to "Candidate ",
        "早于 " to "Older than ",
        " 天" to " days",
        "第 " to "Chapter ",
        " 章 · 自动保存" to " · Auto-saved",
        "还没有写入正文" to "Not added to the chapter yet",
        "当前缩放 " to "Current zoom ",
        "仅本地可见：" to "Local only: ",
        "本次用量：输入 " to "Usage: input ",
        " · 输出 " to " · output ",
        " · 总计 " to " · total ",
        "输入 " to "Input ",
        "可用上限 " to "limit ",
        "内容已变化" to "content changed",
        "已记录实际用量" to "Actual usage recorded",
        "生成完成" to "Completed",
        "中途停下" to "Stopped early",
        "未命名章节" to "Untitled chapter",
        "的更多操作" to " options",
        "编辑 " to "Edit ",
        "删除 " to "Delete ",
        "编辑" to "Edit ",
        "新建" to "New ",
        "删除“" to "Delete “",
        "”？" to "”?",
        "已有总结 · " to "Existing summary · ",
        "总结 · " to "Summary · ",
        "新故事没有创建成功" to "Could not create the new story",
        "封面没有保存成功" to "Could not save the cover",
        "正文还没能保存，请稍后再返回书架" to "The chapter has not been saved yet. Try returning to the library again shortly",
        "这次修改还没能自动保存" to "This change could not be auto-saved",
        "创建下一章失败" to "Could not create the next chapter",
        "新章节没有准备成功" to "Could not prepare the new chapter",
        "重命名失败" to "Could not rename the chapter",
        "删除章节失败" to "Could not delete the chapter",
        "恢复章节失败" to "Could not restore the chapter",
        "调整章节顺序失败" to "Could not reorder chapters",
        "请求检查失败" to "Could not prepare the request review",
        "总结请求检查失败" to "Could not prepare the summary request review",
        "助手请求检查失败" to "Could not prepare the assistant request review",
        "发送请求失败" to "Could not send the request",
        "重新生成没有完成" to "Regeneration did not finish",
        "删除候选失败" to "Could not delete the candidate",
        "删除全部候选失败" to "Could not delete all candidates",
        "接受候选失败" to "Could not accept the candidate",
        "撤销接受失败" to "Could not undo the accepted candidate",
        "放弃候选失败" to "Could not discard the candidate",
        "读取总结来源失败" to "Could not load summary sources",
        "保存总结失败" to "Could not save the summary",
        "重置助手失败" to "Could not reset the assistant",
        "更新上下文失败" to "Could not update the context",
        "保存条目失败" to "Could not save the entry",
        "删除条目失败" to "Could not delete the entry",
        "复制条目失败" to "Could not copy the entry",
        "调整顺序失败" to "Could not change the order",
        "保存灵感失败" to "Could not save idea",
        "填入本章要求失败" to "Could not apply chapter requirement",
        "导出 EPUB 失败" to "Could not export EPUB",
        "还没有可导出的正文内容" to "No chapter text available to export yet",
        "导出 EPUB" to "Export EPUB",
        "导出本作 EPUB" to "Export story as EPUB",
        "EPUB 电子书已导出" to "EPUB ebook exported",
        "灵感记录 #" to "Idea record #",
        "下一章灵感 #" to "Next chapter idea #",
        "连接验证失败" to "Connection verification failed",
        "刷新模型失败" to "Could not refresh models",
        "验证手动模型失败" to "Manual model verification failed",
        "保存模型失败" to "Could not save the model",
        "保存生成参数失败" to "Could not save generation settings",
        "保存上下文上限失败" to "Could not save the context limit",
        "保存独立指令失败" to "Could not save story instructions",
        "保存应用默认指令失败" to "Could not save app defaults",
        "重置总结指令失败" to "Could not reset summary instructions",
        "恢复出厂指令失败" to "Could not restore factory instructions",
        "备份失败" to "Backup failed",
        "读取备份失败" to "Could not read the backup",
        "恢复失败" to "Restore failed",
        "回滚恢复失败" to "Rollback restore failed",
        "清理预览失败" to "Could not prepare the cleanup preview",
        "清理历史失败" to "Could not clean up history",
        "未知错误" to "Unknown error",
        "个模型" to " models",
        "连接成功，已获取 " to "Connected. Loaded ",
        "已刷新 " to "Refreshed ",
        " 个模型，自定义上下文上限保持不变" to " models; custom context limits were preserved",
        "已移除 " to "Removed ",
        " 个候选，正文没有改变" to " candidates; chapter text was unchanged",
        "这个候选已经移除，还剩 " to "Candidate removed; ",
        " 个" to " remaining",
        "这个候选已经移除，这一组没有其他候选了" to "Candidate removed; no other candidates remain in this group",
    )
    replacements.forEach { (zh, en) -> text = text.replace(zh, en) }
    return exactEnglish[text] ?: text
}
