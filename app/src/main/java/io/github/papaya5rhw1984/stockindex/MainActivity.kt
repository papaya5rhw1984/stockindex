package io.github.papaya5rhw1984.stockindex

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.papaya5rhw1984.stockindex.ui.AppTheme
import io.github.papaya5rhw1984.stockindex.ui.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StockApp()
                }
            }
        }
    }
}

/* ───────────────────────── 도메인 모델 ───────────────────────── */

private data class IndexDef(
    val symbol: String,    // 네이버 지수 코드 (국내: KOSPI/KOSDAQ · 해외: 로이터코드 .IXIC 등)
    val koName: String,
    val region: String,
    val flag: String,
    val domestic: Boolean, // true = m.stock.naver.com 국내, false = api.stock.naver.com 해외
    val fallback: Double   // 오프라인 첫 실행용 placeholder(부정확)
)

// 세계 주요 증시 지수 — 네이버 증권 코드.
// 국내(코스피·코스닥)는 m.stock.naver.com/api/index/{code}/basic,
// 해외는 api.stock.naver.com/index/{reutersCode}/basic.
private val CATALOG = listOf(
    IndexDef("KOSPI",     "코스피", "한국", "🇰🇷", true, 2700.0),
    IndexDef("KOSDAQ",    "코스닥", "한국", "🇰🇷", true, 850.0),
    IndexDef(".IXIC",     "나스닥 종합", "미국", "🇺🇸", false, 17500.0),
    IndexDef(".DJI",      "다우존스", "미국", "🇺🇸", false, 39000.0),
    IndexDef(".INX",      "S&P 500", "미국", "🇺🇸", false, 5400.0),
    IndexDef(".N225",     "닛케이 225", "일본", "🇯🇵", false, 38500.0),
    IndexDef("SHCOMP",    "상하이종합", "중국", "🇨🇳", false, 3000.0),
    IndexDef("HSI",       "항셍", "홍콩", "🇭🇰", false, 18000.0),
    IndexDef("TWII",      "가권", "대만", "🇹🇼", false, 22000.0),
    IndexDef(".FTSE",     "FTSE 100", "영국", "🇬🇧", false, 8200.0),
    IndexDef(".GDAXI",    "DAX", "독일", "🇩🇪", false, 18500.0),
    IndexDef(".FCHI",     "CAC 40", "프랑스", "🇫🇷", false, 7600.0),
    IndexDef(".STOXX50E", "유로스톡스 50", "유럽", "🇪🇺", false, 5000.0),
    IndexDef(".BSESN",    "센섹스", "인도", "🇮🇳", false, 80000.0),
    IndexDef(".NSEI",     "니프티 50", "인도", "🇮🇳", false, 24000.0),
    IndexDef(".AXJO",     "ASX 200", "호주", "🇦🇺", false, 7800.0),
    IndexDef(".GSPTSE",   "S&P/TSX", "캐나다", "🇨🇦", false, 22000.0),
    IndexDef(".BVSP",     "보베스파", "브라질", "🇧🇷", false, 125000.0),
    IndexDef("STI",       "STI", "싱가포르", "🇸🇬", false, 3400.0),
    IndexDef(".JKSE",     "JCI", "인도네시아", "🇮🇩", false, 7300.0),
    IndexDef("VNINDEX",   "VN 지수", "베트남", "🇻🇳", false, 1280.0),
    IndexDef(".MXX",      "IPC", "멕시코", "🇲🇽", false, 55000.0),
)

private val CATALOG_BY_SYMBOL = CATALOG.associateBy { it.symbol }

// 심볼 → 정의. 카탈로그에 없으면(직접추가) 해외지수로 간주.
private fun defFor(symbol: String): IndexDef =
    CATALOG_BY_SYMBOL[symbol] ?: IndexDef(symbol, symbol.removePrefix(".").uppercase(), "직접추가", "🏳️", false, 0.0)

// 기본 표시 목록(처음 설치 시) — 한·미·일·중 (네이버에서 확인된 코드)
private val DEFAULT_SYMBOLS = listOf("KOSPI", "KOSDAQ", ".IXIC", ".DJI", ".N225", "SHCOMP")

/** 표시할 지수 심볼 목록(순서 유지) 영구 저장 */
private object SelStore {
    private const val KEY = "selected"
    fun load(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return DEFAULT_SYMBOLS
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return if (list.isEmpty()) DEFAULT_SYMBOLS else list
    }
    fun save(ctx: Context, syms: List<String>) {
        ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE)
            .edit().putString(KEY, syms.joinToString(",")).apply()
    }
}

// 한 지수의 시세 한 줄 (네이버: 종가 + 전일대비 절대/등락률 — 정확한 전일 대비)
private data class Quote(
    val symbol: String,
    val close: Double,
    val changeAbs: Double,   // 전일 대비 (부호 포함)
    val changePct: Double,   // 등락률 % (부호 포함)
    val date: String
)

private fun fmtNum(v: Double): String = String.format(Locale.US, "%,.2f", v)

private fun fmtSigned(v: Double): String {
    val s = if (v >= 0) "+" else "-"
    return s + String.format(Locale.US, "%,.2f", abs(v))
}

private fun fmtPct(v: Double): String =
    (if (v >= 0) "+" else "") + String.format(Locale.US, "%.2f", v) + "%"

private fun fmtSavedTime(unix: Long): String =
    if (unix <= 0) "—" else SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(unix * 1000))

/* ───────────────────────── 네트워크 (네이버 증권) ───────────────────────── */

/**
 * 네이버 증권 지수 API (무료·무키).
 * 국내: https://m.stock.naver.com/api/index/KOSPI/basic
 * 해외: https://api.stock.naver.com/index/.IXIC/basic
 * 응답: closePrice("8,411.21") · fluctuationsRatio("-5.81") · compareToPreviousClosePrice("-519.09")
 * (브라우저 UA + Referer 필요)
 */
private fun httpGet(urlStr: String): String? {
    return try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; SM-G991N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Referer", "https://m.stock.naver.com/")
        }
        val code = conn.responseCode
        val text = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        conn.disconnect()
        text
    } catch (e: Exception) { null }
}

private fun fetchOne(def: IndexDef): Quote? {
    val url = if (def.domestic)
        "https://m.stock.naver.com/api/index/${def.symbol}/basic"
    else
        "https://api.stock.naver.com/index/${def.symbol}/basic"
    val body = httpGet(url) ?: return null
    return try {
        val o = JSONObject(body)
        val close = o.optString("closePrice", "").replace(",", "").trim().toDoubleOrNull() ?: return null
        if (close <= 0.0) return null
        val pct = o.optString("fluctuationsRatio", "0").replace(",", "").trim().toDoubleOrNull() ?: 0.0
        val absChg = o.optString("compareToPreviousClosePrice", "0").replace(",", "").trim().toDoubleOrNull() ?: 0.0
        val date = o.optString("localTradedAt", "").take(10)
        Quote(def.symbol, close, absChg, pct, date)
    } catch (e: Exception) { null }
}

private fun fetchQuotes(defs: List<IndexDef>): Map<String, Quote>? {
    if (defs.isEmpty()) return null
    val out = HashMap<String, Quote>()
    for (d in defs) fetchOne(d)?.let { out[d.symbol] = it }
    return if (out.isEmpty()) null else out
}

/* ───────────────────────── 화면 ───────────────────────── */

@Composable
private fun StockApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("stockindex", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // 60초 슬라이딩 윈도우 내 최대 2회 호출
    val refreshTimes = remember { mutableListOf<Long>() }

    var selected by remember { mutableStateOf(SelStore.load(ctx)) }
    var manage by remember { mutableStateOf(false) }

    var quotes by remember {
        mutableStateOf(
            selected.associateWith { s ->
                val fb = CATALOG_BY_SYMBOL[s]?.fallback ?: 0.0
                Quote(s, fb, 0.0, 0.0, "—")
            }
        )
    }
    var live by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lastUnix by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("불러오는 중…") }

    fun toggleSel(sym: String) {
        selected = if (selected.contains(sym)) selected - sym else selected + sym
        SelStore.save(ctx, selected)
    }

    fun applyCache(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val arr = o.getJSONObject("q")
            val m = HashMap<String, Quote>()
            val keys = arr.keys()
            while (keys.hasNext()) {
                val sym = keys.next()
                val q = arr.getJSONObject(sym)
                m[sym] = Quote(
                    sym,
                    q.optDouble("c", 0.0),
                    q.optDouble("a", 0.0),
                    q.optDouble("p", 0.0),
                    q.optString("d", "—")
                )
            }
            if (m.isEmpty()) return false
            quotes = m
            lastUnix = o.optLong("saved", 0L)
            true
        } catch (e: Exception) { false }
    }

    fun refresh(silent: Boolean = false) {
        if (loading) return
        val now = System.currentTimeMillis()
        refreshTimes.removeAll { now - it > 60_000 }
        if (refreshTimes.size >= 2) {
            if (!silent) {
                val waitSec = ((60_000 - (now - refreshTimes.first())) / 1000) + 1
                Toast.makeText(ctx, "잠시 후 다시 시도해주세요 (${waitSec}초)", Toast.LENGTH_SHORT).show()
            }
            return
        }
        refreshTimes.add(now)
        loading = true
        scope.launch {
            val defs = selected.map { defFor(it) }
            val result = withContext(Dispatchers.IO) { fetchQuotes(defs) }
            if (result != null) {
                quotes = result
                live = true
                lastUnix = System.currentTimeMillis() / 1000
                note = "실시간 · ${fmtSavedTime(lastUnix)} 기준"
                val o = JSONObject()
                val qo = JSONObject()
                result.forEach { (sym, q) ->
                    qo.put(sym, JSONObject()
                        .put("c", q.close).put("a", q.changeAbs)
                        .put("p", q.changePct).put("d", q.date))
                }
                o.put("q", qo).put("saved", lastUnix)
                prefs.edit().putString("cache", o.toString()).apply()
            } else {
                live = false
                note = if (lastUnix > 0) "${fmtSavedTime(lastUnix)} 기준"
                       else "새로고침을 눌러 불러오세요"
                if (!silent) Toast.makeText(ctx, "연결이 원활하지 않아요 · 다시 시도", Toast.LENGTH_SHORT).show()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        val cached = prefs.getString("cache", null)
        if (cached != null && applyCache(cached)) {
            note = if (lastUnix > 0) "${fmtSavedTime(lastUnix)} 기준" else "불러오는 중…"
        }
        refresh(silent = true)
        while (true) {
            delay(180_000)
            refresh(silent = true)
        }
    }

    if (manage) {
        ManageScreen(
            selected = selected,
            onToggle = { toggleSel(it) },
            onClose = { manage = false; refresh(silent = true) }
        )
        return
    }

    val defs = selected.map { defFor(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("증시 나우", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            StatusChip(live = live, loading = loading)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.SurfaceHi)
                    .clickable { manage = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("＋ 관리", color = Brand.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.SurfaceHi)
                    .clickable { refresh() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(if (loading) "..." else "새로고침",
                    color = Brand.AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (defs.isEmpty()) {
                item {
                    Text(
                        "표시할 지수가 없어요 · 우측 상단 ‘＋ 관리’에서 추가하세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(defs, key = { it.symbol }) { def ->
                val q = quotes[def.symbol]
                IndexCard(def, q)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "※ 전일 종가 대비 등락 · 표시 전용(매매·투자 조언 아님)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    "데이터: 네이버 증권 · 개인정보 수집 없음",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusChip(live: Boolean, loading: Boolean) {
    val (dot, label) = when {
        loading -> Brand.Saved to "갱신중"
        live -> Brand.Live to "실시간"
        else -> Brand.Saved to "지연"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.SurfaceHi)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, color = dot, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IndexCard(def: IndexDef, q: Quote?) {
    val pct = q?.changePct ?: 0.0
    val absChg = q?.changeAbs ?: 0.0
    val color = when {
        pct > 0.0 -> Brand.Up
        pct < 0.0 -> Brand.Down
        else -> Brand.Flat
    }
    val arrow = when {
        pct > 0.0 -> "▲"
        pct < 0.0 -> "▼"
        else -> "—"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Brand.Divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.flag, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(def.koName, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(2.dp))
            Text(def.region, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (q != null) fmtNum(q.close) else "—",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(arrow, color = color, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${fmtSigned(absChg)} (${fmtPct(pct)})",
                    color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/* ───────────────────────── 관리(검색·추가·삭제) 화면 ───────────────────────── */

@Composable
private fun ManageScreen(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var query by remember { mutableStateOf("") }
    var customSym by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = if (q.isBlank()) CATALOG
        else CATALOG.filter {
            it.koName.contains(q) || it.region.contains(q) ||
                it.symbol.contains(q, ignoreCase = true)
        }
    val customSelected = selected.filter { !CATALOG_BY_SYMBOL.containsKey(it) }.map { defFor(it) }
        .filter { q.isBlank() || it.symbol.contains(q, ignoreCase = true) || it.koName.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지수 관리", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Brand.Accent)
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("완료", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("나라·지수명으로 검색해 추가/삭제하세요 (${selected.size}개 표시 중)",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("예: 독일, 인도, FTSE") }
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(customSelected, key = { "c_" + it.symbol }) { def ->
                ManageRow(def, true) { onToggle(def.symbol) }
            }
            items(filtered, key = { it.symbol }) { def ->
                ManageRow(def, selected.contains(def.symbol)) { onToggle(def.symbol) }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text("목록에 없나요? 네이버 지수 코드로 직접 추가",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSym,
                        onValueChange = { customSym = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("예: .HSI, .RUT") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brand.Accent)
                            .clickable {
                                val s = customSym.trim()
                                if (s.isNotEmpty()) {
                                    if (!selected.contains(s)) onToggle(s)
                                    customSym = ""
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Text("추가", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(4.dp))
                Text("※ 네이버 증권의 지수 코드(해외=로이터코드, 예: .HSI)예요. 값이 안 뜨면 다시 눌러 삭제하세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ManageRow(def: IndexDef, on: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Brand.Divider, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(def.flag, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(def.koName, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(def.region, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (on) Brand.Accent else Brand.SurfaceHi)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                if (on) "표시중 ✓" else "＋ 추가",
                color = if (on) Color.White else Brand.AccentSoft,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
